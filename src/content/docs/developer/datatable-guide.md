---
title: DataTable 컴포넌트 가이드
description: Portal DataTable의 설계, 전체 기능, 셀 컴포넌트, 확장 가이드를 누구나 쉽게 이해할 수 있도록 정리
---

DataTable은 Portal 프론트엔드의 **핵심 데이터 표시 컴포넌트**입니다. TanStack Table (Headless UI) 위에 Svelte 5 + Tailwind CSS + shadcn-svelte로 구축했으며, 정렬, 필터, 페이지네이션, 가상 스크롤, 셀 복사 등 다양한 기능을 제공합니다.

---

## 1. 파일 구조

```
frontend/src/lib/components/data-table/
├── DataTable.svelte              ← 메인 컴포넌트
├── DataTableToolbar.svelte       ← 검색 + 액션 버튼 + 컬럼 토글
├── DataTablePagination.svelte    ← 페이지네이션 컨트롤
├── DataTableColumnToggle.svelte  ← 컬럼 표시/숨기기
├── types.ts                      ← GroupBy, ServerSide 타입
├── index.ts                      ← Public exports
└── cells/                        ← 재사용 셀 컴포넌트들
    ├── index.ts
    ├── StatusCell.svelte
    ├── ResultCell.svelte
    ├── DateCell.svelte
    ├── SelectCell.svelte
    ├── BookmarkCell.svelte
    ├── LogPathCell.svelte
    ├── LogBrowseCell.svelte
    ├── MetadataBrowseCell.svelte
    ├── ViewResultCell.svelte
    ├── CompareToggleCell.svelte
    ├── CompareOpenCell.svelte
    ├── DeleteRowCell.svelte
    ├── ReorderCell.svelte
    └── TcPreCommandCell.svelte
```

---

## 2. 기본 사용법

### 가장 간단한 예제

```svelte
<script lang="ts">
  import { type ColumnDef } from '@tanstack/table-core';
  import { DataTable } from '$lib/components/data-table';

  interface User {
    id: number;
    name: string;
    email: string;
  }

  let data: User[] = [
    { id: 1, name: '김철수', email: 'kim@test.com' },
    { id: 2, name: '이영희', email: 'lee@test.com' }
  ];

  const columns: ColumnDef<User>[] = [
    { accessorKey: 'id', header: 'ID', size: 60 },
    { accessorKey: 'name', header: '이름', enableSorting: true },
    { accessorKey: 'email', header: '이메일' }
  ];
</script>

<DataTable {data} {columns} />
```

이것만으로 **정렬, 페이지네이션, 컬럼 토글**이 모두 동작합니다.

---

## 3. Props 전체 목록

| Prop | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `data` | `TData[]` | **필수** | 행 데이터 배열 |
| `columns` | `ColumnDef<TData>[]` | **필수** | TanStack 컬럼 정의 |
| `filterColumn` | `string` | `''` | 검색할 컬럼 ID |
| `filterPlaceholder` | `string` | `'Search...'` | 검색 입력 placeholder |
| `enableRowSelection` | `boolean` | `false` | 행 선택 체크박스 |
| `enableMultiRowSelection` | `boolean` | `true` | 다중 선택 허용 |
| `enableColumnVisibility` | `boolean` | `true` | 컬럼 표시/숨기기 토글 |
| `actions` | `ActionButton[]` | `[]` | 툴바 액션 버튼 |
| `showPagination` | `boolean` | `true` | 페이지네이션 표시 |
| `compact` | `boolean` | `false` | 컴팩트 레이아웃 |
| `getRowId` | `(row) => string` | `row.id` | 행 고유 ID |
| `onSelectionChange` | `(rows) => void` | - | 선택 변경 콜백 |
| `groupByOptions` | `GroupByOption[]` | `[]` | 그룹핑 옵션 |
| `serverSide` | `ServerSideConfig` | - | 서버 사이드 페이지네이션 |
| `initialSorting` | `SortingState` | `[]` | 초기 정렬 |
| `initialPageSize` | `number` | `20` | 초기 페이지 크기 |
| `expandableRowContent` | `Snippet` | - | 확장 행 콘텐츠 |
| `onRowExpand` | `(row) => void` | - | 행 확장 콜백 |
| `canExpandRow` | `(row) => boolean` | - | 확장 가능 여부 |
| `canDragRow` | `(row) => boolean` | - | 드래그 가능 여부 |
| `onRowDrop` | `(from, to) => void` | - | 드래그 드롭 콜백 |
| `onRowDoubleClick` | `(row) => void` | - | 더블클릭 콜백 |
| `scrollHeight` | `string` | - | 고정 높이 (가상 스크롤) |
| `enableCellCopy` | `boolean` | `false` | 셀 클릭 복사 |
| `selectedRowIds` | `Set<string>` | - | 외부 선택 동기화 |

---

## 4. 셀 컴포넌트 라이브러리

### 사용 방법

```typescript
import { renderComponent } from '$lib/components/ui/data-table/render-helpers.js';
import { ResultCell, DateCell } from '$lib/components/data-table';

const columns: ColumnDef<MyData>[] = [
  {
    accessorKey: 'result',
    header: '결과',
    cell: ({ row }) => renderComponent(ResultCell, { result: row.original.result })
  }
];
```

### 셀 컴포넌트 목록

| 컴포넌트 | 용도 | 주요 Props |
|----------|------|-----------|
| **StatusCell** | 상태 뱃지 (색상 아이콘) | `status` |
| **ResultCell** | 테스트 결과 (Pass/Fail/Running 등) | `result` |
| **DateCell** | 날짜 포맷팅 (ko-KR) | `date`, `format?` (`'date'`/`'datetime'`/`'time'`) |
| **SelectCell** | 선택 체크박스 | `mode` (`'all'`/`'row'`) |
| **BookmarkCell** | 즐겨찾기 토글 | `bookmarked`, `onToggle` |
| **LogPathCell** | 로그 파일 새 창 열기 | `logPath` |
| **LogBrowseCell** | 로그 브라우저 버튼 | `tcState`, `logPath`, `onBrowse` |
| **MetadataBrowseCell** | 메타데이터 브라우저 버튼 | `tcState`, `logPath`, `onBrowse` |
| **ViewResultCell** | 결과 보기 버튼 | `hasResult`, `onView` |
| **CompareToggleCell** | 비교 추가/제거 | `selected`, `onToggle`, `disabled?` |
| **CompareOpenCell** | 비교 화면 열기 | `disabled?`, `onOpen` |
| **DeleteRowCell** | 행 삭제 버튼 | `visible`, `disabled?`, `onDelete` |
| **ReorderCell** | ↑↓ 이동 버튼 | `visible`, `disabled`, `isFirst`, `isLast`, `onMoveUp`, `onMoveDown` |
| **TcPreCommandCell** | TC Pre-Command 드롭다운 | `source`, `slotIndex`, `tcId`, `tcState`, `onAssignmentChanged?` |

---

## 5. 주요 기능별 사용법

### 검색 필터

```svelte
<DataTable
  {data}
  {columns}
  filterColumn="name"
  filterPlaceholder="이름으로 검색..."
/>
```

### 행 선택 + 액션 버튼

```svelte
<script>
  import PlusIcon from '@lucide/svelte/icons/plus';
  import TrashIcon from '@lucide/svelte/icons/trash-2';

  let selected = $state([]);

  const actions = [
    { label: 'Add', icon: PlusIcon, variant: 'default', onclick: handleAdd },
    { label: 'Delete', icon: TrashIcon, variant: 'destructive', requiresSelection: true, onclick: handleDelete }
  ];
</script>

<DataTable
  {data}
  {columns}
  enableRowSelection={true}
  {actions}
  onSelectionChange={(rows) => selected = rows}
/>
```

### 확장 가능한 행

```svelte
<DataTable
  {data}
  {columns}
  canExpandRow={(row) => row.hasDetail}
  onRowExpand={(row) => loadDetail(row.id)}
>
  {#snippet expandableRowContent({ row })}
    <div class="p-3 text-xs">
      {row.detail ?? '로딩 중...'}
    </div>
  {/snippet}
</DataTable>
```

### 가상 스크롤 (대량 데이터)

```svelte
<!-- 500+ 행에서 자동으로 가상 스크롤 활성화 -->
<DataTable
  data={largeArray}
  {columns}
  scrollHeight="500px"
  showPagination={false}
  enableCellCopy={true}
/>
```

### 서버 사이드 페이지네이션

```svelte
<script>
  let totalItems = $state(0);
  let currentPage = $state(0);
  let pageSize = $state(20);

  async function loadPage(page, size) {
    const result = await fetchData(page, size);
    data = result.content;
    totalItems = result.totalElements;
    currentPage = page;
    pageSize = size;
  }
</script>

<DataTable
  {data}
  {columns}
  serverSide={{
    totalItems,
    currentPage,
    pageSize,
    onPageChange: loadPage
  }}
/>
```

### 셀 복사 (Excel 호환)

```svelte
<DataTable
  {data}
  {columns}
  enableCellCopy={true}
/>
```

- **셀 클릭**: 해당 셀 값 복사
- **드래그 선택**: 범위 선택 → Ctrl+C → TSV로 복사
- **Ctrl+A**: 전체 테이블 복사 (Excel에 바로 붙여넣기)

### 그룹핑

```svelte
<DataTable
  {data}
  {columns}
  groupByOptions={[
    { key: 'category', label: '카테고리' },
    { key: 'status', label: '상태', getValue: (row) => row.active ? 'Active' : 'Inactive' }
  ]}
/>
```

---

## 6. 새 셀 컴포넌트 추가하기

### Step 1: 컴포넌트 파일 생성

`frontend/src/lib/components/data-table/cells/MyCustomCell.svelte`:

```svelte
<script lang="ts">
  interface Props {
    value: string;
    onClick?: () => void;
  }

  let { value, onClick }: Props = $props();
</script>

<button
  class="text-xs text-blue-600 hover:underline"
  onclick={(e) => { e.stopPropagation(); onClick?.(); }}
>
  {value}
</button>
```

:::tip
셀 내부의 버튼에는 항상 `e.stopPropagation()`을 추가하세요. 그렇지 않으면 행 선택/확장 이벤트가 같이 발생합니다.
:::

### Step 2: index.ts에 등록

`cells/index.ts`:
```typescript
export { default as MyCustomCell } from './MyCustomCell.svelte';
```

### Step 3: 컬럼에서 사용

```typescript
import { renderComponent } from '$lib/components/ui/data-table/render-helpers.js';
import { MyCustomCell } from '$lib/components/data-table';

const columns = [
  {
    accessorKey: 'link',
    header: 'Link',
    cell: ({ row }) => renderComponent(MyCustomCell, {
      value: row.original.linkText,
      onClick: () => window.open(row.original.url)
    })
  }
];
```

### 체크리스트

- [ ] `interface Props` 정의 (Svelte 5 `$props()`)
- [ ] 버튼/인터랙션에 `e.stopPropagation()` 추가
- [ ] `cells/index.ts`에 export 추가
- [ ] 필요하면 컴팩트 모드 대응 (`text-[10px]` 등)

---

## 7. 실전 패턴

### 조건부 셀 렌더링

```typescript
cell: ({ row }) => {
  if (!row.original.hasAccess) return '—';
  return renderComponent(ResultCell, { result: row.original.result });
}
```

### 동적 컬럼 (조건부 컬럼 추가)

```typescript
const columns = $derived.by(() => {
  const base = [
    { accessorKey: 'name', header: '이름' },
    { accessorKey: 'value', header: '값' }
  ];
  // 성능 탭에서만 비교 컬럼 추가
  if (!isCompatTab) {
    base.push({
      id: 'compare',
      header: 'VS',
      cell: ({ row }) => renderComponent(CompareOpenCell, { onOpen: () => compare(row) })
    });
  }
  return base;
});
```

### 액션 셀 패턴 (Edit/Delete/기타)

별도 ActionCell 컴포넌트를 만드는 것이 깔끔합니다:

```svelte
<!-- MyActionCell.svelte -->
<script lang="ts">
  import PencilIcon from '@lucide/svelte/icons/pencil';
  import TrashIcon from '@lucide/svelte/icons/trash-2';

  interface Props {
    onEdit: () => void;
    onDelete: () => void;
  }

  let { onEdit, onDelete }: Props = $props();
</script>

<div class="flex gap-1">
  <button class="p-1 rounded hover:bg-muted" onclick={(e) => { e.stopPropagation(); onEdit(); }}>
    <PencilIcon class="size-3" />
  </button>
  <button class="p-1 rounded hover:bg-destructive/10 text-destructive" onclick={(e) => { e.stopPropagation(); onDelete(); }}>
    <TrashIcon class="size-3" />
  </button>
</div>
```

### 외부 선택 상태 동기화

```svelte
<script>
  let externalIds = $state(new Set(['row-1', 'row-3']));
</script>

<DataTable
  {data}
  {columns}
  enableRowSelection={true}
  selectedRowIds={externalIds}
  onSelectionChange={(rows) => {
    externalIds = new Set(rows.map(r => String(r.id)));
  }}
/>
```

---

## 8. 성능 가이드

| 데이터 규모 | 권장 설정 |
|-------------|-----------|
| < 100행 | 기본 설정 그대로 |
| 100~500행 | `showPagination={true}`, `initialPageSize={50}` |
| 500~5000행 | `scrollHeight="500px"` (가상 스크롤 자동) |
| 5000행 이상 | `serverSide` 사용 (서버 페이지네이션) |
| 10000행 이상 | `scrollHeight` + 서버 사이드 필수 |

:::caution
가상 스크롤 + 대량 데이터(500행 이상)에서는 윈도잉이 적용됩니다. 이 경우 정렬/필터는 현재 보이는 윈도우 내에서만 동작합니다. 전체 데이터 정렬이 필요하면 서버 사이드를 사용하세요.
:::

---

## 9. FAQ

**Q: 컬럼 너비를 고정하고 싶어요**
```typescript
{ accessorKey: 'id', header: 'ID', size: 60 }
```

**Q: 정렬을 비활성화하고 싶어요**
```typescript
{ accessorKey: 'actions', header: '', enableSorting: false }
```

**Q: 초기 정렬을 지정하고 싶어요**
```svelte
<DataTable initialSorting={[{ id: 'createdAt', desc: true }]} />
```

**Q: 행을 더블클릭하면 페이지 이동하고 싶어요**
```svelte
<DataTable onRowDoubleClick={(row) => goto(`/detail/${row.id}`)} />
```

**Q: 특정 행만 선택 가능하게 하고 싶어요**
현재 전체 행 선택/비선택만 지원합니다. 특정 행 비활성화는 `onSelectionChange`에서 필터링하세요.

**Q: 셀 값을 포맷팅하고 싶어요**
```typescript
cell: ({ row }) => `${row.original.value.toFixed(2)}%`
```

**Q: 빈 테이블에 메시지를 보여주고 싶어요**
DataTable은 빈 데이터일 때 자동으로 "No results" 메시지를 표시합니다.
