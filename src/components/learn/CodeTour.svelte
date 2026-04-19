<script lang="ts">
  // 스크롤 싱크 코드 투어.
  // 왼쪽: step별 markdown 본문. 오른쪽: sticky 코드 패널.
  // 각 step의 뷰포트 진입을 IntersectionObserver로 감지해 코드의 하이라이트/스크롤을 이동.
  import { onMount, tick } from 'svelte';

  interface Step {
    id: string;
    title: string;
    /** step 본문. HTML을 직접 허용하므로 작성자 신뢰 전제. MDX에서 slot 대신 이 필드로 주입. */
    body: string;
    /** 하이라이트할 라인 [from, to] (1-indexed, inclusive). */
    lines: [number, number];
  }

  interface Props {
    /** 표시할 코드 전체 문자열. */
    code: string;
    /** 언어 힌트 (css hook 용도). */
    language?: string;
    /** 소스 파일 경로 (표시용). */
    source?: string;
    steps: Step[];
    /** 코드 패널 높이 (px). */
    height?: number;
  }

  let { code, language = 'text', source, steps, height = 520 }: Props = $props();

  let activeStepId = $state(steps[0]?.id ?? null);
  let stepEls = $state<Record<string, HTMLElement>>({});
  let codePanel: HTMLDivElement | null = $state(null);
  let lineRefs: HTMLElement[] = [];

  const activeStep = $derived(steps.find((s) => s.id === activeStepId) ?? steps[0]);
  const lines = $derived(code.split('\n'));

  // Shiki 하이라이팅 — 런타임 lazy load로 초기 번들 가볍게, 하이드레이션 후 교체.
  // 하이라이트된 라인별 HTML 배열. 로드 실패/미완료 시 null → 원문 fallback.
  let highlightedLines = $state<string[] | null>(null);

  async function runHighlight() {
    try {
      const shiki = await import('shiki');
      const lang = mapLanguage(language);
      const highlighter = await shiki.createHighlighter({
        themes: ['github-light', 'github-dark'],
        langs: [lang],
      });
      // codeToTokens 로 듀얼 테마 토큰 얻기
      const result = highlighter.codeToHtml(code, {
        lang,
        themes: { light: 'github-light', dark: 'github-dark' },
        defaultColor: false,
      });
      // result는 <pre><code>...</code></pre> 한 덩어리. 라인별로 잘라낸다.
      const lineHtmls = extractLineHtmls(result);
      if (lineHtmls.length === lines.length) {
        highlightedLines = lineHtmls;
      }
      highlighter.dispose();
    } catch (e) {
      // 하이라이터 실패 시 원문 유지 — 치명적이지 않음
      console.warn('[CodeTour] shiki highlight failed:', e);
    }
  }

  function mapLanguage(lang: string): string {
    // MDX에서 받는 언어명을 Shiki가 이해하는 이름으로 매핑
    const table: Record<string, string> = {
      ts: 'ts',
      typescript: 'ts',
      js: 'js',
      javascript: 'js',
      java: 'java',
      yaml: 'yaml',
      yml: 'yaml',
      svelte: 'svelte',
      html: 'html',
      css: 'css',
      json: 'json',
      text: 'text',
    };
    return table[lang.toLowerCase()] ?? 'text';
  }

  function extractLineHtmls(shikiHtml: string): string[] {
    // shiki 결과에서 <span class="line">...</span> 덩어리만 추출
    const m = shikiHtml.match(/<code[^>]*>([\s\S]*)<\/code>/);
    if (!m) return [];
    const inner = m[1];
    // 각 line은 <span class="line">...</span><br>? 또는 \n 구분
    // shiki v4는 <span class="line">로 감싼다. 그렇지 않으면 \n으로 split.
    const lineMatches = inner.match(/<span class="line">[\s\S]*?<\/span>/g);
    if (lineMatches && lineMatches.length > 0) {
      return lineMatches.map((s) =>
        s.replace(/^<span class="line">/, '').replace(/<\/span>$/, ''),
      );
    }
    // fallback: 줄바꿈 기준 split
    return inner.split('\n');
  }

  function setRef(el: HTMLElement | null, id: string) {
    if (el) stepEls[id] = el;
  }

  onMount(() => {
    runHighlight();
    if (typeof IntersectionObserver === 'undefined') return;
    const obs = new IntersectionObserver(
      (entries) => {
        // 뷰포트 상단 30% 구간에 가장 많이 걸친 step을 활성화.
        const visible = entries
          .filter((e) => e.isIntersecting)
          .sort((a, b) => b.intersectionRatio - a.intersectionRatio);
        if (visible[0]) {
          const id = (visible[0].target as HTMLElement).dataset.stepId;
          if (id) activeStepId = id;
        }
      },
      {
        rootMargin: '-30% 0px -55% 0px',
        threshold: [0, 0.25, 0.5, 0.75, 1],
      },
    );
    for (const id of Object.keys(stepEls)) {
      obs.observe(stepEls[id]);
    }
    return () => obs.disconnect();
  });

  // 활성 step이 바뀌면 코드 패널을 해당 라인으로 스크롤.
  $effect(() => {
    if (!activeStep || !codePanel) return;
    const [from] = activeStep.lines;
    const target = lineRefs[from - 1];
    if (!target) return;
    const panelRect = codePanel.getBoundingClientRect();
    const targetRect = target.getBoundingClientRect();
    const offset = targetRect.top - panelRect.top;
    const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    codePanel.scrollTo({
      top: codePanel.scrollTop + offset - panelRect.height * 0.25,
      behavior: reduced ? 'auto' : 'smooth',
    });
  });

  function isLineHighlighted(ln: number) {
    if (!activeStep) return false;
    const [from, to] = activeStep.lines;
    return ln >= from && ln <= to;
  }

  function selectStep(id: string) {
    activeStepId = id;
    const el = stepEls[id];
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
</script>

<div class="learn-tour">
  <div class="learn-tour__narrative">
    {#each steps as step, i (step.id)}
      <section
        class="learn-tour__step"
        class:learn-tour__step--active={activeStepId === step.id}
        data-step-id={step.id}
        use:setRef={step.id}
      >
        <button
          class="learn-tour__step-title"
          type="button"
          onclick={() => selectStep(step.id)}
        >
          <span class="learn-tour__step-num">{String(i + 1).padStart(2, '0')}</span>
          <span>{step.title}</span>
        </button>
        <div class="learn-tour__step-body">
          {@html step.body}
        </div>
      </section>
    {/each}
  </div>

  <aside class="learn-tour__panel" style:--tour-height="{height}px">
    <header class="learn-tour__panel-header">
      {#if source}
        <span class="learn-tour__source">{source}</span>
      {/if}
      {#if activeStep}
        <span class="learn-tour__lines">
          L{activeStep.lines[0]}–{activeStep.lines[1]}
        </span>
      {/if}
    </header>
    <div
      class="learn-tour__code"
      bind:this={codePanel}
      data-language={language}
      role="region"
      aria-label="코드"
    >
      <pre><code>{#each lines as line, i}{@const ln = i + 1}{@const hl = highlightedLines?.[i]}<span
              class="learn-tour__line"
              class:learn-tour__line--hl={isLineHighlighted(ln)}
              bind:this={lineRefs[i]}
            ><span class="learn-tour__gutter">{ln}</span>{#if hl}<span
                class="learn-tour__linecode"
              >{@html hl || '&nbsp;'}</span>{:else}<span class="learn-tour__linecode"
              >{line || ' '}</span>{/if}</span>{/each}</code></pre>
    </div>
  </aside>
</div>

<style>
  .learn-tour {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1.1fr);
    gap: 2rem;
    margin: 2rem 0;
    align-items: start;
  }

  @media (max-width: 900px) {
    .learn-tour {
      grid-template-columns: 1fr;
    }
  }

  .learn-tour__narrative {
    display: flex;
    flex-direction: column;
    gap: 3rem;
  }

  .learn-tour__step {
    position: relative;
    padding-left: 1rem;
    border-left: 3px solid var(--sl-color-gray-5);
    transition: border-color 0.15s ease;
  }

  .learn-tour__step--active {
    border-left-color: var(--learn-focus);
  }

  .learn-tour__step-title {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    background: none;
    border: 0;
    padding: 0;
    text-align: left;
    font-size: 1.05rem;
    font-weight: 700;
    color: inherit;
    cursor: pointer;
  }

  .learn-tour__step-title:focus-visible {
    outline: 2px solid var(--learn-focus);
    outline-offset: 3px;
    border-radius: 4px;
  }

  .learn-tour__step-num {
    font-family: var(--sl-font-mono);
    font-size: 0.75rem;
    color: var(--learn-focus);
    background: var(--sl-color-accent-low);
    padding: 0.15rem 0.5rem;
    border-radius: 999px;
  }

  .learn-tour__step-body {
    margin-top: 0.5rem;
    font-size: 0.875rem;
    line-height: 1.7;
  }

  .learn-tour__step-body :global(p) {
    margin: 0.5rem 0;
  }

  .learn-tour__step-body :global(code) {
    font-size: 0.8em;
    background: var(--sl-color-gray-6);
    padding: 0.1em 0.3em;
    border-radius: 3px;
  }

  .learn-tour__panel {
    position: sticky;
    top: 1rem;
    border: 1px solid var(--learn-border);
    border-radius: var(--learn-radius-lg);
    background: var(--sl-color-bg-nav);
    overflow: hidden;
    display: flex;
    flex-direction: column;
  }

  @media (max-width: 900px) {
    .learn-tour__panel {
      position: static;
    }
  }

  .learn-tour__panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0.5rem 0.85rem;
    border-bottom: 1px solid var(--learn-border);
    font-size: 0.72rem;
    color: var(--sl-color-gray-2);
    font-family: var(--sl-font-mono);
  }

  .learn-tour__lines {
    color: var(--learn-focus);
    font-weight: 600;
  }

  .learn-tour__code {
    height: var(--tour-height);
    overflow: auto;
    scroll-behavior: smooth;
  }

  .learn-tour__code pre {
    margin: 0;
    padding: 0.75rem 0;
    font-size: 0.75rem;
    line-height: 1.55;
    background: transparent;
  }

  .learn-tour__line {
    display: flex;
    padding: 0 0.5rem;
    transition: background-color 0.15s ease;
  }

  .learn-tour__line--hl {
    background: var(--sl-color-accent-low);
    box-shadow: inset 3px 0 0 var(--learn-focus);
  }

  .learn-tour__gutter {
    flex: 0 0 3ch;
    text-align: right;
    padding-right: 1ch;
    color: var(--sl-color-gray-4);
    user-select: none;
  }

  .learn-tour__linecode {
    white-space: pre;
    flex: 1;
  }

  /* Shiki 듀얼 테마 — defaultColor: false가 남긴 --shiki-light / --shiki-dark를 테마별로 스위치 */
  .learn-tour__linecode :global(span) {
    color: var(--shiki-light);
  }
  :root[data-theme='dark'] .learn-tour__linecode :global(span) {
    color: var(--shiki-dark);
  }
  /* Shiki가 style에 박은 색은 우선 적용되므로 별도 오버라이드 불필요 */

  @media (prefers-reduced-motion: reduce) {
    .learn-tour__step,
    .learn-tour__line {
      transition: none;
    }
    .learn-tour__code {
      scroll-behavior: auto;
    }
  }
</style>
