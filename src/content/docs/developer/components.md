---
title: 컴포넌트 가이드
description: DataTable, GenPerf, LogBrowser, PerfChart, SlotCard 등 주요 컴포넌트 Props 및 사용법
---

MOVE 프론트엔드의 주요 컴포넌트를 설명합니다.

## DataTable

**경로:** `frontend/src/lib/components/data-table/DataTable.svelte`

TanStack Table v8 기반의 범용 데이터 테이블 컴포넌트입니다.

### 기본 Props

| Prop | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `data` | `TData[]` | 필수 | 테이블 데이터 |
| `columns` | `ColumnDef<TData>[]` | 필수 | 컬럼 정의 |
| `filterColumn` | `string` | - | 필터링 대상 컬럼 |
| `filterPlaceholder` | `string` | - | 필터 입력 placeholder |
| `enableRowSelection` | `boolean` | `false` | 행 선택 체크박스 |
| `enableMultiRowSelection` | `boolean` | `false` | 다중 행 선택 |
| `enableColumnVisibility` | `boolean` | `true` | 컬럼 표시/숨김 토글 |
| `showPagination` | `boolean` | `true` | 페이지네이션 표시 |
| `compact` | `boolean` | `false` | 컴팩트 모드 |
| `getRowId` | `(row) => string` | - | 행 고유 ID 함수 |
| `initialSorting` | `SortingState` | `[]` | 초기 정렬 상태 |
| `initialPageSize` | `number` | `20` | 초기 페이지 크기 |
| `scrollHeight` | `string` | - | 고정 높이 스크롤 (예: `"400px"`) |
| `enableCellCopy` | `boolean` | `false` | Excel-like 셀 복사 |
| `onRowDoubleClick` | `(row) => void` | - | 행 더블클릭 콜백 |
| `onSelectionChange` | `(rows) => void` | - | 선택 변경 콜백 |

### 서버사이드 설정

| Prop | 타입 | 설명 |
|------|------|------|
| `serverSide` | `ServerSideConfig` | 서버 페이지네이션 |
| `serverGroupBy` | `ServerGroupByConfig` | 서버 그룹핑 |

### 행 확장

| Prop | 타입 | 설명 |
|------|------|------|
| `expandableRowContent` | `Snippet` | 확장 행 콘텐츠 스니펫 |
| `canExpandRow` | `(row) => boolean` | 확장 가능 여부 |
| `onRowExpand` | `(row) => void` | 확장 시 콜백 (데이터 로딩 등) |

### 행 드래그

| Prop | 타입 | 설명 |
|------|------|------|
| `canDragRow` | `(row) => boolean` | 드래그 가능 여부 |
| `onRowDrop` | `(from, to) => void` | 드롭 콜백 |

### 셀 복사

`enableCellCopy=true` 설정 시:
- 마우스 드래그로 셀 범위 선택
- `Ctrl+A` — 전체 선택
- `Ctrl+C` — TSV 형식 클립보드 복사

### 사용 예시

```svelte
<DataTable
  data={rows}
  columns={columns}
  filterColumn="name"
  filterPlaceholder="이름 검색..."
  enableRowSelection={true}
  scrollHeight="400px"
  enableCellCopy={true}
  getRowId={(row) => String(row.id)}
  onRowDoubleClick={(row) => goto(`/detail/${row.id}`)}
/>
```

---

## GenPerf

**경로:** `frontend/src/lib/components/perf-content/GenPerf.svelte`

성능 테스트 결과 시각화 컴포넌트입니다. Read/Write/FlushTime 데이터를 차트 + 테이블로 표시합니다.

### Props

| Prop | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `data` | `Record<string, CycleEntry[]>` | 필수 | 탭별 성능 데이터 |
| `tcName` | `string` | 필수 | TC 이름 (Y축 단위 자동 판별) |
| `fw` | `string` | `''` | 펌웨어 버전 (차트 subtitle) |

### CycleEntry 구조

```typescript
interface CycleEntry {
  cycle: number;
  data: number[];
  min: number;
  max: number;
  avg: number;
}
```

### 기능

- **탭**: 데이터 키 `read`, `write`, `flushtime` 자동 감지 (대소문자 무관)
- **차트 타입**: Line / Scatter 전환
- **Min/Max 마커**: 토글 버튼으로 각 시리즈의 최솟값/최댓값을 마커로 표시 (기본: 비활성)
- **Y축 단위**: tcName에 `rand` 포함 시 IOPS, `seq` 포함 시 MB/s
- **Raw Data 테이블**: 접기/펼치기, 셀 복사 가능
- **Statistics 테이블**: Cycle별 Min/Max/Avg

---

## LogBrowserDialog

**경로:** `frontend/src/lib/components/LogBrowserDialog.svelte`

SSH 원격 서버의 파일 시스템을 탐색하는 다이얼로그입니다.

| Prop | 타입 | 설명 |
|------|------|------|
| `open` | `boolean` (bindable) | 열림/닫힘 상태 |
| `tentacleName` | `string` | Tentacle 서버명 (T1, T2, ...) |
| `initialPath` | `string` | 초기 경로 |
| `title` | `string` | 다이얼로그 제목 |
| `onClose` | `() => void` | 닫기 콜백 |

디렉토리 트리 탐색, 파일 크기/수정일 표시, LogViewerDialog 연동, 파일 다운로드, 전체화면 토글을 지원합니다.

---

## LogViewerDialog

**경로:** `frontend/src/lib/components/LogViewerDialog.svelte`

로그 파일 내용을 조회/검색하는 다이얼로그입니다.

| Prop | 타입 | 설명 |
|------|------|------|
| `open` | `boolean` (bindable) | 열림/닫힘 상태 |
| `tentacleName` | `string` | Tentacle 서버명 |
| `filePath` | `string` | 파일 전체 경로 |
| `onClose` | `() => void` | 닫기 콜백 |

주요 기능:
- 1000줄씩 청크 로딩 (스크롤 시 자동 추가)
- "Last" 버튼 — 파일 끝 2000줄
- `rg` 패턴 검색 (최대 500건) + 결과 클릭 시 해당 라인으로 점프
- 바이너리 파일 감지 + "Open Anyway" 강제 열기
- non-UTF-8 인코딩 자동 감지 및 변환
- `Ctrl+A` — 검색 결과 전체 클립보드 복사
- 다크 터미널 스타일 UI

---

## PerfChart

**경로:** `frontend/src/lib/components/perf-chart/PerfChart.svelte`

ECharts 래퍼 컴포넌트입니다. "shine" 테마가 적용됩니다.

| Prop | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `option` | `EChartsOption` | 필수 | ECharts 설정 객체 |
| `height` | `string` | `"400px"` | 차트 높이 |

차트 줌은 마우스 휠로 가능합니다 (`dataZoom: [{ type: 'inside' }]`).

### 로딩 인디케이터

ECharts의 `finished` 이벤트를 감지하여 차트 렌더링 완료 전까지 반투명 오버레이 + 스피너를 자동 표시합니다. progressive 렌더링(대용량 데이터)이 활성화된 경우에도 실제 렌더링 완료 시점에 정확히 사라집니다.

### 다크 모드 대응

`MutationObserver`로 `document.documentElement`의 `class` 변경을 감지하여 테마 전환 시 자동으로 차트를 재생성합니다. `shine` (라이트) / `shine-dark` (다크) 테마가 적용됩니다.

---

## SlotCard

**경로:** `frontend/src/lib/components/SlotCard.svelte`

슬롯/디바이스 상태를 카드 형태로 표시합니다.

| Prop | 타입 | 설명 |
|------|------|------|
| `slot` | `SlotInfomation` | 슬롯 DB 정보 |
| `headData` | `HeadSlotData` | Head 실시간 데이터 |
| `selected` | `boolean` | 선택 상태 |
| `compact` | `boolean` | 컴팩트 모드 |
| `onclick` | `(e) => void` | 클릭 이벤트 |
| `oncontextmenu` | `(e) => void` | 우클릭 이벤트 |

### 상태별 색상

상태 → 색상 매핑은 `$lib/config/slotState.ts`에서 중앙 관리됩니다. 새 상태 추가 시 `EXACT_STATE_COLORS`에 한 줄만 추가하면 SlotCard, ResultCell, 대시보드 모두 자동 반영됩니다.

| 상태 | 색상 토큰 | 아이콘 | 애니메이션 |
|------|-----------|--------|-----------|
| pass | emerald | circle-dot | - |
| warning_pass | amber | triangle-exclamation | - |
| warning | amber | triangle-exclamation | - |
| fail | red | circle-xmark | - |
| critical_fail | fuchsia | circle-xmark | - |
| running | emerald | spinner | animated |
| booting | cyan | spinner | animated |
| stop | gray | stop | - |
| disconnect | slate | hourglass | - |
| provisioning 등 | violet | spinner | animated |

---

## 셀 렌더러

DataTable 컬럼에 사용하는 커스텀 셀 렌더러입니다.

| 렌더러 | 용도 |
|--------|------|
| **ResultCell** | 테스트 결과를 색상 뱃지로 표시. `slotState.ts`의 색상 토큰 사용 (16종) |
| **DateCell** | 날짜를 `ko-KR` 로케일로 포맷팅 |
| **LogBrowseCell** | 로그 브라우저 열기 버튼 |
| **SelectCell** | 행 선택 체크박스 (`mode: 'all'` 전체, `mode: 'row'` 개별) |
| **BookmarkCell** | 북마크 토글 아이콘 버튼 |
| **StatusCell** | 상태 표시 (dot + text: Processed, Processing, New 등) |
| **CompareToggleCell** | 비교 목록 추가/제거 토글 버튼 (`+` / check 아이콘) |

---

## ConfirmDialog

**경로:** `frontend/src/lib/components/ConfirmDialog.svelte`

네이티브 `confirm()`을 대체하는 커스텀 확인 다이얼로그입니다. 삭제 등 파괴적 액션에 사용됩니다.

| Prop | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `open` | `boolean` (bindable) | 필수 | 열림/닫힘 상태 |
| `title` | `string` | `'확인'` | 다이얼로그 제목 |
| `description` | `string` | 필수 | 설명 텍스트 |
| `confirmLabel` | `string` | `'확인'` | 확인 버튼 텍스트 |
| `cancelLabel` | `string` | `'취소'` | 취소 버튼 텍스트 |
| `variant` | `'destructive' \| 'default'` | `'destructive'` | 스타일 (destructive: 경고 아이콘 + 빨간 버튼) |
| `onConfirm` | `() => void \| Promise<void>` | 필수 | 확인 콜백 (async 지원, 실행 중 스피너 표시) |
| `onCancel` | `() => void` | 필수 | 취소 콜백 |

### 사용 예시

```svelte
<ConfirmDialog
  bind:open={confirmOpen}
  title="삭제 확인"
  description="이 항목을 삭제하시겠습니까?"
  confirmLabel="삭제"
  onConfirm={async () => {
    await deleteItem(id);
    confirmOpen = false;
  }}
  onCancel={() => { confirmOpen = false; }}
/>
```

---

## TableSkeleton

**경로:** `frontend/src/lib/components/TableSkeleton.svelte`

데이터 로딩 중 테이블 형태의 스켈레톤을 표시합니다. 전체 페이지 스피너를 대체합니다.

| Prop | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `columns` | `number` | `5` | 컬럼 수 |
| `rows` | `number` | `5` | 행 수 |

### 사용 예시

```svelte
{#if loading}
  <TableSkeleton columns={7} rows={6} />
{:else}
  <DataTable {data} {columns} />
{/if}
```

---

## 토스트 알림 (svelte-sonner)

**Toaster 위치:** `+layout.svelte`에 `<Toaster richColors position="top-right" />`로 글로벌 설정.

모든 페이지에서 `import { toast } from 'svelte-sonner'`로 사용합니다.

```typescript
toast.success('저장되었습니다');
toast.error('저장에 실패했습니다');
toast.error(e instanceof Error ? e.message : '오류가 발생했습니다');
```

API 클라이언트(`client.ts`)가 409 응답의 `error` 필드를 자동 파싱하므로, 서버에서 `Map.of("error", message)`로 응답하면 프론트에서 해당 메시지가 `toast.error()`로 표시됩니다.

---

## shadcn-svelte 컴포넌트

`frontend/src/lib/components/ui/` 하위에 다음 UI 기본 컴포넌트가 있습니다:

| 컴포넌트 | 용도 |
|----------|------|
| `badge` | 상태/태그 뱃지 |
| `button` | 버튼 (default, outline, ghost, destructive) |
| `card` | 카드 컨테이너 |
| `checkbox` | 체크박스 |
| `context-menu` | 우클릭 메뉴 |
| `dialog` | 모달 다이얼로그 |
| `dropdown-menu` | 드롭다운 메뉴 |
| `input` | 텍스트 입력 |
| `select` | 셀렉트 박스 |
| `separator` | 구분선 |
| `sheet` | 사이드 시트 |
| `skeleton` | 로딩 스켈레톤 |
| `table` | HTML 테이블 래퍼 |
| `tabs` | 탭 인터페이스 |
| `tooltip` | 툴팁 |
