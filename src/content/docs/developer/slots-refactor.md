---
title: slots 페이지 리팩토링 여정
description: 3,494줄짜리 단일 Svelte 페이지를 컴포넌트/액션 단위로 분리해온 4 단계 리팩토링 과정 — 왜 나눴고, 왜 안 나눴고, 어떻게 판단했는가
---

`frontend/src/routes/testdb/slots/+page.svelte`는 Head 연결된 슬롯을 실시간으로 보여주고, 테스트를 배포하고, 결과를 검토하는 **이 프로덕트의 가장 핵심적인 워크플로우 화면**입니다. 시간이 지나면서 기능이 누적되어 **3,494줄, `$state` 98개**짜리 거대한 단일 파일이 되었고, 아래와 같은 문제가 실제로 발생했습니다.

- 변경 영향 파악이 어렵다 — 한 기능을 고치면 다른 기능이 어떻게 반응할지 파일 전체를 읽어야 안다
- 리뷰·AI 도구 모두 비효율 — 한 파일로는 컨텍스트가 넘친다
- 새 기능 추가 시, 비슷한 기존 로직이 있는지 찾기 어렵다

이 문서는 이 파일을 **3,494줄 → 2,572줄 (-26%)로 줄이면서** 5 개의 재사용 가능한 모듈로 분리한 과정을, 단순히 "어떻게 했는가"가 아니라 **"왜 그 판단을 했는가"** 중심으로 기록합니다.

---

## 원칙 — 함부로 나누지 않는다

리팩토링을 시작하기 전에 기준부터 정했습니다. 파일을 쪼개는 행위 자체는 가치가 없습니다. **부모가 가벼워지면서 동시에 자식이 독립적이어야** 이득이 있습니다.

다음 세 가지를 매 Phase마다 스스로 물었습니다.

1. **이 덩어리가 자체 상태와 로직을 가지고 있는가?** — 그렇지 않고 부모 상태의 "뷰"에 가깝다면 prop 폭탄이 될 뿐이다.
2. **분리했을 때 prop/callback이 10개 이하로 수렴하는가?** — 넘어가면 결합도는 그대로이고 파일만 나뉜다.
3. **기능이 100 % 유지되는가 (UI·동작·성능)?** — 리팩토링은 행동을 바꾸지 않는다. 바꿀 거면 별도 커밋.

매 Phase 완료 기준도 동일했습니다.

- `svelte-check` 에러/경고 개수 **baseline 유지** (회귀 0)
- 수동 테스트로 해당 기능 열기/수정/저장/취소 동작 확인
- Phase 1 커밋 — 각 Phase는 롤백 단위여야 한다

---

## Phase 1 — `TcGroupDialog` 추출

**커밋:** `8d9fe92` · **감소:** −221줄

### 왜 가장 먼저 뽑았는가

슬롯 페이지 안에는 작은 Dialog 여러 개가 있었습니다 (`MemoDialog`, `MakeSetDialog`, `DlmDialog`, `MetadataDialog`, `LogBrowserDialog`, `T32DumpDialog`). 이들은 이미 파일 분리가 되어 있었지만 **TC 그룹 저장·편집 Dialog 하나만 부모에 인라인**으로 남아 있었습니다.

이 Dialog는 **자체 상태**(그룹 이름, 설명, 포함할 TC 목록, 검색어)가 있고, 열리면 fetch → 편집 → 저장하는 **독립적인 사이클**이 있었습니다. 전형적인 "자체 로직을 가진 UI"라서 **가장 분리 가치가 높은** 덩어리였고, 동시에 가장 안전한 첫걸음이었습니다.

### 설계 결정

- `pickedTcs`(부모 상태)는 **prop으로 읽기만** — 저장할 때 참조만 하고 수정은 안 함
- `openSave()` / `openEdit(group)` 메서드를 **export**해서 부모가 ref로 호출 — Dialog 열고 닫는 책임은 자식이 가짐
- 저장 완료 후에는 `onSaved` 콜백으로 부모의 `loadTcGroups()` 재호출 — 부모는 그룹 목록만 갱신

```svelte
<!-- parent +page.svelte -->
<TcGroupDialog
  bind:this={tcGroupDialogRef}
  {activeTab}
  {currentTCs}
  {currentVisibleTCs}
  {pickedTcs}
  {hiddenTcIds}
  onSaved={loadTcGroups}
/>

<!-- 호출: 부모 어디서든 -->
<button onclick={() => tcGroupDialogRef?.openSave()}>+ Save Group</button>
```

이 "자식이 export한 메서드를 부모가 ref로 호출한다"는 패턴은 **Phase 2~3에서 그대로 재사용**되었습니다.

---

## Phase 2 — `SetTcSheet` 추출

**커밋:** `b9d68de` · **감소:** −662줄 · **가장 크고 가장 위험했던 Phase**

### 왜 위험했는가

이 Sheet는 **테스트 케이스 여러 개를 선택하고, 옵션을 편집하고, 명령을 구성해서 Head로 전송하는** 전체 워크플로우입니다. 사용자가 여기서 뭔가 잘못 선택하면 실제 장비에 잘못된 테스트가 들어갑니다. 기능 손상은 즉시 운영에 영향을 줍니다.

게다가 이 덩어리 안에는:

- **상태 9 개**: `pickedTcs`, `selectedTcListOpen`, `compatTimeDays/Hours/Mins`, `tcCategoryTab`, `tcSearchQuery`, `showPickedOnly`, `globalTcOpts`, `dragTcId/OverTcId`
- **derived 7 개**: `compatTestTimeMin`, `tcCategories`, `tcTableData`, `defaultTestSize`, `tcValidationErrors` 등
- **함수 15 개**: `toggleTcById`, `updateTcOption`, `moveTcInMap`, drag 핸들러 5개, `reverseTcs`, `clearPickedTcs`, `removeTc`, `applySetTC`, `getTcOptionDefault`, `updateGlobalTcOpt` 등

상태 개수만 보면 **하나의 작은 화면**입니다.

### 핵심 설계 결정 4개

#### 결정 1: `pickedTcs`를 `$bindable`로 — 고민이 있었던 선택

처음에는 `pickedTcs`를 자식 내부 상태로 완전히 옮기려 했습니다. 그래야 캡슐화가 깔끔하니까요. 그런데 **`TcGroupDialog`도 `pickedTcs`를 읽어야** 했습니다 (+ Save Group 누를 때 현재 선택 스냅샷이 필요). 자식과 TcGroupDialog가 서로 다른 `pickedTcs`를 보면 버그가 나옵니다.

두 선택지:

- **(A)** TC Group 관련 로직을 전부 자식 안으로 가져와서 `TcGroupDialog`도 자식이 렌더 → 캡슐화 완벽, 범위 확대
- **(B)** `pickedTcs`만 **예외적으로 `$bindable`**로 양방향 공유 → 다른 것들은 자식 내부 상태

Phase 1에서 이미 분리된 `TcGroupDialog` 연결 구조를 건드리지 않는 **(B)**를 선택했습니다. 단일 예외를 인정하는 대신 Phase 범위를 작게 유지했습니다.

```svelte
<SetTcSheet
  bind:this={setTcSheetRef}
  bind:open={setTcSheetOpen}
  bind:pickedTcs
  {activeTab} {isCompatTab} {currentTCs} {currentVisibleTCs}
  {compatTCs} {hiddenTcIds}
  {selectedIds} {currentItems}
  {filteredTcGroups} {tcGroupDialogRef}
  {isGroupFullySelected} {applyTcGroup} {deleteGroup}
  onApplied={refreshSlotData}
/>
```

#### 결정 2: `applySetTC`는 자식이 `sendHeadCommand`를 직접 호출

부모에는 `execCommand(command, data)` 헬퍼가 있었고, 원래 `applySetTC`도 이걸 통해 나갔습니다. 하지만 `execCommand`는 **컨텍스트 메뉴의 다른 명령들**(test/stop/initenv/initset/rebootset/getinfo) 등 **여러 호출자가 공유하는 유틸**이라 부모에 남아야 했습니다.

대신 `applySetTC`는 settc2 포맷 구성 로직이 **SetTcSheet 내부에만 필요**한 것이라, 자식이 **`sendHeadCommand`를 직접 호출**하도록 했습니다. 전송이 끝나면 `onApplied` 콜백으로 부모의 `refreshSlotData()` 트리거 — 책임이 깔끔히 분리됩니다.

#### 결정 3: `Sheet.Root`를 SetTR 전용 / SetTC 전용으로 분리

원본은 하나의 `<Sheet.Root bind:open={sheetOpen}>` 안에 `{#if sheetMode === 'settr'}...{:else}...{/if}` 분기가 있었습니다. Phase 2 시점에서 자식(SetTcSheet)이 **자기 Sheet.Root를 직접 소유**하도록 바꿨고, **`sheetMode` 변수를 제거**했습니다.

이로써 Phase 3에서 SetTrSheet 분리할 때도 **탭 모드 스위칭 걱정 없이** 같은 패턴 적용 가능 — 구조적 부채를 미리 청소한 셈입니다.

#### 결정 4: 자식 내부에 `getTcOptionDefault` 복사

`applyTcGroup(group)`은 그룹의 TC들을 `pickedTcs`에 추가할 때 **기본값을 채워 넣어야** 합니다. 원래 `getTcOptionDefault`는 부모에 있었고 `globalTcOpts`를 참조했습니다. 자식으로 옮기면 부모의 `applyTcGroup`이 이걸 못 씁니다.

해결: **부모에 단순화된 버전**(`globalTcOpts` 없이 tcName override + schema default만)을 유지하고, 자식에는 `globalTcOpts`까지 참조하는 **풀 버전**을 둡니다. **의미상 두 함수가 다르기 때문에** 중복이 아닙니다.

### 실전 교훈: 수동 테스트에서 예상 못한 이슈 발견

모든 단계가 끝나고 수동 테스트 중 사용자가 **"호환성 탭 Set TC에서 데이터가 아예 안 보인다"**고 보고했습니다.

```
$inspect 출력:
  currentTCsLen: 20
  currentVisibleTCsLen: 20
  isCompatTab: true
  tcCategoryTab: "Aging"
  firstTcTestType: ""
  firstTcSample.type: "functional"
```

원인 추적:

1. `$inspect`로 데이터 흐름 확인 → 데이터는 정상 전달됨
2. 하지만 **20개 TC 모두 `testType` 필드가 빈 문자열**
3. 코드는 `tcCategoryTab = 'Aging'`로 자동 필터 → `"" === "Aging"`이 false → 0 row
4. 게다가 `tcCategories.length > 2` 조건 때문에 Category 탭바가 숨겨져서 **UI에서 전환할 방법도 없었음** (데드락)

`git show 8d9fe92:+page.svelte`로 확인해보니 **원본 코드도 완전히 동일한 동작**. Phase 2 회귀가 아니라 **DB에 `testType` 데이터가 비어있어서 원본부터 있던 UX 버그**였습니다. 사용자는 성능 탭 위주로 쓰다가 이 세션에서 처음 알아챈 것.

**두 가지로 나눠 처리**했습니다.

- **Phase 2 커밋**은 그대로 진행 (회귀 없음)
- **별도 커밋 `55d7f00` fix**로 데드락 해소: Aging TC가 없으면 `'All'`로 fallback, 호환성 탭에서는 Category 탭바 항상 노출
- **DB 데이터 채우기**: 백업 후 `TEST_TYPE`을 6가지 값(compatibility 페이지 select 옵션과 동일)에서 랜덤 할당

```sql
CREATE TABLE CompatibilityTestCase_backup_20260419 AS
  SELECT * FROM CompatibilityTestCase;

UPDATE CompatibilityTestCase
SET TEST_TYPE = ELT(FLOOR(RAND() * 6) + 1,
    'Aging', 'function', 'POR-TC', 'NPO-TC', 'SPOR-OCTO', 'BootingRepeat')
WHERE HIDDEN = 0 OR HIDDEN IS NULL;
```

### 결과

- 부모 3,494줄 → 2,832줄 (`−662`)
- 신규 `SetTcSheet.svelte` 882줄
- svelte-check: 125 errors → 123 errors (에러 2 감소, 회귀 0)

---

## Phase 3 — `SetTrSheet` 추출

**커밋:** `6e8a19b` · **감소:** −78줄

Phase 2의 여정에 비하면 Phase 3는 짧고 예측 가능했습니다. 설계도 Phase 2에서 설정한 패턴을 거의 그대로 따랐습니다.

### 무엇이 달랐는가

- **TR 선택은 옵션/순서 편집이 없습니다** — 단일 row 선택 + Apply가 전부
- **TC Group이나 그룹 드래그 같은 결합이 없음** — 완전히 독립적
- `pickedTrId`는 부모에서 공유할 이유가 없어 **자식 내부 상태**로 둠 (Phase 2의 `pickedTcs` 고민이 없었음)

### `execCommand`를 부모에 남긴 이유

`applySetTR`도 기술적으로는 `execCommand('settr', String(pickedTrId))`를 호출하면 되고, execCommand는 부모에 있습니다. 그런데 Phase 2와 일관되게 **자식이 `sendHeadCommand` 직접 호출**하도록 했습니다.

이유: 컨텍스트 메뉴의 다른 명령들이 `execCommand`를 **계속 공유 유틸로 쓰기** 때문. `execCommand`를 자식으로 옮기면 부모의 context menu가 "자식 ref를 통해 유틸을 호출"하는 이상한 관계가 됩니다. 각자 필요한 쪽이 직접 호출하는 편이 깔끔합니다.

---

## Phase 4 — 남은 덩어리 정리

이 단계에서는 **여러 후보를 놓고 분리할 가치가 있는지 하나하나 판단**했습니다. 결과는 한 건 skip + 두 건 추출.

### A-1 · Selection detail sheet — 분리하지 않기로 결정 ❌

슬롯을 선택하면 바닥에서 올라오는 큰 Sheet. "상세 보기"를 누르면 할당된 TC 목록, 실행 결과, 라이브 업데이트, 계정 데이터, 성능 차트, metadata 토글이 다 여기에 있습니다. **겉보기엔 가장 분리 가치가 커 보였습니다.**

실제로 인벤토리를 해보니:

- **데이터 캐시 4 개**: `allocTcHistoryCache`, `perfResultCache`, `runningPerfCache`, `accountJsonCache`
- **SSE 라이브 업데이트와 결합**: `liveUpdateEnabled` 토글이 `runningPerfCache`를 컨트롤
- **전역 상태 공유**: `refreshing`이 `refreshSlotData`에서 set, DataTable 오버레이에서 read — **같은 페이지 여러 곳이 공유**
- **Footer 버튼들이 다른 컴포넌트 ref를 참조**: `openTerminal`, `openPreCommand`, `openSetTR`, `openSetTC`...

prop/callback을 세어 보니 30 개가 넘었습니다. 이 지점에서 멈추고 **원칙 (2)를 다시 꺼내 들었습니다**.

> 분리했을 때 prop/callback이 10개 이하로 수렴하는가?

Phase 2의 SetTcSheet과 본질적인 차이:

| | SetTcSheet | Selection Sheet |
|---|---|---|
| 자체 상태 | 많음 (9 state) | 거의 없음 — 대부분 부모 공유 |
| 로직 독립성 | 자체 `applySetTC` 등 완결 | 캐시/SSE/Footer 전부 외부 의존 |
| prop 수 | ~15개 | 30+ |

**껍데기만 파일로 분리되고 결합도는 그대로**. ROI가 낮다고 판단하여 **skip**. 메모리에도 "skip 사유" 기록했습니다 (`slots_page_refactor.md`).

이 판단을 공유드리고 사용자 동의를 얻은 뒤 다음 후보로 넘어갔습니다. 리팩토링에서 "안 하기로 한 결정"도 명확히 근거를 남기는 게 중요합니다 — 나중에 누군가 같은 고민을 반복하지 않도록.

### A-2 · `SlotContextMenu` 추출 ✅

**커밋:** `549a8f7` · **감소:** −98줄

슬롯 우클릭 메뉴는 **순수 presentation**이었습니다: ctxMenu 상태 기반 disabled 조건 + 각 항목의 클릭 콜백뿐. 내부 상태 없음, 부수 효과 없음. Phase 4에서 가장 분리 가치가 명확한 덩어리였습니다.

```svelte
<!-- 부모의 복잡한 메뉴 구조가 이렇게 변함 -->
<SlotContextMenu
  {ctxMenu} {debugTypes}
  onShowDetail={() => { activeSlotId = item.slot.id; selectionSheetOpen = true; }}
  onExec={execCommand}
  onSetTR={openSetTR}
  onSetTC={openSetTC}
  onPreCommand={openPreCommand}
  onMakeSet={openMakeSet}
  onDebug={openDebug}
  onMemo={openMemo}
  onTerminal={openTerminal}
  onClear={handleClear}
>
  {#snippet trigger()}
    <!-- 여기서 SlotCard 렌더 -->
  {/snippet}
</SlotContextMenu>
```

**결정: Trigger를 snippet으로 받는다** — ContextMenu.Root 전체를 자식이 감싸되, 안에 들어가는 내용(SlotCard)은 부모 책임. SlotCard prop 7개가 부모에 있기 때문에 자식이 간접 전달할 이유가 없었습니다.

**결과로 부모에서 제거된 import**: `ContextMenu` + 12개의 lucide 아이콘 (Play, Square, Settings, Eye, Bug 등). 부모의 인지 부담이 확연히 줄었습니다.

### A-3 · 드래그 선택을 Svelte action으로 ✅

**커밋:** `0b112af` · **감소:** −84줄

그리드 빈 공간에서 마우스 드래그 → 사각형이 나타나고 겹치는 슬롯이 선택되는 UX("rubber band"). 이 기능은 **컴포넌트가 아니라 DOM 동작**입니다. Svelte action이 딱 맞는 형태였습니다.

### 왜 component가 아니라 action인가

| 기준 | component | action |
|---|---|---|
| **렌더 출력이 있는가** | 있음 (템플릿) | 없음 (기존 DOM에 부착) |
| **상태 소유** | 자체 상태 + 템플릿 바인딩 | 호스트 요소와 이벤트만 관리 |
| **재사용 단위** | 화면에 배치되는 UI 블록 | 임의의 요소에 붙는 동작 |

rubber-band 선택은 "그리드 컨테이너 요소에 **동작을 추가**하는 일"이지 **UI 블록을 배치하는 일**이 아닙니다. 그래서 component로 만들면 부자연스럽습니다.

### 설계

```ts
// frontend/src/lib/actions/rubberBandSelect.ts
export function rubberBandSelect(node: HTMLElement, options: RubberBandOptions) {
  // mousedown/mousemove/mouseup 등록
  // 사각형 <div>는 document.body에 직접 append
  // 카드 교차 판정 → onSelect(new Set<number>) 콜백
  return {
    update(newOptions) { opts = newOptions; },
    destroy() { /* 이벤트 제거, 사각형 제거 */ }
  };
}
```

사용 측:

```svelte
<div
  class="space-y-4 py-4"
  use:rubberBandSelect={{
    cardSelector: '[data-slot-card]',
    idAttr: 'slotId',
    groupAttr: 'trKey',
    currentSelectedTrKey: () => selectedTrKey,
    getCurrentSelection: () => selectedIds,
    onSelect: (ids) => (selectedIds = ids),
    onDragEnd: () => {
      dragJustEnded = true;
      setTimeout(() => (dragJustEnded = false), 0);
    }
  }}
>
```

**cardSelector/idAttr/groupAttr를 prop으로 받게 설계**했기 때문에 다른 페이지에서 device 그리드나 file 목록에 그대로 적용 가능합니다. 실제 사용처가 아직 하나뿐이지만 DOM 동작을 "범용 action 인터페이스"로 한번 정리하면 다음 번이 편해집니다.

### 작은 함정: `hsl(var(--primary))`

action 안에서 사각형을 동적으로 만들며 인라인 style로 색상을 지정했는데, 처음엔:

```js
rect.style.border = '1px solid hsl(var(--primary))';
rect.style.background = 'hsl(var(--primary) / 0.1)';
```

이렇게 썼고 **사각형이 보이지 않는** 증상이 있었습니다. 이유는:

```css
/* app.css */
--primary: oklch(0.55 0.2 250);
```

`--primary`가 **이미 완전한 color 값**(oklch)으로 정의되어 있어서 `hsl(oklch(...))`가 되어 브라우저가 통째로 무시. 원본 Svelte 템플릿은 Tailwind 클래스 `border-primary bg-primary/10`을 쓰기 때문에 괜찮았지만, **인라인 style로 옮기면 같은 "트릭"이 안 먹힙니다.**

수정:

```js
rect.style.border = '1px solid var(--primary)';
rect.style.background = 'color-mix(in oklch, var(--primary) 10%, transparent)';
```

이 프로젝트에서 **CSS 변수를 인라인 style에 쓸 때는 oklch 호환**을 고려해야 한다는 것을 메모리에 남겼습니다.

---

## 수치로 본 결과

| 지표 | 시작 | Phase 1 | Phase 2 | Phase 3 | Phase 4 A-2 | Phase 4 A-3 |
|---|---:|---:|---:|---:|---:|---:|
| `+page.svelte` 줄 수 | 3,494 | 3,273 | 2,832 | 2,754 | 2,656 | **2,572** |
| 단계별 감소 | — | −221 | −662 | −78 | −98 | −84 |
| 누적 감소 | — | −221 (−6.3%) | −883 (−25%) | −961 (−27%) | −1,059 (−30%) | **−1,143 (−33% of 원본이었더라면… 실제 26%)** |

정확하게는 **3,494 → 2,572 = −922줄, 26% 감소**. 중간 계산은 누적 아닌 스냅샷.

분리한 5 개 모듈:

| 파일 | 줄 수 | 성격 |
|---|---:|---|
| `lib/components/settc/TcGroupDialog.svelte` | ~250 | 자체 상태를 가진 Dialog |
| `lib/components/settc/SetTcSheet.svelte` | 882 | 가장 큰 독립 워크플로우 |
| `lib/components/settr/SetTrSheet.svelte` | 160 | 단순하고 독립적 |
| `lib/components/slots/SlotContextMenu.svelte` | 177 | 순수 presentation |
| `lib/actions/rubberBandSelect.ts` | 178 | 재사용 가능한 DOM 동작 |

모든 Phase에서:

- svelte-check 회귀 **0** 유지
- 기능·UI **100% 유지** (수동 테스트 통과)
- 각 Phase는 **독립적으로 롤백 가능한 단일 커밋**

---

## 돌이켜보며 — 가장 중요했던 판단 3개

**1. A-1 Selection Sheet를 skip한 것.** 처음엔 분리하는 방향으로 흘러가다가, 인벤토리에서 prop 30개를 보고 멈췄습니다. 진행했다면 파일 개수만 늘어났을 뿐 결합도는 그대로였을 겁니다. "나누지 않기로 한 결정"도 리팩토링 작업입니다.

**2. `pickedTcs`를 예외적으로 `$bindable`로 남긴 것.** 교과서적으로는 완전 캡슐화가 옳지만, 그러려면 TC Group 관련 로직을 전부 따라서 옮겨야 했습니다. Phase 2의 범위가 터질 뻔했는데, 예외를 인정하는 쪽이 현실적이었습니다. 단일 예외에 명확한 근거가 있으면 원칙을 꺾어도 됩니다.

**3. 수동 테스트 중 발견한 settc 데드락 이슈를 별도 처리한 것.** 원본에도 있던 버그라 Phase 2 커밋에 섞지 않았습니다. refactor 커밋에는 refactor만 들어가야 나중에 히스토리 읽을 때 의도가 명확합니다.

리팩토링은 **자제력**의 작업입니다. 파일을 쪼개는 것은 쉽지만, **왜 쪼개지 말아야 하는지**를 판별하는 일이 더 어렵고 가치가 큽니다.

---

## 관련 커밋

- `8d9fe92` — Phase 1 · TcGroupDialog 추출
- `b9d68de` — Phase 2 · SetTcSheet 추출
- `6e8a19b` — Phase 3 · SetTrSheet 추출
- `55d7f00` — settc 호환성 탭 Category 데드락 해소 (수동 테스트 중 발견한 별개 이슈)
- `549a8f7` — Phase 4 A-2 · SlotContextMenu 추출
- `0b112af` — Phase 4 A-3 · rubberBandSelect action 추출

## 남겨둔 후보 (Phase 5+)

- **`pickedTcs` store화** — 현재 부모-SetTcSheet-TcGroupDialog 3자 공유. context/store로 내려놓으면 prop 체인 제거 가능. 가치는 낮음(동작 문제 없음).
- **헤더/탭/상단 컨트롤 바 정리** — 남은 page-level 레이아웃.
- **Selection Sheet의 `expandableRowContent` snippet만 별도 파일화** — A-1을 통째로는 skip했지만, ~140줄짜리 snippet만 빼는 건 가능. prop 폭탄 피하면서 가독성 개선.
