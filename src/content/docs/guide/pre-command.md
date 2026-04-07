---
title: Pre-Command (사전 명령어)
description: 슬롯/TC 단위로 테스트 시작 전 adb/shell 명령어를 자동 실행하는 Pre-Command 기능의 설계, 구현, 사용법, UX 가이드
---

Pre-Command는 테스트 시작 전에 슬롯의 디바이스에 필요한 명령어(adb push, chmod 등)를 미리 실행하는 기능입니다. 명령어 템플릿을 만들어 두고, **슬롯 단위** 또는 **TC 단위**로 등록하면 init 상태 진입 시 자동으로 실행됩니다.

---

## 1. 개요

### 두 가지 등록 레벨

| | 슬롯 Pre-Command | TC Pre-Command |
|---|---|---|
| **등록 단위** | 슬롯 1개당 1개 | TC position별 각각 등록 |
| **등록 위치** | Pre-Command 시트 | TC 테이블의 Pre-Cmd 드롭다운 |
| **실행 조건** | init 진입 시 (TC Pre-Command 없을 때) | init 진입 + testToolName 매칭 + 이전 TC 완료 |
| **우선순위** | 낮음 | **높음** (TC 것이 있으면 슬롯 것 무시) |
| **저장 구조** | `pre_command_id` (FK) | `tc_pre_command_ids` (comma 구분 문자열) |

### 우선순위 규칙

슬롯이 init 상태에 진입하면 다음 순서로 실행할 명령어를 결정합니다:

1. `testcaseStatus`에서 **현재 대상 TC position** 결정 (이전 TC 모두 완료, 현재 미완료)
2. `testToolName`이 해당 TC의 `name`과 일치하는지 확인
3. 해당 position에 TC Pre-Command가 등록되어 있으면 → **TC Pre-Command 실행**
4. 없으면 → 슬롯 Pre-Command 실행 (fallback)

---

## 2. UI Mockup — 전체 화면 구성

### 2.1 슬롯 카드 뷰

슬롯 카드 그리드에서 Pre-Command 등록 상태를 시각적으로 확인할 수 있습니다.

```
┌─── compatibility 탭 ─────────────────────────────────────────────────────────┐
│                                                                              │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐           │
│  │ Slot 0  T3-0 ⚡● │  │ Slot 1  T3-1   ● │  │ Slot 2  T3-2   ● │           │
│  │                   │  │                   │  │                   │           │
│  │  ● Running        │  │  Standby          │  │  Standby          │           │
│  │  fio              │  │                   │  │                   │           │
│  │  perf-test        │  │                   │  │                   │           │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘           │
│   ↑                                                                          │
│   ⚡ = Pre-Command 등록됨                                                     │
│   tooltip: "Pre-Cmd: tiotest 설치"                                           │
│                                                                              │
│  ┌──────────────────┐  ┌──────────────────┐                                 │
│  │ Slot 15  T3-15 ● │  │ Slot 16  T4-0  ● │                                 │
│  │  Standby          │  │  Standby          │                                 │
│  └──────────────────┘  └──────────────────┘                                 │
│                                                                              │
│  [ ☑ Slot 0 선택됨 ]                    [ Pre-Command ]  [ 기타 액션 ... ]   │
└──────────────────────────────────────────────────────────────────────────────┘
```

**이 상태의 DB 데이터:**
```sql
-- portal_slot_pre_commands
source='compatibility', slot_index=0, pre_command_id=3, tc_pre_command_ids='3,0,5'
--                                     ↑ 슬롯 Pre-Cmd      ↑ TC별 Pre-Cmd
--                                     "tiotest 설치"       pos0=3, pos1=없음, pos2=5
```

---

### 2.2 TC 테이블 — Pre-Cmd 드롭다운

슬롯을 클릭하면 우측에 TC 테이블이 나타납니다. **Pre-Cmd** 컬럼에서 TC별 Pre-Command를 설정합니다.

```
┌─── Slot 0 (T3-0) TC 목록 ───────────────────────────────────────────────┐
│                                                                          │
│  ┌────┬──────────┬───────────┬──────────────┬─────────────────────────┐  │
│  │ #  │ TC       │ Model     │ State        │ Pre-Cmd                 │  │
│  ├────┼──────────┼───────────┼──────────────┼─────────────────────────┤  │
│  │ 1  │ fio      │ PM1733    │ ✅ PASS      │  tiotest 설치           │  │
│  │ 2  │ fio      │ PM1733    │ ● RUNNING    │  (읽기전용) -           │  │
│  │ 3  │ iozone   │ PM1733    │ ○ NOTSTART   │  ┌─────────────┐  [x]  │  │
│  │    │          │           │              │  │ fio 설치   ▼ │       │  │
│  │    │          │           │              │  └─────────────┘       │  │
│  │ 4  │ iozone   │ PM1733    │ ○ NOTSTART   │  ┌─────────────┐       │  │
│  │    │          │           │              │  │     -      ▼ │       │  │
│  │    │          │           │              │  └─────────────┘       │  │
│  │ 5  │ fio      │ PM1733    │ ○ NOTSTART   │  ┌─────────────┐       │  │
│  │    │          │           │              │  │     -      ▼ │       │  │
│  │    │          │           │              │  └─────────────┘       │  │
│  └────┴──────────┴───────────┴──────────────┴─────────────────────────┘  │
│                                                                          │
│  ● PASS/RUNNING 상태는 읽기 전용 (텍스트만 표시)                          │
│  ● NOTSTART 상태만 드롭다운으로 편집 가능                                  │
│  ● [x] 버튼으로 해제                                                     │
└──────────────────────────────────────────────────────────────────────────┘
```

**이 상태의 DB 데이터:**
```sql
-- portal_slot_pre_commands
source='compatibility', slot_index=0, pre_command_id=3, tc_pre_command_ids='3,0,5,0,0'
--                                                        ↑   ↑   ↑   ↑   ↑
--                                                       #1  #2  #3  #4  #5
--                                                   tiotest  -  fio  -   -

-- portal_pre_commands (템플릿)
-- id=3: "tiotest 설치"
-- id=5: "fio 설치"

-- SlotInfomation (T3, 0) — 외부 DB
-- testcaseIds:    '12/12/35/35/12'     (fio/fio/iozone/iozone/fio)
-- testcaseStatus: '27/21/0/0/0'        (PASS/RUNNING/NOTSTART/NOTSTART/NOTSTART)
```

**드롭다운 선택 시 DB 변화:**
```
#4 (position 3)에 "tiotest 설치"(id=3) 선택:

변경 전: tc_pre_command_ids = '3,0,5,0,0'
변경 후: tc_pre_command_ids = '3,0,5,3,0'
                                     ↑ position 3 → 3
```

---

### 2.3 Pre-Command 시트 — 슬롯 등록/즉시 실행

슬롯을 선택하고 "Pre-Command" 버튼을 누르면 우측 시트가 열립니다.

```
┌──────────────────────────────────────────┐
│  Pre-Command                        ← × │
│─────────────────────────────────────────│
│                                          │
│  선택: Slot 0 (T3-0), Slot 1 (T3-1)     │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │ ✅ tiotest 설치             [해제] │  │
│  │                                    │  │
│  │  adb push tiotest-0.52 /dev       │  │
│  │  adb shell chmod +x /dev/tiotest  │  │
│  │                                    │  │
│  │                      [▶ 즉시 실행] │  │
│  └────────────────────────────────────┘  │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │    fio 설치                 [등록] │  │
│  │                                    │  │
│  │  adb push fio /dev                │  │
│  │  adb shell chmod +x /dev/fio     │  │
│  │                                    │  │
│  │                      [▶ 즉시 실행] │  │
│  └────────────────────────────────────┘  │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │    디렉토리 정리             [등록] │  │
│  │                                    │  │
│  │  adb shell rm -rf /data/output    │  │
│  │  adb shell mkdir -p /data/output  │  │
│  │                                    │  │
│  │                      [▶ 즉시 실행] │  │
│  └────────────────────────────────────┘  │
│                                          │
│  ⚙ 관리                   [전체 해제]   │
└──────────────────────────────────────────┘
```

**"등록" 클릭 시 DB 변화 (fio 설치, id=5):**
```
Slot 0: pre_command_id = NULL → 5
Slot 1: (행 생성) source='compatibility', slot_index=1, pre_command_id=5
```

**"해제" 클릭 시 DB 변화 (tiotest 설치, id=3):**
```
Slot 0: pre_command_id = 3 → NULL
  (tc_pre_command_ids가 '3,0,5,0,0'이므로 행은 유지)
Slot 1: pre_command_id = 3 → NULL
  (tc_pre_command_ids가 NULL이므로 행 삭제)
```

---

### 2.4 플로팅 카드 — 실행 진행

"즉시 실행" 클릭 후 우하단에 플로팅 카드가 나타납니다.

**실행 중 (펼침):**
```
                                    ┌───────────────────────────────────────┐
                                    │ 🔄 tiotest 설치          2/3   ▾  × │
                                    │ ████████████████░░░░░░░░░  66%       │
                                    │──────────────────────────────────────│
                                    │                                      │
                                    │ ✅ Slot 0 (T3-0)                     │
                                    │    ✅ adb -s usb:1-4 push tiotest   │
                                    │       → tiotest: 1 file pushed.     │
                                    │    ✅ adb -s usb:1-4 shell chmod    │
                                    │       → (성공)                       │
                                    │                                      │
                                    │ 🔄 Slot 1 (T3-1)                     │
                                    │    ✅ adb -s usb:1-5 push tiotest   │
                                    │       → tiotest: 1 file pushed.     │
                                    │    🔄 adb -s usb:1-5 shell chmod    │
                                    │       → (실행 중...)                 │
                                    │                                      │
                                    │ ○ Slot 2 (T3-2)  대기               │
                                    │                                      │
                                    └───────────────────────────────────────┘
```

**완료 (실패 포함, 실패 슬롯 자동 펼침):**
```
                                    ┌───────────────────────────────────────┐
                                    │ ❌ tiotest 설치          3/3   ▾  × │
                                    │ ████████████████████████████  빨강   │
                                    │──────────────────────────────────────│
                                    │                                      │
                                    │ ✅ Slot 0 (T3-0)   2/2 성공         │
                                    │                                      │
                                    │ ❌ Slot 1 (T3-1)   1/2 실패 ▾       │
                                    │    ✅ adb -s usb:1-5 push tiotest   │
                                    │    ❌ adb -s usb:1-5 shell chmod    │
                                    │       ┌──────────────────────────┐   │
                                    │       │ chmod: /dev/tiotest:     │   │
                                    │       │ Operation not permitted  │   │
                                    │       │ exit code: 1             │   │
                                    │       └──────────────────────────┘   │
                                    │                                      │
                                    │ ✅ Slot 2 (T3-2)   2/2 성공         │
                                    │                                      │
                                    │ 2 성공 · 1 실패 · 0 스킵             │
                                    └───────────────────────────────────────┘
```

**접힌 상태:**
```
                                    ┌───────────────────────────────────────┐
                                    │ 🔄 tiotest 설치          2/3   ▴  × │
                                    │ ████████████████░░░░░░░░░  66%       │
                                    └───────────────────────────────────────┘
```

---

### 2.5 관리 뷰 — 편집/삭제

시트 하단 "⚙ 관리" 클릭 시 관리 뷰로 전환됩니다.

```
┌──────────────────────────────────────────┐
│  ← Pre-Command 관리                   × │
│─────────────────────────────────────────│
│                                          │
│  ┌────────────────────────────────────┐  │
│  │ tiotest 설치               ✏️  🗑️ │  │
│  │  adb push tiotest-0.52 /dev       │  │
│  │  adb shell chmod +x /dev/tiotest  │  │
│  └────────────────────────────────────┘  │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │ fio 설치                   ✏️  🗑️ │  │
│  │  adb push fio /dev                │  │
│  │  adb shell chmod +x /dev/fio     │  │
│  └────────────────────────────────────┘  │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │ 디렉토리 정리              ✏️  🗑️ │  │
│  │  adb shell rm -rf /data/output    │  │
│  │  adb shell mkdir -p /data/output  │  │
│  └────────────────────────────────────┘  │
│                                          │
│            [ + 새 명령어 추가 ]           │
└──────────────────────────────────────────┘
```

### 2.6 편집 뷰 — 생성/수정

"✏️" 또는 "+ 새 명령어 추가" 클릭 시 편집 뷰로 전환됩니다.

```
┌──────────────────────────────────────────┐
│  ← 명령어 편집                        × │
│─────────────────────────────────────────│
│                                          │
│  이름 *                                  │
│  ┌────────────────────────────────────┐  │
│  │ tiotest 설치                       │  │
│  └────────────────────────────────────┘  │
│                                          │
│  설명 (선택)                             │
│  ┌────────────────────────────────────┐  │
│  │ tiotest 바이너리를 디바이스에 배포  │  │
│  └────────────────────────────────────┘  │
│                                          │
│  명령어 (한 줄에 하나씩) *               │
│  ┌────────────────────────────────────┐  │
│  │ adb push tiotest-0.52 /dev        │  │
│  │ adb shell chmod +x /dev/tiotest   │  │
│  │ adb shell ls -al /dev/tiotest*    │  │
│  │                                    │  │
│  │                                    │  │
│  └────────────────────────────────────┘  │
│  ℹ️ adb 명령어는 자동으로 -s {usbId}     │
│     삽입됩니다                            │
│                                          │
│          [ 취소 ]    [ 저장 ]             │
└──────────────────────────────────────────┘
```

---

## 3. 데이터 구조 — 한 행으로 통합 관리

### DB 테이블: `portal_slot_pre_commands`

```
┌─────────────────────────────────────────────────────────────────────┐
│  portal_slot_pre_commands                                           │
├──────────────────────┬──────────────────────────────────────────────┤
│ source               │ "compatibility"                              │
│ slot_index           │ 0                                            │
│ pre_command_id       │ 3  (슬롯 Pre-Cmd, nullable)                 │
│ tc_pre_command_ids   │ "3,0,5,0,0"  (TC별, 0=미등록)               │
└──────────────────────┴──────────────────────────────────────────────┘
```

### testcaseIds와 1:1 대응

```
SlotInfomation (T3, slotNumber=0):
  testcaseIds:       12  /  12  /  35  /  35  /  12
  testcaseStatus:    27  /  21  /   0  /   0  /   0
                    PASS  RUNNING     ←── 현재 대상 position

portal_slot_pre_commands (compatibility, slotIndex=0):
  tc_pre_command_ids:  3  ,   0  ,   5  ,   0  ,   0
                   tiotest  (없음)  fio   (없음) (없음)
```

| position | tcId | TC name | status | tc_pre_command_ids | 의미 |
|----------|------|---------|--------|-------------------|------|
| 0 | 12 | fio | 27 (PASS) | **3** | 완료, "tiotest 설치" (이미 실행됨) |
| 1 | 12 | fio | 21 (RUNNING) | 0 | 실행 중, Pre-Command 없음 |
| **2** | **35** | **iozone** | **0 (NOTSTART)** | **5** | **다음 대상** — "fio 설치" 등록됨 |
| 3 | 35 | iozone | 0 (NOTSTART) | 0 | 대기 중, Pre-Command 없음 |
| 4 | 12 | fio | 0 (NOTSTART) | 0 | 대기 중, Pre-Command 없음 |

:::tip
같은 tcId(12)가 position 0, 1, 4에 나와도 **각각 다른 Pre-Command** 설정이 가능합니다.
:::

---

## 4. TC Pre-Command 자동 실행 흐름

### 실행 조건 (4가지 모두 충족)

```mermaid
flowchart TD
    A["슬롯 testState에 init 포함"] --> B{"이전 상태가 init?"}
    B -->|예| Z["무시 (중복 방지)"]
    B -->|아니오| C["setLocation에서\ntentacleName/slotNumber 추출"]
    C --> D["SlotInfomation 조회\ntestcaseIds, testcaseStatus"]
    D --> E{"testcaseStatus에서\n첫 번째 미완료(0) position"}
    E -->|모두 완료| Z2["실행 없음"]
    E -->|position N| F{"testToolName ==\nTC[N].name ?"}
    F -->|불일치| Z3["실행 안 함"]
    F -->|일치| G{"tc_pre_command_ids[N]\n> 0 ?"}
    G -->|0 (미등록)| H{"슬롯 Pre-Command\n있음?"}
    H -->|있음| I["슬롯 Pre-Command 실행"]
    H -->|없음| Z4["실행 없음"]
    G -->|"preCommandId"| J["TC Pre-Command 실행\n(슬롯 것 건너뜀)"]
```

### Step별 실행 시나리오

**초기 상태:**
```
testcaseIds:         "12/35/12"          (fio / iozone / fio)
testcaseStatus:      "0/0/0"
tc_pre_command_ids:  "3,0,5"             (tiotest / 없음 / fio설치)
pre_command_id:      7                   (슬롯: 디렉토리 정리)
```

---

**Step 1 — TC#12 (position 0) init 진입**

```
┌─ HEAD 데이터 ─────────────────────────────────────────────┐
│ testState: "Init"    testToolName: "fio"                   │
└────────────────────────────────────────────────────────────┘

┌─ DB 상태 ─────────────────────────────────────────────────┐
│ testcaseStatus:      [ 0 ] / 0 / 0    ← position 0 = 첫 미완료  │
│ testcaseIds:         [12] / 35 / 12                        │
│ tc_pre_command_ids:  [ 3] /  0 /  5                        │
│                        ↑                                   │
│                    TC[0] id=12, name="fio"                 │
│                    testToolName "fio" == "fio" ✅           │
│                    tc_pre_cmd_ids[0] = 3 > 0 ✅             │
└────────────────────────────────────────────────────────────┘

→ Pre-Command id=3 "tiotest 설치" 실행
→ SSH: adb -s usb:9-1.4 push tiotest-0.52 /dev
→ SSH: adb -s usb:9-1.4 shell chmod +x /dev/tiotest-0.52
```

---

**Step 2 — TC#12 완료 → TC#35 (position 1) init 진입**

```
┌─ HEAD 데이터 ─────────────────────────────────────────────┐
│ testState: "Init"    testToolName: "iozone"                │
└────────────────────────────────────────────────────────────┘

┌─ DB 상태 ─────────────────────────────────────────────────┐
│ testcaseStatus:      27 / [ 0 ] / 0    ← position 1 = 첫 미완료  │
│ testcaseIds:         12 / [35] / 12                        │
│ tc_pre_command_ids:   3 / [ 0] /  5                        │
│                             ↑                              │
│                    TC[1] id=35, name="iozone"              │
│                    testToolName "iozone" == "iozone" ✅     │
│                    tc_pre_cmd_ids[1] = 0 → 미등록           │
│                    → 슬롯 Pre-Command fallback              │
│                    pre_command_id = 7 ✅                     │
└────────────────────────────────────────────────────────────┘

→ Pre-Command id=7 "디렉토리 정리" 실행 (슬롯 fallback)
→ SSH: adb -s usb:9-1.4 shell rm -rf /data/output
→ SSH: adb -s usb:9-1.4 shell mkdir -p /data/output
```

---

**Step 3 — TC#35 완료 → TC#12 (position 2) init 진입**

```
┌─ HEAD 데이터 ─────────────────────────────────────────────┐
│ testState: "Init"    testToolName: "fio"                   │
└────────────────────────────────────────────────────────────┘

┌─ DB 상태 ─────────────────────────────────────────────────┐
│ testcaseStatus:      27 / 36 / [ 0 ]   ← position 2 = 첫 미완료  │
│ testcaseIds:         12 / 35 / [12]                        │
│ tc_pre_command_ids:   3 /  0 / [ 5]                        │
│                                  ↑                         │
│                    TC[2] id=12, name="fio"                 │
│                    testToolName "fio" == "fio" ✅           │
│                    tc_pre_cmd_ids[2] = 5 > 0 ✅             │
└────────────────────────────────────────────────────────────┘

→ Pre-Command id=5 "fio 설치" 실행
→ SSH: adb -s usb:9-1.4 push fio /dev
→ SSH: adb -s usb:9-1.4 shell chmod +x /dev/fio
```

---

**Step 4 — 슬롯 clear**

```
┌─ HEAD 데이터 ─────────────────────────────────────────────┐
│ testState: "Clear"                                         │
└────────────────────────────────────────────────────────────┘

┌─ DB 변화 ─────────────────────────────────────────────────┐
│ 변경 전: tc_pre_command_ids = "3,0,5"                      │
│ 변경 후: tc_pre_command_ids = NULL                          │
│          pre_command_id = 7 (슬롯 Pre-Cmd 유지)            │
└────────────────────────────────────────────────────────────┘
```

---

## 5. TC 순서 변경 시 동기화

TC 순서가 변경되면 `tc_pre_command_ids`도 같은 순서로 재배열됩니다.

```
┌─ 변경 전 ──────────────────────────────────────────────┐
│ testcaseIds:         "12 / 35 / 40"                     │
│ tc_pre_command_ids:  " 3 ,  5 ,  0"                     │
│                    tiotest  fio  (없음)                  │
└─────────────────────────────────────────────────────────┘

    사용자가 TC 순서를 [35, 12, 40]으로 변경

┌─ 변경 후 ──────────────────────────────────────────────┐
│ testcaseIds:         "35 / 12 / 40"                     │
│ tc_pre_command_ids:  " 5 ,  3 ,  0"   ← 같이 재배열     │
│                      fio  tiotest (없음)                 │
└─────────────────────────────────────────────────────────┘

→ POST /api/pre-commands/tc/sync { tcPreCommandIds: "5,3,0" }
```

**TC 추가/삭제:**
```
TC 추가 (position 1에 삽입):  "3,5,0" → "3,0,5,0"
TC 삭제 (position 1 제거):    "3,5,0" → "3,0"
```

---

## 6. 접근 방법

| 방법 | 경로 |
|------|------|
| **컨텍스트 메뉴** | 슬롯 우클릭 → Prepare Test → Pre-Command |
| **선택 시트** | 슬롯 다중 선택 → 하단 "Pre-Command" 버튼 |

---

## 7. 슬롯 Pre-Command 등록

슬롯에 Pre-Command를 등록하면, 해당 슬롯이 **init 상태에 진입할 때 자동으로 실행**됩니다. 단, TC Pre-Command가 등록된 position이면 TC 것이 우선합니다.

| 조건 | 설명 |
|------|------|
| testState | init 포함 (대소문자 무시) |
| 이전 상태 | init이 아니어야 함 |
| TC 우선순위 | 현재 position에 TC Pre-Command가 있으면 슬롯 것은 건너뜀 |
| 중복 방지 | 같은 슬롯에 대해 한 번만 실행 |

---

## 8. TC Pre-Command 등록

### 등록 방법

1. 슬롯 선택 → TC 테이블 표시
2. **NOTSTART** 상태인 TC의 **Pre-Cmd** 드롭다운에서 선택
3. X 버튼으로 해제

:::caution
NOTSTART 상태인 TC만 설정/변경 가능합니다. RUNNING/PASS 등은 읽기 전용입니다.
:::

### 실행 조건 (4가지 모두 충족)

| # | 조건 | 설명 |
|---|------|------|
| 1 | **이전 TC 완료** | testcaseStatus에서 현재 position 이전이 모두 ≠ 0 |
| 2 | **testToolName 매칭** | HEAD의 testToolName == TC.name (대소문자 무시) |
| 3 | **testState init** | testState에 "init" 포함 |
| 4 | **Pre-Command 등록됨** | tc_pre_command_ids[position] > 0 |

### 자동 삭제

| 상황 | 동작 |
|------|------|
| **슬롯 clear** | `tc_pre_command_ids` → NULL |
| **수동 해제** (X 버튼) | 해당 position만 0으로 변경 |
| **TC 순서 변경** | 프론트에서 재배열 후 sync API 호출 |

---

## 9. 즉시 실행

등록 없이 선택한 슬롯들에 바로 명령어를 실행합니다. 슬롯 상태에 관계없이 실행 가능합니다.

1. 슬롯 선택 → Pre-Command 시트 열기
2. "즉시 실행" 버튼 클릭
3. 우하단 플로팅 카드에서 실시간 진행 표시

---

## 10. adb 명령어 자동 처리

`adb`로 시작하는 명령어에는 자동으로 `-s {usbId}`가 삽입됩니다.

| 입력 | 실제 실행 |
|------|-----------|
| `adb push file /dev` | `adb -s usb:9-1.4.1 push file /dev` |
| `adb shell chmod +x /dev/file` | `adb -s usb:9-1.4.1 shell chmod +x /dev/file` |
| `ls -al /data` | `ls -al /data` (adb 아님, 변환 없음) |

---

## 11. SSH 실행 환경

| 항목 | 설명 |
|------|------|
| **SSH 대상** | `setLocation`에서 추출 (예: "T3-0" → T3 서버) |
| **접속 정보** | `portal_servers` 테이블의 SSH 설정 |
| **실행 방식** | JSch `ChannelExec` (단발 명령어) |
| **타임아웃** | 명령어당 60초 |
| **실패 처리** | exit code ≠ 0 → 해당 슬롯 나머지 명령어 중단 |
| **동시 실행** | 즉시 실행 8스레드, 자동 실행 4스레드 |

---

## 12. 활용 예시

### tiotest 바이너리 배포
```
adb push tiotest-0.52 /dev
adb shell chmod +x /dev/tiotest-0.52
```

### fio 설치 및 확인
```
adb push fio /dev
adb shell chmod +x /dev/fio
adb shell /dev/fio --version
```

### 테스트 디렉토리 정리
```
adb shell rm -rf /data/test_output
adb shell mkdir -p /data/test_output
```
