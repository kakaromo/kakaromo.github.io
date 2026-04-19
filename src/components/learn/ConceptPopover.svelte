<script lang="ts">
  // L3 용어 인라인 팝오버.
  // 호버/포커스 = 요약 표시 (자식 slot 내용)
  // Enter 또는 ↗ 클릭 = L3 상세 페이지로 이동 (/learn/l3-concepts/{category}/{id})
  import { tick } from 'svelte';

  interface Props {
    /** 본문에 표시될 용어 텍스트. 예: "@Scheduled" */
    term: string;
    /** 상세 페이지 slug. 예: "spring/at-scheduled" */
    concept: string;
    /** 상세 페이지 기본 경로. 기본 "/learn/l3-concepts/". */
    basePath?: string;
    children?: () => unknown;
  }

  let { term, concept, basePath = '/learn/l3-concepts/', children }: Props = $props();

  const href = `${basePath}${concept}/`;
  const popoverId = `concept-pop-${concept.replace(/[^a-z0-9]/gi, '-')}-${Math.random()
    .toString(36)
    .slice(2, 7)}`;

  let triggerEl: HTMLButtonElement | null = $state(null);
  let popoverEl: HTMLDivElement | null = $state(null);
  let open = $state(false);
  let coords = $state({ top: 0, left: 0, placement: 'bottom' as 'bottom' | 'top' });

  async function show() {
    open = true;
    await tick();
    position();
  }

  function hide() {
    open = false;
  }

  function position() {
    if (!triggerEl || !popoverEl) return;
    const t = triggerEl.getBoundingClientRect();
    const p = popoverEl.getBoundingClientRect();
    const margin = 8;
    const vpW = window.innerWidth;
    const vpH = window.innerHeight;

    let placement: 'bottom' | 'top' = 'bottom';
    let top = t.bottom + margin;
    if (top + p.height > vpH - 12 && t.top - margin - p.height > 12) {
      placement = 'top';
      top = t.top - margin - p.height;
    }
    let left = t.left + t.width / 2 - p.width / 2;
    left = Math.max(8, Math.min(vpW - p.width - 8, left));
    coords = { top, left, placement };
  }

  function onKey(e: KeyboardEvent) {
    if (e.key === 'Escape') {
      hide();
      triggerEl?.focus();
    } else if (e.key === 'Enter') {
      // Enter: 상세 페이지로 이동
      window.location.href = href;
    }
  }
</script>

<svelte:window onscroll={position} onresize={position} />

<button
  class="concept-trigger"
  type="button"
  bind:this={triggerEl}
  aria-describedby={open ? popoverId : undefined}
  aria-expanded={open}
  onmouseenter={show}
  onmouseleave={hide}
  onfocus={show}
  onblur={hide}
  onkeydown={onKey}
  onclick={() => (window.location.href = href)}
>
  <code>{term}</code>
  <span class="concept-trigger__dot" aria-hidden="true"></span>
</button>

{#if open}
  <div
    class="concept-pop"
    id={popoverId}
    role="tooltip"
    data-placement={coords.placement}
    bind:this={popoverEl}
    style:top="{coords.top}px"
    style:left="{coords.left}px"
    onmouseenter={show}
    onmouseleave={hide}
  >
    <div class="concept-pop__body">
      {#if children}
        {@render children()}
      {:else}
        <p>{term}</p>
      {/if}
    </div>
    <a class="concept-pop__more" href={href}>자세히 보기 →</a>
  </div>
{/if}

<style>
  .concept-trigger {
    display: inline-flex;
    align-items: center;
    gap: 0.25em;
    background: none;
    border: 0;
    padding: 0;
    margin: 0;
    color: inherit;
    font: inherit;
    cursor: help;
    position: relative;
  }

  .concept-trigger code {
    background: var(--learn-layer-state-bg);
    color: var(--learn-layer-state);
    padding: 0.1em 0.35em;
    border-radius: 3px;
    border-bottom: 1px dashed currentColor;
    font-size: 0.9em;
    transition: background 0.15s ease;
  }

  .concept-trigger:hover code,
  .concept-trigger:focus-visible code {
    background: var(--sl-color-accent-low);
    color: var(--learn-focus);
  }

  .concept-trigger:focus-visible {
    outline: 2px solid var(--learn-focus);
    outline-offset: 2px;
    border-radius: 4px;
  }

  .concept-trigger__dot {
    width: 4px;
    height: 4px;
    border-radius: 50%;
    background: var(--learn-layer-state);
    opacity: 0.7;
  }

  .concept-pop {
    position: fixed;
    z-index: 999;
    width: min(320px, calc(100vw - 16px));
    background: var(--sl-color-bg-nav);
    border: 1px solid var(--learn-border);
    border-radius: var(--learn-radius-lg);
    box-shadow: 0 20px 40px -20px rgba(0, 0, 0, 0.4);
    padding: 0.85rem 1rem;
    font-size: 0.8125rem;
    line-height: 1.6;
    animation: concept-fade 0.12s ease-out;
  }

  @keyframes concept-fade {
    from {
      opacity: 0;
      transform: translateY(4px);
    }
    to {
      opacity: 1;
      transform: translateY(0);
    }
  }

  .concept-pop__body :global(p) {
    margin: 0 0 0.5rem;
  }

  .concept-pop__body :global(p:last-child) {
    margin-bottom: 0;
  }

  .concept-pop__body :global(code) {
    background: var(--sl-color-gray-6);
    padding: 0.1em 0.3em;
    border-radius: 3px;
    font-size: 0.92em;
  }

  .concept-pop__more {
    display: inline-block;
    margin-top: 0.6rem;
    font-size: 0.78rem;
    font-weight: 600;
    color: var(--learn-focus);
    text-decoration: none;
    border-top: 1px solid var(--learn-border);
    padding-top: 0.5rem;
    width: 100%;
  }

  .concept-pop__more:hover {
    text-decoration: underline;
  }

  @media (prefers-reduced-motion: reduce) {
    .concept-pop {
      animation: none;
    }
  }
</style>
