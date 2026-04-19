<script lang="ts">
  // 데이터 한 건이 단계를 거치며 어떻게 변형되는지 시각화.
  // 각 stage 카드: 단계 번호, 이름, payload 샘플(코드 블록), 설명.
  // 가로로 N 단계, 사이에 화살표 + 변환 레이블.

  type Layer = 'hw' | 'be' | 'state' | 'tx' | 'fe';

  interface Stage {
    id: string;
    label: string;
    layer?: Layer;
    /** 이 단계에서의 payload 샘플. 여러 줄 가능. */
    sample: string;
    /** 짧은 설명. 카드 하단. */
    note?: string;
    /** 샘플 언어 힌트 */
    language?: string;
  }

  interface Transform {
    /** 두 스테이지 사이의 화살표 라벨 — 예: "parse", "put", "serialize" */
    label: string;
  }

  interface Props {
    stages: Stage[];
    /** stages.length - 1 길이. 생략 시 빈 화살표만. */
    transforms?: Transform[];
    caption?: string;
    altText?: string;
  }

  let { stages, transforms = [], caption, altText }: Props = $props();

  let activeId = $state<string | null>(null);
</script>

<figure class="learn-flow">
  <div class="learn-flow__track" role="list">
    {#each stages as stage, i (stage.id)}
      <div
        class="learn-flow__stage"
        data-layer={stage.layer ?? 'be'}
        class:learn-flow__stage--active={activeId === stage.id}
        role="listitem"
        tabindex="0"
        onmouseenter={() => (activeId = stage.id)}
        onmouseleave={() => (activeId = null)}
        onfocus={() => (activeId = stage.id)}
        onblur={() => (activeId = null)}
      >
        <header class="learn-flow__stage-head">
          <span class="learn-flow__stage-num">{String(i + 1).padStart(2, '0')}</span>
          <span class="learn-flow__stage-label">{stage.label}</span>
        </header>
        <pre
          class="learn-flow__sample"
          data-language={stage.language ?? 'text'}>{stage.sample}</pre>
        {#if stage.note}
          <p class="learn-flow__stage-note">{stage.note}</p>
        {/if}
      </div>

      {#if i < stages.length - 1}
        <div
          class="learn-flow__arrow"
          class:learn-flow__arrow--active={activeId === stage.id ||
            activeId === stages[i + 1]?.id}
          aria-hidden="true"
        >
          <span class="learn-flow__arrow-line"></span>
          {#if transforms[i]}
            <span class="learn-flow__arrow-label">{transforms[i].label}</span>
          {/if}
          <span class="learn-flow__arrow-head">▶</span>
        </div>
      {/if}
    {/each}
  </div>

  {#if caption}
    <figcaption class="learn-flow__caption">{caption}</figcaption>
  {/if}

  {#if altText}
    <details class="learn-flow__alt">
      <summary>흐름 텍스트 설명 보기</summary>
      <p>{altText}</p>
    </details>
  {/if}
</figure>

<style>
  .learn-flow {
    margin: 1.5rem 0;
  }

  .learn-flow__track {
    display: flex;
    align-items: stretch;
    gap: 0;
    overflow-x: auto;
    padding: 0.5rem 0;
    scroll-snap-type: x proximity;
  }

  .learn-flow__stage {
    flex: 0 0 260px;
    display: flex;
    flex-direction: column;
    border: 1px solid currentColor;
    border-radius: var(--learn-radius-lg);
    background: var(--sl-color-bg);
    color: var(--learn-layer-be);
    overflow: hidden;
    scroll-snap-align: start;
    transition: transform 0.15s ease, box-shadow 0.15s ease;
    outline: none;
  }

  .learn-flow__stage[data-layer='hw']    { color: var(--learn-layer-hw); }
  .learn-flow__stage[data-layer='be']    { color: var(--learn-layer-be); }
  .learn-flow__stage[data-layer='state'] { color: var(--learn-layer-state); }
  .learn-flow__stage[data-layer='tx']    { color: var(--learn-layer-tx); }
  .learn-flow__stage[data-layer='fe']    { color: var(--learn-layer-fe); }

  .learn-flow__stage:hover,
  .learn-flow__stage--active,
  .learn-flow__stage:focus-visible {
    transform: translateY(-2px);
    box-shadow: 0 8px 24px -12px currentColor;
  }

  .learn-flow__stage:focus-visible {
    outline: 2px solid var(--learn-focus);
    outline-offset: 2px;
  }

  .learn-flow__stage-head {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.5rem 0.75rem;
    background: color-mix(in srgb, currentColor 10%, var(--sl-color-bg-nav));
    border-bottom: 1px solid currentColor;
  }

  .learn-flow__stage-num {
    font-family: var(--sl-font-mono);
    font-size: 0.7rem;
    font-weight: 700;
    opacity: 0.8;
  }

  .learn-flow__stage-label {
    font-size: 0.85rem;
    font-weight: 700;
    color: var(--sl-color-white);
  }

  :root[data-theme='light'] .learn-flow__stage-label {
    color: var(--sl-color-black);
  }

  .learn-flow__sample {
    margin: 0;
    padding: 0.75rem;
    background: transparent;
    font-size: 0.7rem;
    line-height: 1.5;
    color: var(--sl-color-gray-1);
    white-space: pre-wrap;
    word-break: break-all;
    overflow-wrap: anywhere;
    flex: 1;
    min-height: 4rem;
  }

  .learn-flow__stage-note {
    margin: 0;
    padding: 0.5rem 0.75rem 0.75rem;
    font-size: 0.75rem;
    color: var(--sl-color-gray-2);
    border-top: 1px dashed var(--learn-border);
  }

  .learn-flow__arrow {
    flex: 0 0 auto;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 0 0.5rem;
    color: var(--sl-color-gray-3);
    transition: color 0.15s ease;
    min-width: 72px;
  }

  .learn-flow__arrow--active {
    color: var(--learn-focus);
  }

  .learn-flow__arrow-line {
    display: block;
    width: 100%;
    height: 2px;
    background: currentColor;
    border-radius: 1px;
  }

  .learn-flow__arrow-label {
    font-size: 0.7rem;
    font-family: var(--sl-font-mono);
    color: inherit;
    margin: 0.2rem 0;
  }

  .learn-flow__arrow-head {
    font-size: 0.85rem;
    line-height: 1;
  }

  .learn-flow__caption {
    margin-top: 0.75rem;
    font-size: 0.8125rem;
    color: var(--sl-color-gray-2);
    text-align: center;
  }

  .learn-flow__alt {
    margin-top: 0.75rem;
    font-size: 0.8125rem;
  }
  .learn-flow__alt summary {
    cursor: pointer;
    color: var(--sl-color-gray-2);
  }

  @media (prefers-reduced-motion: reduce) {
    .learn-flow__stage {
      transition: none;
    }
    .learn-flow__stage:hover,
    .learn-flow__stage--active {
      transform: none;
    }
  }
</style>
