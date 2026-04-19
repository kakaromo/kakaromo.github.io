<script lang="ts">
  // 레인 간 메시지 교환을 시간순 애니메이션으로 보여주는 시퀀스 다이어그램.
  // lanes: 수직 세로선으로 그려짐. messages: from→to 화살표가 t 시각에 나타남.
  // 재생·정지·속도 버튼 + prefers-reduced-motion 존중.
  import { onMount } from 'svelte';

  type Layer = 'hw' | 'be' | 'state' | 'tx' | 'fe';

  interface Lane {
    id: string;
    label: string;
    layer?: Layer;
    sublabel?: string;
  }

  interface Message {
    from: string;
    to: string;
    label: string;
    /** 0 이상의 시각(임의 단위, 보통 0..100). 같은 t를 가진 메시지는 동시 발생. */
    t: number;
    /** sync = 실선 (요청-응답), async = 점선 (이벤트) */
    kind?: 'sync' | 'async';
    /** 메시지 본문(툴팁/보조) */
    note?: string;
  }

  interface Props {
    lanes: Lane[];
    messages: Message[];
    /** 기본 재생 속도 (ms per t-unit). */
    speed?: number;
    autoplay?: boolean;
    caption?: string;
    altText?: string;
    /** 전체 높이 (px) */
    height?: number;
  }

  let {
    lanes,
    messages,
    speed = 30,
    autoplay = true,
    caption,
    altText,
    height = 360,
  }: Props = $props();

  const LANE_SPACING = 180;
  const LANE_TOP = 60;
  const MSG_START = LANE_TOP + 40;
  const MSG_STEP = 44;

  const maxT = $derived(Math.max(0, ...messages.map((m) => m.t)));
  const viewWidth = $derived(Math.max(600, lanes.length * LANE_SPACING));
  const viewHeight = $derived(
    MSG_START + Math.max(3, messages.length) * MSG_STEP + 40,
  );

  const laneX = $derived.by(() => {
    const map = new Map<string, number>();
    lanes.forEach((lane, i) => {
      map.set(lane.id, LANE_SPACING / 2 + i * LANE_SPACING);
    });
    return map;
  });

  // 정렬된 메시지 리스트 — t 기준, 같은 t는 원래 순서 유지.
  const ordered = $derived(
    [...messages]
      .map((m, i) => ({ msg: m, origIdx: i }))
      .sort((a, b) => a.msg.t - b.msg.t || a.origIdx - b.origIdx),
  );

  let currentT = $state(0);
  let playing = $state(false);
  let speedMul = $state(1);
  let reduced = $state(false);

  onMount(() => {
    reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
    if (autoplay && !reduced) {
      play();
    } else {
      // 축소 모션: 전체를 한 번에 표시
      currentT = maxT;
    }
    return () => stop();
  });

  let rafId = 0;
  let lastTs = 0;
  function tick(ts: number) {
    if (!playing) return;
    if (!lastTs) lastTs = ts;
    const dt = ts - lastTs;
    lastTs = ts;
    currentT += (dt / speed) * speedMul;
    if (currentT >= maxT + 2) {
      // 끝에서 1초 쉬고 반복
      currentT = maxT + 2;
      playing = false;
      setTimeout(() => {
        currentT = 0;
        play();
      }, 1500);
      return;
    }
    rafId = requestAnimationFrame(tick);
  }
  function play() {
    if (reduced) {
      currentT = maxT;
      return;
    }
    if (currentT >= maxT + 2) currentT = 0;
    playing = true;
    lastTs = 0;
    rafId = requestAnimationFrame(tick);
  }
  function stop() {
    playing = false;
    cancelAnimationFrame(rafId);
  }
  function reset() {
    stop();
    currentT = 0;
  }
  function skipToEnd() {
    stop();
    currentT = maxT;
  }

  function isVisible(m: Message) {
    return currentT >= m.t;
  }

  function messageY(idx: number) {
    return MSG_START + idx * MSG_STEP;
  }

  function pathD(m: Message, idx: number) {
    const x1 = laneX.get(m.from) ?? 0;
    const x2 = laneX.get(m.to) ?? 0;
    const y = messageY(idx);
    return `M ${x1} ${y} L ${x2} ${y}`;
  }
</script>

<figure class="learn-seq">
  <div class="learn-seq__controls">
    <button type="button" onclick={playing ? stop : play} aria-label={playing ? '정지' : '재생'}>
      {playing ? '⏸' : '▶'}
    </button>
    <button type="button" onclick={reset} aria-label="처음부터">⟲</button>
    <button type="button" onclick={skipToEnd} aria-label="끝으로">⏭</button>
    <label class="learn-seq__speed">
      속도
      <select bind:value={speedMul}>
        <option value={0.5}>0.5×</option>
        <option value={1}>1×</option>
        <option value={2}>2×</option>
        <option value={4}>4×</option>
      </select>
    </label>
    <span class="learn-seq__progress">
      t = {currentT.toFixed(0)} / {maxT}
    </span>
  </div>

  <svg
    role="img"
    aria-label={caption ?? '시퀀스 다이어그램'}
    viewBox="0 0 {viewWidth} {viewHeight}"
    style:height="{height}px"
  >
    <defs>
      <marker
        id="seq-arrow"
        viewBox="0 0 10 10"
        refX="9"
        refY="5"
        markerWidth="7"
        markerHeight="7"
        orient="auto-start-reverse"
      >
        <path d="M0,0 L10,5 L0,10 z" fill="currentColor" />
      </marker>
    </defs>

    <!-- 레인 세로선 -->
    {#each lanes as lane (lane.id)}
      {@const x = laneX.get(lane.id) ?? 0}
      <g class="learn-seq__lane" data-layer={lane.layer ?? 'be'}>
        <rect
          x={x - 60}
          y={LANE_TOP - 28}
          width="120"
          height="48"
          rx="6"
          class="learn-seq__lane-head"
        />
        <text x={x} y={LANE_TOP - 10} text-anchor="middle" class="learn-seq__lane-label">
          {lane.label}
        </text>
        {#if lane.sublabel}
          <text x={x} y={LANE_TOP + 6} text-anchor="middle" class="learn-seq__lane-sublabel">
            {lane.sublabel}
          </text>
        {/if}
        <line
          x1={x}
          y1={LANE_TOP + 20}
          x2={x}
          y2={viewHeight - 20}
          class="learn-seq__lane-line"
        />
      </g>
    {/each}

    <!-- 메시지 화살표 -->
    {#each ordered as { msg, origIdx }, i (origIdx)}
      {@const visible = isVisible(msg)}
      {@const y = messageY(i)}
      {@const x1 = laneX.get(msg.from) ?? 0}
      {@const x2 = laneX.get(msg.to) ?? 0}
      <g
        class="learn-seq__msg"
        class:learn-seq__msg--hidden={!visible}
        class:learn-seq__msg--async={msg.kind === 'async'}
      >
        <path
          d={pathD(msg, i)}
          class="learn-seq__msg-path"
          stroke-dasharray={msg.kind === 'async' ? '5 4' : undefined}
          marker-end="url(#seq-arrow)"
        />
        <text
          x={(x1 + x2) / 2}
          y={y - 6}
          text-anchor="middle"
          class="learn-seq__msg-label"
        >
          {msg.label}
        </text>
      </g>
    {/each}
  </svg>

  {#if caption}
    <figcaption class="learn-seq__caption">{caption}</figcaption>
  {/if}

  {#if altText}
    <details class="learn-seq__alt">
      <summary>시퀀스 텍스트 설명 보기</summary>
      <ol>
        {#each ordered as { msg }, i (i)}
          <li>
            <strong>{msg.from} → {msg.to}</strong>: {msg.label}
            {#if msg.note}<br /><span class="dim">{msg.note}</span>{/if}
          </li>
        {/each}
      </ol>
    </details>
  {/if}
</figure>

<style>
  .learn-seq {
    margin: 1.5rem 0;
    border: 1px solid var(--learn-border);
    border-radius: var(--learn-radius-lg);
    padding: 1rem;
    background: var(--sl-color-bg-nav);
  }

  .learn-seq__controls {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    margin-bottom: 0.75rem;
    font-size: 0.8rem;
  }

  .learn-seq__controls button {
    width: 2rem;
    height: 2rem;
    border: 1px solid var(--learn-border);
    border-radius: 6px;
    background: var(--sl-color-bg);
    cursor: pointer;
    font-size: 0.9rem;
  }

  .learn-seq__controls button:hover,
  .learn-seq__controls button:focus-visible {
    border-color: var(--learn-focus);
    color: var(--learn-focus);
    outline: none;
  }

  .learn-seq__speed {
    display: inline-flex;
    align-items: center;
    gap: 0.25rem;
    color: var(--sl-color-gray-2);
  }

  .learn-seq__speed select {
    padding: 0.15rem 0.4rem;
    border: 1px solid var(--learn-border);
    border-radius: 4px;
    background: var(--sl-color-bg);
    font: inherit;
    color: inherit;
  }

  .learn-seq__progress {
    margin-left: auto;
    font-family: var(--sl-font-mono);
    font-size: 0.75rem;
    color: var(--sl-color-gray-2);
  }

  .learn-seq svg {
    display: block;
    width: 100%;
    height: auto;
  }

  .learn-seq__lane-head {
    fill: var(--sl-color-bg);
    stroke: currentColor;
  }
  .learn-seq__lane[data-layer='hw']    { color: var(--learn-layer-hw); }
  .learn-seq__lane[data-layer='be']    { color: var(--learn-layer-be); }
  .learn-seq__lane[data-layer='state'] { color: var(--learn-layer-state); }
  .learn-seq__lane[data-layer='tx']    { color: var(--learn-layer-tx); }
  .learn-seq__lane[data-layer='fe']    { color: var(--learn-layer-fe); }

  .learn-seq__lane-head {
    fill: color-mix(in srgb, currentColor 12%, var(--sl-color-bg));
  }

  .learn-seq__lane-label {
    font-size: 12px;
    font-weight: 700;
    fill: var(--sl-color-white);
  }
  :root[data-theme='light'] .learn-seq__lane-label {
    fill: var(--sl-color-black);
  }

  .learn-seq__lane-sublabel {
    font-size: 10px;
    fill: var(--sl-color-gray-2);
  }

  .learn-seq__lane-line {
    stroke: var(--sl-color-gray-5);
    stroke-width: 1;
    stroke-dasharray: 2 4;
  }

  .learn-seq__msg {
    color: var(--sl-color-gray-2);
    transition: opacity 0.25s ease;
  }

  .learn-seq__msg--hidden {
    opacity: 0;
    pointer-events: none;
  }

  .learn-seq__msg--async {
    color: var(--learn-layer-tx);
  }

  .learn-seq__msg-path {
    fill: none;
    stroke: currentColor;
    stroke-width: 1.5;
  }

  .learn-seq__msg-label {
    font-size: 11px;
    fill: var(--sl-color-gray-1);
    paint-order: stroke;
    stroke: var(--sl-color-bg);
    stroke-width: 3px;
    stroke-linejoin: round;
  }

  .learn-seq__caption {
    margin-top: 0.75rem;
    font-size: 0.8125rem;
    color: var(--sl-color-gray-2);
    text-align: center;
  }

  .learn-seq__alt {
    margin-top: 0.75rem;
    font-size: 0.8125rem;
  }
  .learn-seq__alt summary {
    cursor: pointer;
    color: var(--sl-color-gray-2);
  }
  .learn-seq__alt .dim {
    color: var(--sl-color-gray-3);
  }

  @media (prefers-reduced-motion: reduce) {
    .learn-seq__msg {
      transition: none;
    }
  }
</style>
