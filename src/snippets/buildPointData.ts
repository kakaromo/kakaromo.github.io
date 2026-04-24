// routes/trace/deckgl/buildPointData.ts — series → Deck.gl binary attribute 변환.
// ECharts buildSeries 와 동일한 필터 로직을 Float32Array/Uint8Array 로 포팅.
// 2-pass: 먼저 유효 포인트 index 수집(kn 획득) → TypedArray 할당 → 두 번째 pass 에서 값·색상 쓰기.

export function buildPointData(series: Series, opts: BuildOpts): PointBuildResult {
  const { yKey, activeActionTab, isUfsCustom, cpuMode = 'cmd', hiddenCmds } = opts;
  const colorFor = opts.colorFor ?? createCmdColorAssigner();
  const time = series.time;
  const actionArr = series.action;
  const excludeZero = LATENCY_KEYS.has(yKey);   // latency 0 값은 렌더 제외

  // CPU-LBA 모드: y = lba, 색상 = cpu 번호 (고정 팔레트 8색)
  const isCpuLba = yKey === 'cpu' && cpuMode === 'lba';
  const yArr = isCpuLba ? series.lba : (series[yKey] as number[]);
  const labelArr = isCpuLba ? series.cpu : series.cmd;

  const n = time.length;
  // (1) 1st pass: 유효 포인트 인덱스 수집 + data domain 계산
  const keep = new Int32Array(n);
  let kn = 0;
  let xMin = Infinity, xMax = -Infinity, yMin = Infinity, yMax = -Infinity;
  for (let i = 0; i < n; i++) {
    if (!actionMatchesTab(actionArr[i] ?? '', activeActionTab, isUfsCustom)) continue;
    const x = time[i]; const y = yArr[i];
    if (!Number.isFinite(x) || !Number.isFinite(y)) continue;
    if (excludeZero && y <= 0) continue;
    if (!isCpuLba && hiddenCmds && hiddenCmds.size > 0) {
      if (hiddenCmds.has(series.cmd[i] || 'unknown')) continue;
    }
    keep[kn++] = i;
    if (x < xMin) xMin = x; if (x > xMax) xMax = x;
    if (y < yMin) yMin = y; if (y > yMax) yMax = y;
  }

  // (2) TypedArray 할당 — 최종 크기가 확정된 후에만 alloc (메모리 절감)
  const positions = new Float32Array(kn * 2);   // [x0,y0, x1,y1, ...] — OrthographicView z 불필요
  const colors = new Uint8Array(kn * 4);        // [r,g,b,a, ...] — normalized=false

  const legendMap = new Map<string, { color: string; count: number }>();

  // (3) 2nd pass: positions/colors 쓰기 + legend 집계
  for (let k = 0; k < kn; k++) {
    const i = keep[k];
    positions[k * 2] = time[i];
    positions[k * 2 + 1] = yArr[i];

    let rgba: [number, number, number, number];
    let legendKey: string;
    let legendColor: string;

    if (isCpuLba) {
      const cpuIdx = Math.max(0, Math.floor(labelArr[i] as number) % CPU_COLORS.length);
      legendKey = `CPU ${labelArr[i]}`;
      legendColor = CPU_COLORS[cpuIdx];
      rgba = hexToRgba(legendColor);
    } else {
      const cmdVal = (labelArr[i] as string) || 'unknown';
      legendKey = cmdVal;
      legendColor = colorFor(cmdVal);   // cmdColors: SCSI opcode → 색상 매핑
      rgba = hexToRgba(legendColor);
    }

    colors[k * 4] = rgba[0]; colors[k * 4 + 1] = rgba[1];
    colors[k * 4 + 2] = rgba[2]; colors[k * 4 + 3] = rgba[3];

    const entry = legendMap.get(legendKey);
    if (entry) entry.count++;
    else legendMap.set(legendKey, { color: legendColor, count: 1 });
  }

  const cmdLegend = [...legendMap.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([cmd, { color, count }]) => ({ cmd, color, count }));

  return { positions, colors, length: kn, cmdLegend,
           xDomain: [xMin, xMax], yDomain: [yMin, yMax] };
}
