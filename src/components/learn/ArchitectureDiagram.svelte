<script lang="ts">
  // 학습 UX 인터랙티브 아키텍처 다이어그램.
  // SVG로 배경·연결선을 그리고, 노드는 HTML div로 얹어 접근성/링크를 확보한다.
  // 노드 좌표는 props로 받으며, viewBox는 노드 bbox에 맞춰 자동 계산.

  type Layer = 'hw' | 'be' | 'state' | 'tx' | 'fe';

  interface Node {
    id: string;
    label: string;
    sublabel?: string;
    layer: Layer;
    /** x, y, w, h in viewBox units */
    x: number;
    y: number;
    w?: number;
    h?: number;
    /** 클릭 시 이동할 href. 지정되지 않으면 클릭 불가. */
    href?: string;
    /** 간단한 한 줄 설명 (호버 툴팁). */
    hint?: string;
  }

  interface Edge {
    from: string;
    to: string;
    label?: string;
    /** async(점선) vs sync(실선). 기본 'sync'. */
    kind?: 'sync' | 'async';
  }

  interface Props {
    nodes: Node[];
    edges?: Edge[];
    /** viewBox 높이. 폭은 노드 배치로 결정되며 이 값은 비율 기준. */
    height?: number;
    /** 캡션/대체 텍스트용 설명. */
    caption?: string;
    /** 대체 텍스트 세부 내용 (접기 상태). */
    altText?: string;
  }

  let { nodes, edges = [], height = 360, caption, altText }: Props = $props();

  const NODE_W = 150;
  const NODE_H = 64;
  const PAD = 24;

  const bbox = $derived.by(() => {
    const xs = nodes.map((n) => n.x);
    const ys = nodes.map((n) => n.y);
    const ws = nodes.map((n) => n.w ?? NODE_W);
    const hs = nodes.map((n) => n.h ?? NODE_H);
    const minX = Math.min(...xs) - PAD;
    const minY = Math.min(...ys) - PAD;
    const maxX = Math.max(...xs.map((x, i) => x + ws[i])) + PAD;
    const maxY = Math.max(...ys.map((y, i) => y + hs[i])) + PAD;
    return { minX, minY, width: maxX - minX, height: maxY - minY };
  });

  const nodeMap = $derived(new Map(nodes.map((n) => [n.id, n])));

  let hoveredId = $state<string | null>(null);
  let focusedId = $state<string | null>(null);
  const activeId = $derived(hoveredId ?? focusedId);

  function nodeCenter(n: Node) {
    return {
      x: n.x + (n.w ?? NODE_W) / 2,
      y: n.y + (n.h ?? NODE_H) / 2,
    };
  }

  /**
   * 두 노드를 잇는 엣지 경로.
   * 노드 경계에서 시작·끝나게 하기 위해 중심 간 벡터로 rect 교점을 구한다.
   */
  function edgePath(edge: Edge): { d: string; mid: { x: number; y: number } } | null {
    const a = nodeMap.get(edge.from);
    const b = nodeMap.get(edge.to);
    if (!a || !b) return null;
    const ca = nodeCenter(a);
    const cb = nodeCenter(b);
    const p1 = rectEdgeIntersection(a, ca, cb);
    const p2 = rectEdgeIntersection(b, cb, ca);
    const mx = (p1.x + p2.x) / 2;
    const my = (p1.y + p2.y) / 2;
    // 부드러운 2차 베지어 — 직선보다 계층 구조 느낌.
    const dx = p2.x - p1.x;
    const dy = p2.y - p1.y;
    const len = Math.sqrt(dx * dx + dy * dy) || 1;
    const curve = Math.min(40, len * 0.18);
    // 수직 방향으로 약간 휘게
    const nx = -dy / len;
    const ny = dx / len;
    const cx = mx + nx * curve;
    const cy = my + ny * curve;
    return {
      d: `M ${p1.x} ${p1.y} Q ${cx} ${cy} ${p2.x} ${p2.y}`,
      mid: { x: cx, y: cy },
    };
  }

  function rectEdgeIntersection(
    node: Node,
    from: { x: number; y: number },
    to: { x: number; y: number },
  ) {
    const w = (node.w ?? NODE_W) / 2;
    const h = (node.h ?? NODE_H) / 2;
    const dx = to.x - from.x;
    const dy = to.y - from.y;
    if (dx === 0 && dy === 0) return { x: from.x, y: from.y };
    const scale = Math.min(
      dx !== 0 ? Math.abs(w / dx) : Infinity,
      dy !== 0 ? Math.abs(h / dy) : Infinity,
    );
    return {
      x: from.x + dx * scale,
      y: from.y + dy * scale,
    };
  }

  const edgePaths = $derived(edges.map((e) => ({ edge: e, path: edgePath(e) })));

  function isEdgeActive(e: Edge) {
    if (!activeId) return false;
    return e.from === activeId || e.to === activeId;
  }

  function isNodeDimmed(id: string) {
    if (!activeId) return false;
    if (id === activeId) return false;
    // 활성 노드와 직접 연결된 노드는 유지
    const connected = edges.some(
      (e) =>
        (e.from === activeId && e.to === id) || (e.to === activeId && e.from === id),
    );
    return !connected;
  }

  function onKey(e: KeyboardEvent, id: string) {
    const node = nodeMap.get(id);
    if (!node) return;
    if (e.key === 'Enter' || e.key === ' ') {
      if (node.href) {
        e.preventDefault();
        window.location.href = node.href;
      }
    }
  }
</script>

<figure class="learn-arch">
  <svg
    role="img"
    aria-label={caption ?? '아키텍처 다이어그램'}
    viewBox="{bbox.minX} {bbox.minY} {bbox.width} {bbox.height}"
    preserveAspectRatio="xMidYMid meet"
    style:height="{height}px"
  >
    <defs>
      <marker
        id="arrow"
        viewBox="0 0 10 10"
        refX="9"
        refY="5"
        markerWidth="6"
        markerHeight="6"
        orient="auto-start-reverse"
      >
        <path d="M0,0 L10,5 L0,10 z" fill="currentColor" />
      </marker>
    </defs>

    {#each edgePaths as item (item.edge.from + '-' + item.edge.to)}
      {#if item.path}
        {@const active = isEdgeActive(item.edge)}
        <g class="learn-arch__edge" class:learn-arch__edge--active={active}>
          <path
            d={item.path.d}
            fill="none"
            stroke="currentColor"
            stroke-width={active ? 2 : 1.25}
            stroke-dasharray={item.edge.kind === 'async' ? '5 4' : undefined}
            marker-end="url(#arrow)"
            opacity={activeId && !active ? 0.25 : 0.8}
          />
          {#if item.edge.label}
            <text
              x={item.path.mid.x}
              y={item.path.mid.y}
              class="learn-arch__edge-label"
              text-anchor="middle"
              dominant-baseline="middle"
              opacity={activeId && !active ? 0.3 : 1}
            >
              {item.edge.label}
            </text>
          {/if}
        </g>
      {/if}
    {/each}
  </svg>

  <div
    class="learn-arch__nodes"
    style:--arch-vb-x={bbox.minX}
    style:--arch-vb-y={bbox.minY}
    style:--arch-vb-w={bbox.width}
    style:--arch-vb-h={bbox.height}
  >
    {#each nodes as n (n.id)}
      {@const Tag = n.href ? 'a' : 'div'}
      {@const interactive = !!n.href}
      {@const dimmed = isNodeDimmed(n.id)}
      <svelte:element
        this={Tag}
        href={n.href}
        class="learn-arch__node"
        class:learn-arch__node--active={activeId === n.id}
        class:learn-arch__node--dimmed={dimmed}
        class:learn-arch__node--interactive={interactive}
        data-layer={n.layer}
        role={interactive ? 'link' : undefined}
        tabindex={interactive ? 0 : undefined}
        title={n.hint}
        style:left="calc(({n.x} - var(--arch-vb-x)) / var(--arch-vb-w) * 100%)"
        style:top="calc(({n.y} - var(--arch-vb-y)) / var(--arch-vb-h) * 100%)"
        style:width="calc({n.w ?? NODE_W} / var(--arch-vb-w) * 100%)"
        style:height="calc({n.h ?? NODE_H} / var(--arch-vb-h) * 100%)"
        onmouseenter={() => (hoveredId = n.id)}
        onmouseleave={() => (hoveredId = null)}
        onfocus={() => (focusedId = n.id)}
        onblur={() => (focusedId = null)}
        onkeydown={(e: KeyboardEvent) => onKey(e, n.id)}
      >
        <span class="learn-arch__node-label">{n.label}</span>
        {#if n.sublabel}
          <span class="learn-arch__node-sublabel">{n.sublabel}</span>
        {/if}
        {#if interactive}
          <span class="learn-arch__node-arrow" aria-hidden="true">↗</span>
        {/if}
      </svelte:element>
    {/each}
  </div>

  {#if caption}
    <figcaption class="learn-arch__caption">{caption}</figcaption>
  {/if}

  {#if altText}
    <details class="learn-arch__alt">
      <summary>다이어그램 텍스트 설명 보기</summary>
      <p>{altText}</p>
    </details>
  {/if}
</figure>

<style>
  .learn-arch {
    position: relative;
    margin: 1.5rem 0;
    border: 1px solid var(--learn-border);
    border-radius: var(--learn-radius-lg);
    padding: 1rem;
    background: var(--sl-color-bg-nav);
  }

  .learn-arch svg {
    display: block;
    width: 100%;
    height: auto;
    color: var(--sl-color-gray-3);
  }

  .learn-arch__nodes {
    position: absolute;
    inset: 1rem;
    pointer-events: none;
  }

  .learn-arch__node {
    position: absolute;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: flex-start;
    gap: 2px;
    padding: 0.5rem 0.75rem;
    border-radius: var(--learn-radius);
    background: var(--sl-color-bg);
    border: 1px solid currentColor;
    color: var(--learn-layer-be);
    text-decoration: none;
    pointer-events: auto;
    transition: transform 0.15s ease, box-shadow 0.15s ease, opacity 0.15s ease,
      background-color 0.15s ease;
    overflow: hidden;
    word-break: keep-all;
  }

  .learn-arch__node[data-layer='hw']    { color: var(--learn-layer-hw); }
  .learn-arch__node[data-layer='be']    { color: var(--learn-layer-be); }
  .learn-arch__node[data-layer='state'] { color: var(--learn-layer-state); }
  .learn-arch__node[data-layer='tx']    { color: var(--learn-layer-tx); }
  .learn-arch__node[data-layer='fe']    { color: var(--learn-layer-fe); }

  .learn-arch__node[data-layer='hw']    { background: var(--learn-layer-hw-bg); }
  .learn-arch__node[data-layer='be']    { background: var(--learn-layer-be-bg); }
  .learn-arch__node[data-layer='state'] { background: var(--learn-layer-state-bg); }
  .learn-arch__node[data-layer='tx']    { background: var(--learn-layer-tx-bg); }
  .learn-arch__node[data-layer='fe']    { background: var(--learn-layer-fe-bg); }

  .learn-arch__node--interactive {
    cursor: pointer;
  }

  .learn-arch__node--interactive:hover,
  .learn-arch__node--active {
    transform: translateY(-1px);
    box-shadow: 0 6px 20px -10px currentColor;
    z-index: 2;
  }

  .learn-arch__node:focus-visible {
    outline: 2px solid var(--learn-focus);
    outline-offset: 2px;
  }

  .learn-arch__node--dimmed {
    opacity: 0.35;
  }

  .learn-arch__node-label {
    font-size: 0.8125rem;
    font-weight: 700;
    color: var(--sl-color-white);
    line-height: 1.15;
  }

  :root[data-theme='light'] .learn-arch__node-label {
    color: var(--sl-color-black);
  }

  .learn-arch__node-sublabel {
    font-size: 0.7rem;
    opacity: 0.75;
    color: var(--sl-color-gray-2);
    line-height: 1.15;
  }

  .learn-arch__node-arrow {
    position: absolute;
    right: 0.35rem;
    bottom: 0.2rem;
    font-size: 0.7rem;
    opacity: 0.6;
  }

  .learn-arch__edge {
    color: var(--sl-color-gray-3);
    transition: opacity 0.15s ease, color 0.15s ease;
  }

  .learn-arch__edge--active {
    color: var(--learn-focus);
  }

  .learn-arch__edge-label {
    font-size: 10px;
    fill: var(--sl-color-gray-2);
    paint-order: stroke;
    stroke: var(--sl-color-bg);
    stroke-width: 3px;
    stroke-linejoin: round;
  }

  .learn-arch__caption {
    margin-top: 0.75rem;
    font-size: 0.8125rem;
    color: var(--sl-color-gray-2);
    text-align: center;
  }

  .learn-arch__alt {
    margin-top: 0.75rem;
    font-size: 0.8125rem;
  }

  .learn-arch__alt summary {
    cursor: pointer;
    color: var(--sl-color-gray-2);
  }

  @media (prefers-reduced-motion: reduce) {
    .learn-arch__node {
      transition: none;
    }
    .learn-arch__node--interactive:hover,
    .learn-arch__node--active {
      transform: none;
    }
  }
</style>
