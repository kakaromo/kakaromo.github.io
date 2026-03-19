---
title: UI 크기 조절
description: resize-ui.sh 스크립트를 사용한 UI 크기 프리셋 변경 및 수동 CSS 조절 가이드
---

Portal 프론트엔드는 의도적으로 컴팩트한 크기로 설계되어 있습니다. `resize-ui.sh` 스크립트로 전체 UI 크기를 일괄 변경하거나, 개별 CSS를 수동으로 조절할 수 있습니다.

## resize-ui.sh 스크립트

```bash
cd frontend

bash resize-ui.sh              # 현재 상태 확인 + 사용법
bash resize-ui.sh status       # 현재 적용된 크기만 확인
bash resize-ui.sh compact      # 가장 작은 크기 (기본값)
bash resize-ui.sh default      # 한 단계 업 (일반 웹 크기)
bash resize-ui.sh large        # 두 단계 업 (넉넉한 크기)
```

변경 후 `npm run dev`로 결과를 확인하세요. 프리셋 간 자유롭게 전환할 수 있습니다.

---

## 3가지 프리셋 비교

| 프리셋 | body 폰트 | 헤더 높이 | 테이블 텍스트 | 테이블 행 높이 | 슬롯 카드 너비 |
|--------|-----------|-----------|--------------|---------------|---------------|
| **compact** | text-sm (14px) | h-10 (40px) | text-[11px] | h-7 (28px) | 170~220px |
| **default** | text-base (16px) | h-12 (48px) | text-xs (12px) | h-8 (32px) | 200~260px |
| **large** | text-lg (18px) | h-14 (56px) | text-sm (14px) | h-9 (36px) | 240~300px |

---

## 수동 조절

스크립트가 변경하는 각 영역을 개별적으로 수정할 수 있습니다.

### 전역 폰트 크기

**파일**: `frontend/src/app.css`

```css
@layer base {
  body {
    @apply bg-background text-foreground text-sm;  /* compact 기본값 */
  }
}
```

`text-sm` → `text-base` → `text-lg` 순서로 크기가 커집니다.

### 고해상도 디스플레이 줌

```css
@media (min-width: 2560px) { html { zoom: 1.15; } }
@media (min-width: 3840px) { html { zoom: 1.35; } }
@media (min-width: 5120px) { html { zoom: 1.6; } }
```

일반 FHD 모니터에서도 확대하려면 `@media (min-width: 1440px) { html { zoom: 1.1; } }` 추가를 고려하세요.

### 컴포넌트별 크기 참조

| 영역 | 파일 | compact | default | large |
|------|------|---------|---------|-------|
| 헤더 높이 | `+layout.svelte` | `h-10` | `h-12` | `h-14` |
| 헤더 텍스트 | `+layout.svelte` | `text-[11px]` | `text-xs` | `text-sm` |
| 헤더 아이콘 | `+layout.svelte` | `size-2.5` | `size-3` | `size-3.5` |
| 테이블 텍스트 | `DataTable.svelte` | `text-[11px]` | `text-xs` | `text-sm` |
| 테이블 행 | `DataTable.svelte` | `h-7` | `h-8` | `h-9` |
| 툴바 텍스트 | `DataTableToolbar.svelte` | `text-[10px]` | `text-xs` | `text-sm` |
| 슬롯 카드 | `SlotCard.svelte` | 170~220px | 200~260px | 240~300px |
| 탭 높이 | `tabs-list.svelte` | `h-8` | `h-9` | `h-10` |
| 탭 텍스트 | `tabs-trigger.svelte` | `text-xs` | `text-sm` | `text-base` |

---

## 영향 범위

프리셋 변경은 다음 요소에 영향을 미칩니다:

| 요소 | 설명 |
|------|------|
| **텍스트** | 전역 폰트, 테이블, 헤더, 툴바, 대시보드 텍스트 크기 |
| **버튼/입력** | Button, Input, Select 높이와 패딩 |
| **패딩** | 카드, 테이블 셀, 네비게이션 링크의 여백 |
| **아이콘** | 네비게이션, 정렬, 툴바 아이콘 크기 |

### Tailwind 크기 참조표

| 클래스 | 픽셀 | 용도 |
|--------|------|------|
| `text-[10px]` | 10px | 가장 작은 텍스트 |
| `text-[11px]` | 11px | 테이블, 대시보드 타일 |
| `text-xs` | 12px | 보조 텍스트, 뱃지 |
| `text-sm` | 14px | 기본 본문 텍스트 |
| `text-base` | 16px | 일반 웹 기본 크기 |
| `text-lg` | 18px | 강조 텍스트 |
| `h-7` | 28px | compact 행 높이 |
| `h-8` | 32px | default 행/탭 높이 |
| `h-9` | 36px | 기본 버튼/인풋 |
| `h-10` | 40px | 헤더 |

:::note
헤더 높이를 변경하면 Slots 페이지(`slots/+page.svelte`)의 `h-[calc(100vh-4rem)]` 값도 함께 수정해야 합니다. `h-10` → `4rem`, `h-12` → `4.5rem`, `h-14` → `5rem`으로 대응합니다.
:::
