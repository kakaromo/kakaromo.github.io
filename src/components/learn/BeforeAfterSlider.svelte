<script lang="ts">
  // Before/After 코드 비교.
  // mode='split' → 좌우 병치 (세로 스크롤 싱크)
  // mode='slider' → 오버레이 + 드래그 슬라이더 (높이가 같을 때만 의미)
  import { onMount } from 'svelte';

  interface Side {
    title: string;
    code: string;
    language?: string;
    /** 하이라이트할 라인 구간 [[from, to], ...] (1-indexed) */
    highlight?: Array<[number, number]>;
  }

  interface Props {
    before: Side;
    after: Side;
    mode?: 'split' | 'slider';
    height?: number;
    caption?: string;
  }

  let { before, after, mode = 'split', height = 420, caption }: Props = $props();

  const beforeLines = $derived(before.code.split('\n'));
  const afterLines = $derived(after.code.split('\n'));

  function isHighlighted(ln: number, ranges?: Array<[number, number]>) {
    if (!ranges) return false;
    return ranges.some(([from, to]) => ln >= from && ln <= to);
  }

  // 슬라이더 모드: before가 오른쪽으로 쓸려나가는 효과
  let sliderPos = $state(50); // %
  let dragging = $state(false);
  let wrapperEl: HTMLDivElement | null = $state(null);

  function onPointer(e: PointerEvent) {
    if (!wrapperEl || !dragging) return;
    const rect = wrapperEl.getBoundingClientRect();
    const x = e.clientX - rect.left;
    sliderPos = Math.max(0, Math.min(100, (x / rect.width) * 100));
  }

  // 좌우 스크롤 싱크 (split 모드)
  let leftPane: HTMLDivElement | null = $state(null);
  let rightPane: HTMLDivElement | null = $state(null);
  let syncLock = false;
  function syncScroll(source: 'left' | 'right') {
    if (syncLock) return;
    syncLock = true;
    const src = source === 'left' ? leftPane : rightPane;
    const dst = source === 'left' ? rightPane : leftPane;
    if (src && dst) {
      dst.scrollTop = src.scrollTop;
    }
    setTimeout(() => {
      syncLock = false;
    }, 0);
  }
</script>

<figure class="learn-ba" style:--ba-height="{height}px">
  <div class="learn-ba__controls">
    <span class="learn-ba__title learn-ba__title--before">{before.title}</span>
    <span class="learn-ba__arrow" aria-hidden="true">→</span>
    <span class="learn-ba__title learn-ba__title--after">{after.title}</span>
  </div>

  {#if mode === 'split'}
    <div class="learn-ba__split">
      <div
        class="learn-ba__pane learn-ba__pane--before"
        bind:this={leftPane}
        onscroll={() => syncScroll('left')}
      >
        <pre><code>{#each beforeLines as line, i (i)}{@const ln = i + 1}<span
                class="learn-ba__line"
                class:learn-ba__line--hl={isHighlighted(ln, before.highlight)}
              ><span class="learn-ba__gutter">{ln}</span><span
                class="learn-ba__linecode">{line || ' '}</span></span>{/each}</code></pre>
      </div>
      <div
        class="learn-ba__pane learn-ba__pane--after"
        bind:this={rightPane}
        onscroll={() => syncScroll('right')}
      >
        <pre><code>{#each afterLines as line, i (i)}{@const ln = i + 1}<span
                class="learn-ba__line"
                class:learn-ba__line--hl={isHighlighted(ln, after.highlight)}
              ><span class="learn-ba__gutter">{ln}</span><span
                class="learn-ba__linecode">{line || ' '}</span></span>{/each}</code></pre>
      </div>
    </div>
  {:else}
    <div
      class="learn-ba__slider"
      bind:this={wrapperEl}
      onpointermove={onPointer}
      onpointerup={() => (dragging = false)}
      onpointerleave={() => (dragging = false)}
    >
      <!-- after가 바닥. before가 위에서 sliderPos% 까지 보임 -->
      <div class="learn-ba__slider-layer learn-ba__pane--after">
        <pre><code>{#each afterLines as line, i (i)}{@const ln = i + 1}<span
                class="learn-ba__line"
                class:learn-ba__line--hl={isHighlighted(ln, after.highlight)}
              ><span class="learn-ba__gutter">{ln}</span><span
                class="learn-ba__linecode">{line || ' '}</span></span>{/each}</code></pre>
      </div>
      <div
        class="learn-ba__slider-layer learn-ba__pane--before"
        style:clip-path="inset(0 {100 - sliderPos}% 0 0)"
      >
        <pre><code>{#each beforeLines as line, i (i)}{@const ln = i + 1}<span
                class="learn-ba__line"
                class:learn-ba__line--hl={isHighlighted(ln, before.highlight)}
              ><span class="learn-ba__gutter">{ln}</span><span
                class="learn-ba__linecode">{line || ' '}</span></span>{/each}</code></pre>
      </div>
      <div
        class="learn-ba__handle"
        style:left="{sliderPos}%"
        onpointerdown={() => (dragging = true)}
        role="slider"
        aria-label="Before/After 슬라이더"
        aria-valuenow={sliderPos.toFixed(0)}
        aria-valuemin="0"
        aria-valuemax="100"
        tabindex="0"
        onkeydown={(e) => {
          if (e.key === 'ArrowLeft') sliderPos = Math.max(0, sliderPos - 5);
          if (e.key === 'ArrowRight') sliderPos = Math.min(100, sliderPos + 5);
        }}
      >
        <span class="learn-ba__handle-bar"></span>
        <span class="learn-ba__handle-knob" aria-hidden="true">⇔</span>
      </div>
    </div>
  {/if}

  {#if caption}
    <figcaption class="learn-ba__caption">{caption}</figcaption>
  {/if}
</figure>

<style>
  .learn-ba {
    margin: 1.5rem 0;
    border: 1px solid var(--learn-border);
    border-radius: var(--learn-radius-lg);
    background: var(--sl-color-bg-nav);
    overflow: hidden;
  }

  .learn-ba__controls {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    padding: 0.5rem 0.85rem;
    border-bottom: 1px solid var(--learn-border);
    font-size: 0.8rem;
  }

  .learn-ba__title {
    font-weight: 700;
  }

  .learn-ba__title--before {
    color: var(--sl-color-gray-2);
  }

  .learn-ba__title--after {
    color: var(--learn-layer-state);
  }

  .learn-ba__arrow {
    color: var(--sl-color-gray-3);
  }

  .learn-ba__split {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 1px;
    background: var(--learn-border);
  }

  .learn-ba__pane {
    height: var(--ba-height);
    overflow: auto;
    background: var(--sl-color-bg);
  }

  .learn-ba__pane pre {
    margin: 0;
    padding: 0.5rem 0;
    font-size: 0.72rem;
    line-height: 1.55;
    background: transparent;
  }

  .learn-ba__line {
    display: flex;
    padding: 0 0.5rem;
  }

  .learn-ba__pane--before .learn-ba__line--hl {
    background: color-mix(in srgb, var(--sl-color-red, #e11d48) 12%, transparent);
    box-shadow: inset 3px 0 0 var(--sl-color-red, #e11d48);
  }

  .learn-ba__pane--after .learn-ba__line--hl {
    background: color-mix(in srgb, var(--learn-layer-state) 14%, transparent);
    box-shadow: inset 3px 0 0 var(--learn-layer-state);
  }

  .learn-ba__gutter {
    flex: 0 0 3ch;
    text-align: right;
    padding-right: 1ch;
    color: var(--sl-color-gray-4);
    user-select: none;
  }

  .learn-ba__linecode {
    white-space: pre;
    flex: 1;
  }

  /* Slider 모드 */
  .learn-ba__slider {
    position: relative;
    height: var(--ba-height);
    background: var(--sl-color-bg);
  }

  .learn-ba__slider-layer {
    position: absolute;
    inset: 0;
    overflow: hidden;
    background: var(--sl-color-bg);
  }

  .learn-ba__handle {
    position: absolute;
    top: 0;
    bottom: 0;
    width: 2px;
    background: var(--learn-focus);
    transform: translateX(-50%);
    cursor: ew-resize;
    z-index: 2;
  }

  .learn-ba__handle-bar {
    position: absolute;
    inset: 0 -1px;
    background: var(--learn-focus);
  }

  .learn-ba__handle-knob {
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    width: 28px;
    height: 28px;
    border-radius: 50%;
    background: var(--learn-focus);
    color: var(--sl-color-bg);
    display: grid;
    place-items: center;
    font-size: 0.8rem;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  }

  .learn-ba__handle:focus-visible {
    outline: 2px solid var(--learn-focus);
    outline-offset: 4px;
  }

  .learn-ba__caption {
    padding: 0.5rem 0.85rem;
    border-top: 1px solid var(--learn-border);
    font-size: 0.8125rem;
    color: var(--sl-color-gray-2);
    text-align: center;
  }
</style>
