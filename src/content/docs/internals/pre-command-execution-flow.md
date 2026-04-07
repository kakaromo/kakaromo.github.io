---
title: Pre-Command 실행 흐름
description: 슬롯/TC 사전 명령어의 즉시 실행, 자동 실행(TC 우선순위), DB 데이터 흐름 상세
---

## 1. 즉시 실행 흐름

사용자가 "즉시 실행" 버튼을 클릭했을 때의 전체 흐름입니다.

```mermaid
sequenceDiagram
    participant U as 사용자
    participant Sheet as PreCommandSheet
    participant Page as +page.svelte
    participant API as preCommand.ts
    participant CTRL as PreCommandController
    participant SVC as PreCommandService
    participant SSH as Tentacle (SSH)
    participant Card as 플로팅 카드

    U->>Sheet: "즉시 실행" 클릭
    Sheet->>Page: onExecute(preCommand)
    Page->>Card: visible=true
    Page->>API: executePreCommand(id, source, slots)
    API->>CTRL: POST /api/pre-commands/execute
    CTRL->>SVC: execute(id, source, slotNumbers)
    SVC-->>API: SSE stream

    loop 각 슬롯
        SVC->>SVC: 슬롯 데이터 조회 (usbId, vmName)
        SVC-->>API: slot-start / cmd-start / cmd-done / slot-done
        API-->>Page: onEvent → progress 갱신
        Page-->>Card: 반응성 리렌더
    end

    SVC-->>API: done
    Card->>Card: 완료 표시
```

---

## 2. 자동 실행 흐름 (TC 우선순위)

슬롯이 init 상태에 진입했을 때의 자동 실행 흐름입니다.

```mermaid
sequenceDiagram
    participant HEAD as Head TCP Server
    participant STORE as HeadSlotStateStore
    participant AUTO as PreCommandAutoExecutor
    participant DB as MySQL
    participant SVC as PreCommandService
    participant SSH as Tentacle (SSH)

    HEAD->>STORE: updateSlots(source, slotDataList)
    STORE->>AUTO: onSlotStateChanged(source, oldData, newData)

    AUTO->>AUTO: testState includes "init" ?
    AUTO->>AUTO: 이전 상태가 init 아님 ?

    AUTO->>DB: SlotPreCommand 조회 (source, slotIndex)
    Note over AUTO: tcPreCommandIds: "3,0,5"

    AUTO->>AUTO: setLocation "T3-0" → tentacleName=T3, slotNumber=0
    AUTO->>DB: SlotInfomation 조회 (T3, 0)
    Note over AUTO: testcaseIds: "12/35/12"<br/>testcaseStatus: "27/0/0"

    AUTO->>AUTO: 첫 미완료 position = 1

    AUTO->>AUTO: testToolName == TC[1].name ?
    AUTO->>DB: TC id=35 조회 → name="iozone"

    alt testToolName 일치 + tc_pre_command_ids[1] > 0
        AUTO->>SVC: executeSync(preCommandId, source, [slotIndex])
        SVC->>SSH: SSH 명령어 실행
        Note over AUTO: TC Pre-Command 실행됨
    else tc_pre_command_ids[1] == 0
        alt 슬롯 Pre-Command 있음
            AUTO->>SVC: executeSync(slotPreCommandId, source, [slotIndex])
            Note over AUTO: 슬롯 Pre-Command fallback
        end
    end
```

---

## 3. DB 데이터 흐름 상세

### 시나리오: Slot 0에 TC 3개, TC Pre-Command 2개 등록

**초기 등록 상태:**

```
portal_pre_commands:
  id=3: "tiotest 설치"   commands=["adb push tiotest /dev", "adb shell chmod +x /dev/tiotest"]
  id=5: "fio 설치"       commands=["adb push fio /dev", "adb shell chmod +x /dev/fio"]

portal_slot_pre_commands:
  source=compatibility, slot_index=0, pre_command_id=NULL, tc_pre_command_ids="3,0,5"

SlotInfomation (T3, 0):
  testcaseIds: "12/35/12"    testcaseStatus: "0/0/0"
```

**Phase 1: TC#12 (position 0) init 진입**

```
HEAD → testState: "Init", testToolName: "fio"
DB  → testcaseStatus: "0/0/0"  → 첫 미완료 = position 0
DB  → TC id=12, name="fio"    → testToolName 매칭 ✅
DB  → tc_pre_command_ids[0] = 3 > 0 ✅

→ Pre-Command id=3 "tiotest 설치" 실행
→ SSH: adb -s usb:9-1.4.1 push tiotest /dev
→ SSH: adb -s usb:9-1.4.1 shell chmod +x /dev/tiotest
```

**Phase 2: TC#12 (position 0) 완료 → TC#35 (position 1) init**

```
HEAD → testState: "Init", testToolName: "iozone"
DB  → testcaseStatus: "27/0/0"  → 첫 미완료 = position 1
DB  → TC id=35, name="iozone"  → testToolName 매칭 ✅
DB  → tc_pre_command_ids[1] = 0 → 미등록

→ 슬롯 Pre-Command fallback → pre_command_id = NULL → 실행 없음
```

**Phase 3: TC#35 (position 1) 완료 → TC#12 (position 2) init**

```
HEAD → testState: "Init", testToolName: "fio"
DB  → testcaseStatus: "27/36/0"  → 첫 미완료 = position 2
DB  → TC id=12, name="fio"      → testToolName 매칭 ✅
DB  → tc_pre_command_ids[2] = 5 > 0 ✅

→ Pre-Command id=5 "fio 설치" 실행
```

**Phase 4: 슬롯 clear**

```
HEAD → testState: "Clear"
AutoExecutor → handleClear()
  tc_pre_command_ids: "3,0,5" → NULL
  pre_command_id: NULL → 행 삭제
```

---

## 4. TC 순서 변경 동기화 흐름

```mermaid
sequenceDiagram
    participant User as 사용자
    participant FE as Frontend
    participant API as /tc/sync
    participant DB as MySQL

    User->>FE: TC 순서 드래그&드롭
    FE->>FE: testcaseIds 재배열
    FE->>FE: tc_pre_command_ids도 같은 순서로 재배열

    Note over FE: 변경 전: testcaseIds "12/35/12"<br/>tc_pre_command_ids "3,0,5"
    Note over FE: 변경 후: testcaseIds "35/12/12"<br/>tc_pre_command_ids "0,3,5"

    FE->>API: POST /tc/sync {tcPreCommandIds: "0,3,5"}
    API->>DB: UPDATE tc_pre_command_ids = "0,3,5"
```

---

## 5. 전체 흐름 요약

```mermaid
flowchart TD
    subgraph 등록
        A["Pre-Command 시트\n슬롯 등록"] --> B["pre_command_id"]
        C["TC 테이블 드롭다운\nposition별 등록"] --> D["tc_pre_command_ids"]
    end

    subgraph 자동실행["자동 실행 (init 진입)"]
        E["testcaseStatus 파싱\n→ 현재 대상 position"] --> F{"testToolName\n== TC.name ?"}
        F -->|일치| G{"tc_pre_command_ids\n[position] > 0 ?"}
        G -->|등록됨| H["TC Pre-Command 실행"]
        G -->|미등록| I{"pre_command_id\n!= NULL ?"}
        I -->|있음| J["슬롯 Pre-Command 실행"]
        I -->|없음| K["실행 없음"]
        F -->|불일치| K
    end

    subgraph 동기화
        L["TC 순서 변경"] --> M["tc_pre_command_ids\n같이 재배열"]
        M --> N["POST /tc/sync"]
    end

    subgraph 정리
        O["슬롯 clear"] --> P["tc_pre_command_ids\n→ NULL"]
    end
```
