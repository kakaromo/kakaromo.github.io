---
title: 새 파서 추가하기
description: 성능 테스트 결과 시각화를 위한 새 파서 추가 단계별 가이드
---

성능 테스트 결과의 시각화는 파서(Parser) 단위로 관리됩니다. 새로운 파서를 추가하려면 다음 단계를 따릅니다.

## 전체 흐름

```
1. DB에 Parser 레코드 추가
2. Frontend에 시각화 컴포넌트 생성
3. parserRegistry.ts에 매핑 등록
4. (선택) Go Excel Service에 Generator 추가
5. 테스트 및 확인
```

---

## Step 1: DB에 PerformanceParser 레코드 추가

`testdb` 데이터베이스의 `performance_parsers` 테이블에 새 파서를 등록합니다.

```sql
INSERT INTO performance_parsers (PARSER_NAME) VALUES ('new-parser-name');
```

등록 후 자동 생성된 `ID`를 확인합니다. 이 값이 `parserId`로 사용됩니다.

---

## Step 2: Frontend 시각화 컴포넌트 생성

`frontend/src/lib/components/perf-content/` 디렉토리에 새 Svelte 컴포넌트를 생성합니다.

### 기본 패턴

대부분의 파서 컴포넌트는 `PerfChart` + `DataTable` 조합을 사용합니다:

```svelte
<script lang="ts">
  import PerfChart from '$lib/components/perf-chart/PerfChart.svelte';
  import DataTable from '$lib/components/data-table/DataTable.svelte';
  import type { ColumnDef } from '@tanstack/table-core';

  let { data, tcName = '', fw = '' }: {
    data: Record<string, any>;
    tcName?: string;
    fw?: string;
  } = $props();

  // 데이터 가공 및 차트 옵션 구성
  let chartOption = $derived(buildChartOption(data));
  let tableData = $derived(buildTableData(data));
  let columns: ColumnDef<any>[] = [...];

  function buildChartOption(data: Record<string, any>) {
    return {
      title: { text: tcName, subtext: fw },
      xAxis: { type: 'category', data: [...] },
      yAxis: { type: 'value' },
      series: [{ type: 'line', data: [...] }],
      dataZoom: [{ type: 'inside' }]
    };
  }
</script>

<div class="space-y-4">
  <PerfChart option={chartOption} height="400px" />
  <DataTable {data} {columns} enableCellCopy={true} />
</div>
```

### 기존 컴포넌트 참고

| 컴포넌트 | 특징 |
|----------|------|
| `GenPerf.svelte` | 범용 Read/Write/FlushTime 탭, Line/Scatter 전환 |
| `FragmentWrite.svelte` | 특수 데이터 구조 시각화 |
| `WearLeveling.svelte` | 특정 도메인 차트 |
| `KernelLatency.svelte` | Latency 히스토그램 |

:::tip[Perf Generator 활용]
Admin 대시보드의 **Perf Gen** 탭에서 JSON 데이터 구조를 입력하면 `perf-content` 패턴에 맞는 Svelte 컴포넌트 코드가 자동 생성됩니다. 초기 코드 작성 시 활용하면 편리합니다.
:::

---

## Step 3: parserRegistry.ts에 매핑 등록

`frontend/src/lib/components/perf-content/parserRegistry.ts`에 parserId와 컴포넌트를 매핑합니다.

```typescript
import NewParser from './NewParser.svelte';

// registry에 등록
register(30, NewParser);  // 30 = DB의 parserId
```

이 한 줄만 추가하면 다음 페이지에서 자동으로 해당 컴포넌트가 렌더링됩니다:
- Performance History 상세 페이지
- Compare Chart Overlay
- Compare Side-by-Side
- Slots 페이지 실시간 결과

---

## Step 4: (선택) Go Excel Service에 Generator 추가

Excel Export 기능이 필요한 경우 Go Excel Service(`~/project/excel-service`)에 Generator를 추가합니다.

1. `generator/` 디렉토리에 새 Generator 구현
2. `generator/generator.go`의 registry에 parserId 매핑 추가
3. Strategy 패턴으로 기존 Generator를 확장하거나 새로 작성

---

## Step 5: 테스트 및 확인

1. **JSON 데이터 확인**: 해당 파서의 테스트 결과 JSON이 예상 구조인지 확인
   - `GET /api/performance-results/{historyId}/data` API로 확인
2. **차트 렌더링 확인**: Performance History 상세 페이지에서 차트가 정상 표시되는지 확인
3. **Excel Export 확인** (Step 4 수행 시): Excel 다운로드 후 차트/데이터 확인

## parserId 매핑 현황

현재 등록된 parserId와 컴포넌트 매핑:

| parserId | 컴포넌트 | 설명 |
|----------|----------|------|
| 2, 3, 16 | GenPerf | 범용 Read/Write |
| 4 | FragmentWrite | Fragment Write |
| 5 | PerfByChunk | Chunk별 성능 |
| 6 | IntervaledReadLatency | 읽기 지연시간 |
| 10 | WearLeveling | Wear Leveling |
| 15 | LongTermTC | 장기 TC |
| 20 | KernelLatency | 커널 지연시간 |
| 21 | WriteAndDelete | 쓰기/삭제 |
| 23 | UnmapThroughput | Unmap 처리량 |
| 24 | VluLatency | VLU 지연시간 |
| 25, 28 | VluDirtyCase4 | VLU Dirty Case |
| 26 | VluRandReadPerThread | VLU 랜덤 읽기 |
| 27 | WideRandRead | Wide 랜덤 읽기 |
| 29 | WbFlushThroughput | WB Flush 처리량 |
