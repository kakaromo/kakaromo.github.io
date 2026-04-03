---
title: 새 프론트엔드 페이지 추가하기
description: SvelteKit 라우팅, API 연동, 상태 관리, 재사용 컴포넌트를 활용하여 새 페이지를 추가하는 가이드
---

Portal 프론트엔드에 새로운 페이지를 추가하는 전체 과정입니다.

## 전체 흐름

```
1. SvelteKit 라우트 생성
2. API 함수 추가
3. 페이지 컴포넌트 작성
4. 메뉴 등록
```

---

## Step 1: SvelteKit 라우트 생성

SvelteKit은 파일 시스템 기반 라우팅을 사용합니다.

```
frontend/src/routes/
├── dashboard/+page.svelte          →  /dashboard
├── testdb/performance/+page.svelte →  /testdb/performance
├── agent/+page.svelte              →  /agent
└── new-feature/+page.svelte        →  /new-feature     ← 새 페이지
```

```bash
mkdir -p frontend/src/routes/new-feature
touch frontend/src/routes/new-feature/+page.svelte
```

### 동적 라우트 (파라미터)

```
frontend/src/routes/new-feature/[id]/+page.svelte  →  /new-feature/123
```

```svelte
<script lang="ts">
    import { page } from '$app/state';
    const id = $derived(page.params.id);
</script>
```

---

## Step 2: API 함수 추가

`frontend/src/lib/api/` 디렉토리에 타입과 API 함수를 생성합니다.

```typescript
// frontend/src/lib/api/newFeature.ts
import { get, post, put, del } from './client.js';

// 타입 정의
export interface NewFeatureItem {
    id: number;
    name: string;
    status: string;
    createdAt?: string;
}

// CRUD 함수
export function fetchItems(): Promise<NewFeatureItem[]> {
    return get('/new-feature');
}

export function fetchItem(id: number): Promise<NewFeatureItem> {
    return get(`/new-feature/${id}`);
}

export function createItem(data: Omit<NewFeatureItem, 'id' | 'createdAt'>): Promise<NewFeatureItem> {
    return post('/new-feature', data);
}

export function updateItem(id: number, data: Partial<NewFeatureItem>): Promise<NewFeatureItem> {
    return put(`/new-feature/${id}`, data);
}

export function deleteItem(id: number): Promise<void> {
    return del(`/new-feature/${id}`);
}
```

`client.ts`가 XSRF 토큰과 에러 처리를 자동으로 처리하므로 별도 설정 불필요.

---

## Step 3: 페이지 컴포넌트 작성

### 기본 패턴

```svelte
<!-- frontend/src/routes/new-feature/+page.svelte -->
<script lang="ts">
    import { onMount } from 'svelte';
    import { fetchItems, createItem, deleteItem, type NewFeatureItem } from '$lib/api/newFeature.js';
    import { toast } from 'svelte-sonner';

    let items = $state<NewFeatureItem[]>([]);
    let loading = $state(true);

    // 파생 값
    let activeItems = $derived(items.filter(i => i.status === 'active'));
    let itemCount = $derived(items.length);

    onMount(async () => {
        try {
            items = await fetchItems();
        } catch (e) {
            toast.error('데이터를 불러올 수 없습니다.');
        } finally {
            loading = false;
        }
    });

    async function handleCreate(data: Omit<NewFeatureItem, 'id' | 'createdAt'>) {
        const created = await createItem(data);
        items = [...items, created];
        toast.success('생성되었습니다.');
    }

    async function handleDelete(id: number) {
        await deleteItem(id);
        items = items.filter(i => i.id !== id);
        toast.success('삭제되었습니다.');
    }
</script>

<div class="container mx-auto p-4 space-y-4">
    <h1 class="text-lg font-semibold">New Feature</h1>

    {#if loading}
        <p>로딩 중...</p>
    {:else}
        <p>총 {itemCount}개 항목</p>
        <!-- 컨텐츠 -->
    {/if}
</div>
```

### Svelte 5 Runes 요약

| Rune | 용도 | 예시 |
|------|------|------|
| `$state` | 반응형 상태 | `let count = $state(0)` |
| `$derived` | 파생 값 (computed) | `let double = $derived(count * 2)` |
| `$effect` | 부수효과 (상태 변경 감시) | `$effect(() => { console.log(count) })` |
| `$props` | 컴포넌트 Props | `let { data } = $props()` |

---

## 재사용 컴포넌트

### DataTable

데이터 테이블이 필요한 경우 `DataTable` 컴포넌트를 재사용합니다:

```svelte
<script lang="ts">
    import DataTable from '$lib/components/data-table/DataTable.svelte';
    import type { ColumnDef } from '@tanstack/table-core';

    const columns: ColumnDef<NewFeatureItem>[] = [
        { accessorKey: 'id', header: 'ID' },
        { accessorKey: 'name', header: '이름' },
        { accessorKey: 'status', header: '상태' },
    ];
</script>

<DataTable data={items} {columns} enableCellCopy={true} />
```

### shadcn-svelte 다이얼로그

CRUD 폼에 다이얼로그를 사용하는 패턴:

```svelte
<script lang="ts">
    import * as Dialog from '$lib/components/ui/dialog';
    import { Button } from '$lib/components/ui/button';
    import { Input } from '$lib/components/ui/input';

    let open = $state(false);
    let formData = $state({ name: '', status: 'active' });
</script>

<Button onclick={() => open = true}>추가</Button>

<Dialog.Root bind:open>
    <Dialog.Content>
        <Dialog.Header>
            <Dialog.Title>새 항목 추가</Dialog.Title>
        </Dialog.Header>
        <div class="space-y-4">
            <Input bind:value={formData.name} placeholder="이름" />
        </div>
        <Dialog.Footer>
            <Button variant="outline" onclick={() => open = false}>취소</Button>
            <Button onclick={() => { handleCreate(formData); open = false; }}>저장</Button>
        </Dialog.Footer>
    </Dialog.Content>
</Dialog.Root>
```

### PerfChart (ECharts)

차트가 필요한 경우:

```svelte
<script lang="ts">
    import PerfChart from '$lib/components/perf-chart/PerfChart.svelte';

    let chartOption = $derived({
        xAxis: { type: 'category', data: items.map(i => i.name) },
        yAxis: { type: 'value' },
        series: [{ type: 'bar', data: items.map(i => i.value) }],
        dataZoom: [{ type: 'inside' }]  // 마우스 휠 줌
    });
</script>

<PerfChart option={chartOption} height="400px" />
```

---

## Step 4: 메뉴 등록

### 사이드바 메뉴에 추가

`+layout.svelte`의 `menuItems` 배열에 새 항목을 추가합니다:

```typescript
// frontend/src/routes/+layout.svelte
const menuItems = [
    { id: 'dashboard', title: 'Dashboard', href: '/dashboard', icon: HomeIcon },
    // ... 기존 메뉴
    { id: 'new-feature', title: 'New Feature', href: '/new-feature', icon: StarIcon },  // ← 추가
];
```

### 메뉴 가시성 제어

Admin 페이지에서 역할별 메뉴 가시성을 제어할 수 있습니다. `menuStore`가 Admin API에서 설정을 로드하여, 해당 역할에 허용된 메뉴만 표시합니다.

```svelte
<!-- +layout.svelte 내부 -->
const visibleMenuItems = $derived(
    navMenuItems.filter(item => menuStore.isVisible(item.id))
);
```

새 메뉴 ID를 DB의 `portal_admin_menus` 테이블에도 등록해야 Admin에서 가시성을 제어할 수 있습니다.

---

## 상태 관리 패턴

### 언제 어떤 패턴을 사용할지

| 패턴 | 용도 | 예시 |
|------|------|------|
| **로컬 `$state`** | 해당 컴포넌트에서만 사용하는 상태 | 폼 입력, 다이얼로그 열기/닫기 |
| **Module-level store** | 여러 컴포넌트에서 공유하는 글로벌 상태 | `auth.svelte.ts`, `menuStore.svelte.ts` |
| **Factory store** | 인스턴스별로 독립적인 상태가 필요할 때 | `createHeadSlotStore(source)` |

### Module-level store 패턴

```typescript
// frontend/src/lib/stores/newFeature.svelte.ts
let items = $state<NewFeatureItem[]>([]);
let loading = $state(false);

export const newFeatureStore = {
    get items() { return items; },
    get loading() { return loading; },

    async load() {
        loading = true;
        items = await fetchItems();
        loading = false;
    }
};
```

---

## 체크리스트

- [ ] `routes/new-feature/+page.svelte` 생성
- [ ] `lib/api/newFeature.ts`에 타입 + API 함수
- [ ] Svelte 5 runes 사용 (`$state`, `$derived`, `$effect`)
- [ ] 에러 처리 (`try/catch` + toast)
- [ ] `+layout.svelte`의 `menuItems`에 추가
- [ ] DB에 메뉴 항목 등록 (Admin 가시성 제어용)
