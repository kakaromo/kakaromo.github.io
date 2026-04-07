---
title: Pre-Command 아키텍처
description: 슬롯/TC 사전 명령어 시스템의 설계, 데이터 모델, 실행 메커니즘, 우선순위 로직
---

## 1. 시스템 개요

Pre-Command는 슬롯의 디바이스에 테스트 전 준비 명령어를 실행하는 시스템입니다. Portal 서버가 Tentacle 서버에 SSH 접속하여 adb/shell 명령어를 직접 실행합니다. **슬롯 단위**와 **TC 단위** 두 레벨의 등록을 지원하며, TC가 우선합니다.

```mermaid
flowchart TD
    subgraph Browser ["Browser (SvelteKit)"]
        UI["Pre-Command 시트\n(등록/즉시실행/관리)"]
        TC_UI["TC 테이블 Pre-Cmd 컬럼\n(TC별 등록/해제)"]
        FC["플로팅 카드\n(실행 진행 표시)"]
    end

    subgraph Portal ["Portal (Spring Boot)"]
        CTRL["PreCommandController\n/api/pre-commands/*"]
        SVC["PreCommandService\nSSH 실행 + SSE"]
        AUTO["PreCommandAutoExecutor\ninit 상태 감지 + 우선순위 판정"]
        STORE["HeadSlotStateStore\n슬롯 상태 관리"]
    end

    subgraph Tentacle ["Tentacle 서버 (T1, T2, ...)"]
        SSH["SSH (JSch ChannelExec)"]
        ADB["adb -s {usbId}"]
    end

    Browser <-->|"REST + SSE\n/api/pre-commands/*"| CTRL
    CTRL --> SVC
    SVC -->|"SSH 접속"| SSH
    SSH --> ADB
    ADB --> Device["Android Device\nUSB 연결"]

    STORE -->|"상태 변화 감지\n(init 진입)"| AUTO
    AUTO -->|"우선순위 판정\nTC > 슬롯"| SVC

    Portal -->|JPA| DB["MySQL 3307\nportal_pre_commands\nportal_slot_pre_commands\nportal_tc_pre_commands"]
```

### 핵심 설계 결정

| 결정 | 이유 |
|------|------|
| **SSH 직접 실행** | Head TCP에 shell 명령어 전달 커맨드가 없음 |
| **ChannelExec** | 단발 명령어 실행. ChannelShell(터미널)과 분리 |
| **SSE 스트리밍** | 슬롯별/명령어별 실시간 진행 표시 |
| **자동 실행** | HeadSlotStateStore 상태 변화 감지 → init 진입 시 트리거 |
| **`-s usbId` 자동 삽입** | 사용자가 usbId를 직접 관리할 필요 없음 |
| **TC > 슬롯 우선순위** | TC별로 다른 명령어가 필요한 경우 대응 |
| **TC 등록 시 슬롯 자동 해제** | 두 레벨 혼합 방지 — 운영 혼란 최소화 |

---

## 2. 패키지 구조

```
com.samsung.portal.head/
├── entity/
│   ├── PreCommand.java          # 명령어 템플릿 엔티티
│   ├── SlotPreCommand.java      # 슬롯-템플릿 매핑 엔티티
│   └── TcPreCommand.java        # TC-템플릿 매핑 엔티티
├── repository/
│   ├── PreCommandRepository.java
│   ├── SlotPreCommandRepository.java
│   └── TcPreCommandRepository.java
├── service/
│   ├── PreCommandService.java        # CRUD + SSH 실행 + SSE
│   ├── PreCommandAutoExecutor.java   # init 상태 자동 실행 + 우선순위 판정
│   └── HeadSlotStateStore.java       # 상태 변화 → AutoExecutor 호출
└── controller/
    └── PreCommandController.java     # REST API (슬롯 + TC)
```

기존 `head/` 패키지의 역할별 구조(entity, repository, service, controller)를 따릅니다.

---

## 3. DB 스키마

### portal_pre_commands

명령어 템플릿 저장. 슬롯/TC 양쪽에서 참조합니다.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK AUTO | 고유 ID |
| name | VARCHAR(100) NOT NULL | 템플릿 이름 |
| description | VARCHAR(500) | 설명 (선택) |
| commands | TEXT NOT NULL | 명령어 JSON 배열 |
| created_at | DATETIME | 생성 시간 |
| updated_at | DATETIME | 수정 시간 |

**commands 예시:**
```json
["adb push tiotest-0.52 /dev", "adb shell chmod +x /dev/tiotest-0.52"]
```

### portal_slot_pre_commands

슬롯별 명령어 등록. init 상태 진입 시 자동 실행 대상.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK AUTO | 고유 ID |
| source | VARCHAR(50) NOT NULL | Head 소스 (compatibility, performance) |
| slot_index | INT NOT NULL | 슬롯 인덱스 |
| pre_command_id | BIGINT FK NOT NULL | → portal_pre_commands.id (CASCADE) |
| created_at | DATETIME | 등록 시간 |

- **UK**: `(source, slot_index)` — 동일 슬롯에 하나만 등록 가능
- 새로운 템플릿으로 교체하면 기존 등록이 업데이트됨

### portal_tc_pre_commands

TC별 명령어 등록. TC 우선순위로 실행.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK AUTO | 고유 ID |
| source | VARCHAR(50) NOT NULL | Head 소스 (compatibility, performance) |
| slot_index | INT NOT NULL | 슬롯 인덱스 |
| tc_id | INT NOT NULL | currentRunningTc (TC ID) |
| pre_command_id | BIGINT FK NOT NULL | → portal_pre_commands.id (CASCADE) |
| created_at | DATETIME | 등록 시간 |

- **UK**: `(source, slot_index, tc_id)` — 동일 TC에 하나만 등록 가능
- 슬롯 clear 시 해당 슬롯의 모든 TC Pre-Command가 자동 삭제됨

### ER 다이어그램

```mermaid
erDiagram
    portal_pre_commands ||--o{ portal_slot_pre_commands : "1:N"
    portal_pre_commands ||--o{ portal_tc_pre_commands : "1:N"

    portal_pre_commands {
        BIGINT id PK
        VARCHAR name
        VARCHAR description
        TEXT commands
        DATETIME created_at
        DATETIME updated_at
    }

    portal_slot_pre_commands {
        BIGINT id PK
        VARCHAR source
        INT slot_index
        BIGINT pre_command_id FK
        DATETIME created_at
    }

    portal_tc_pre_commands {
        BIGINT id PK
        VARCHAR source
        INT slot_index
        INT tc_id
        BIGINT pre_command_id FK
        DATETIME created_at
    }
```

---

## 4. 우선순위 메커니즘

### PreCommandAutoExecutor의 판정 흐름

슬롯이 init 상태에 진입하면 `PreCommandAutoExecutor.onSlotStateChanged()`가 호출됩니다.

```mermaid
flowchart TD
    A["슬롯 상태 변경 감지"] --> B{"testState에 init 포함?"}
    B -->|아니오| C["무시"]
    B -->|예| D{"이전 상태가 init?"}
    D -->|예| C
    D -->|아니오| E{"clear 상태?"}
    E -->|예| F["TC Pre-Command 전체 삭제\n+ 실행 추적 리셋"]
    E -->|아니오| G{"중복 실행 체크\n(executedSlots Set)"}
    G -->|이미 실행됨| C
    G -->|첫 실행| H["currentRunningTc 조회"]
    H --> I{"TC Pre-Command\n등록되어 있음?"}
    I -->|예| J["TC Pre-Command 실행\n(슬롯 것 건너뜀)"]
    I -->|아니오| K{"슬롯 Pre-Command\n등록되어 있음?"}
    K -->|예| L["슬롯 Pre-Command 실행"]
    K -->|아니오| C
```

### 중복 실행 방지

두 개의 `ConcurrentHashMap.newKeySet()`으로 추적합니다:

| Set | 키 형식 | 용도 |
|-----|---------|------|
| `executedSlots` | `"source:slotIndex"` | 슬롯 Pre-Command 중복 방지 |
| `executedTcs` | `"source:slotIndex:tcId"` | TC Pre-Command 중복 방지 |

- init 진입: `add(key)` — 이미 있으면 실행하지 않음
- init 이탈: `remove(key)` — 다음 init 진입 시 다시 실행 가능

---

## 5. 명령어 실행 흐름

### 즉시 실행 (SSE)

```mermaid
sequenceDiagram
    participant B as Browser
    participant C as PreCommandController
    participant S as PreCommandService
    participant T as Tentacle (SSH)

    B->>C: POST /api/pre-commands/execute
    C->>S: execute(preCommandId, source, slotNumbers)
    S-->>B: SSE stream 시작

    loop 각 슬롯
        S->>S: 슬롯 데이터 조회 (usbId, vmName)
        alt 슬롯 검증 실패
            S-->>B: slot-skip {reason}
        else 검증 통과
            S-->>B: slot-start {slotIndex, commandCount}
            loop 각 명령어
                S-->>B: cmd-start {command}
                S->>T: SSH ChannelExec (adb -s {usbId} ...)
                T-->>S: exit code + output
                S-->>B: cmd-done {status, output}
                alt exit code ≠ 0
                    Note over S: 해당 슬롯 나머지 명령어 중단
                end
            end
            S-->>B: slot-done {status}
        end
        S-->>B: summary {completed, failed, skipped}
    end

    S-->>B: done
```

### 자동 실행 (init 감지 + TC 우선순위)

```mermaid
sequenceDiagram
    participant H as Head TCP
    participant SS as HeadSlotStateStore
    participant AE as PreCommandAutoExecutor
    participant DB as MySQL
    participant S as PreCommandService
    participant T as Tentacle (SSH)

    H->>SS: updateSlots(source, slotDataList)
    SS->>SS: 이전 상태와 비교
    alt testState가 init을 포함하고, 이전 상태는 init이 아님
        SS->>AE: onSlotStateChanged(source, oldData, newData)
        AE->>AE: 중복 실행 체크

        AE->>DB: SlotInfomation.currentRunningTc 조회
        AE->>DB: TcPreCommand 조회 (source + slotIndex + tcId)

        alt TC Pre-Command 있음
            AE->>S: executeSync(tcPreCommandId, source, [slotIndex])
            Note over AE: 슬롯 Pre-Command 건너뜀
        else TC Pre-Command 없음
            AE->>DB: SlotPreCommand 조회
            alt 슬롯 Pre-Command 있음
                AE->>S: executeSync(slotPreCommandId, source, [slotIndex])
            end
        end

        loop 각 명령어
            S->>S: adb -s {usbId} 치환
            S->>T: JSch ChannelExec
            T-->>S: exit code + output
        end
    end

    Note over AE: clear 상태 → TC Pre-Command 전체 삭제
```

---

## 6. SSH 실행 상세

### 접속 대상 결정

슬롯의 `setLocation` (예: "T1-S03")에서 VM 이름을 추출하고, `portal_servers` 테이블에서 SSH 접속 정보를 조회합니다.

```java
// "T1-S03" → "T1"
Pattern.compile("^(T\\d+)").matcher(setLocation)
```

### adb 명령어 치환

```java
// "adb push file /dev" → "adb -s usb:9-1.4.1 push file /dev"
Pattern ADB_PREFIX = Pattern.compile("^(adb\\s)");
if (m.find()) {
    return "adb -s " + usbId + " " + command.substring(m.end());
}
```

`adb`로 시작하지 않는 명령어는 변환 없이 그대로 실행됩니다.

### 타임아웃

- 명령어당 **60초** 타임아웃
- stdout 읽기, stderr 읽기, exit status 대기 모두 deadline 체크
- 초과 시 채널 강제 종료, `[TIMEOUT]` 메시지 반환

### 에러 처리

| 상황 | 동작 |
|------|------|
| SSH 접속 실패 | 해당 명령어 에러, 슬롯 나머지 명령어 중단 |
| 명령어 exit code ≠ 0 | 해당 슬롯 나머지 명령어 중단 (다른 슬롯은 계속) |
| 타임아웃 | exit code -1, `[TIMEOUT]` 메시지 |
| SSE 연결 끊김 | 서버 측 실행은 계속됨 (fire-and-forget) |

### 동시성 제한

| 풀 | 최대 스레드 | 대상 |
|----|------------|------|
| `PreCommandService` | **8** | 즉시 실행 (사용자 요청) |
| `PreCommandAutoExecutor` | **4** | 자동 실행 (init 감지) |

20명 동시 사용 기준. 초과 시 대기열에서 순서를 기다립니다 (에러 아님).

---

## 7. 자동 실행 메커니즘 (PreCommandAutoExecutor)

### 상태 감지

`HeadSlotStateStore.updateSlots()`에서 `slots.put()` 호출 시 이전 값(`oldData`)을 보존하고, `PreCommandAutoExecutor.onSlotStateChanged()`를 호출합니다.

### 트리거 조건

```java
// 새 상태가 init을 포함
newState.toLowerCase().contains("init")

// 이전 상태는 init이 아님 (이미 init이면 무시)
oldState == null || !oldState.toLowerCase().contains("init")
```

### clear 상태 처리

슬롯이 clear 상태가 되면:

1. 해당 슬롯의 **모든 TC Pre-Command를 자동 삭제** (`TcPreCommandRepository.deleteBySourceAndSlotIndex()`)
2. `executedSlots`와 `executedTcs` Set에서 해당 슬롯 키 제거
3. 다음 라운드에서 다시 정상 실행 가능

### 순환 의존 해결

`HeadSlotStateStore` → `PreCommandAutoExecutor` → `PreCommandService` → `HeadSlotStateStore` 순환이 발생하므로, `@Lazy` 어노테이션으로 해결합니다.

```java
public HeadSlotStateStore(@Lazy PreCommandAutoExecutor preCommandAutoExecutor) {
    this.preCommandAutoExecutor = preCommandAutoExecutor;
}
```

---

## 8. 프론트엔드 아키텍처

### 컴포넌트 구조

```
+page.svelte (slots)
├── PreCommandSheet.svelte          # 통합 시트 (등록/실행/관리)
│   ├── main 뷰: 슬롯 상태 + 템플릿 목록 + 등록/즉시 실행
│   ├── manage 뷰: 편집/삭제 (hover 시 액션 노출)
│   └── edit 뷰: 생성/수정 폼
├── PreCommandFloatingCard.svelte   # 실행 진행 표시 (순수 표시 컴포넌트)
│   ├── 프로그레스 바 (실행 중: 파랑, 성공: 초록, 실패: 빨강)
│   ├── 실패 슬롯 자동 펼침
│   └── 닫기 시 토스트 요약
├── TcPreCommandCell.svelte         # TC 테이블 Pre-Cmd 드롭다운
│   ├── NOTSTART 상태만 편집 가능
│   ├── 드롭다운 선택 → 즉시 DB 등록
│   └── 등록 시 슬롯 Pre-Command 자동 해제
└── SlotCard.svelte                 # ⚡ 뱃지 + 툴팁 Pre-Cmd 이름
```

### UX 설계 원칙

| 원칙 | 적용 |
|------|------|
| 한 화면 한 목적 | 시트 3단 뷰 전환 (main → manage → edit) |
| 맥락 유지 | 시트 상단에 선택 슬롯 표시, 등록 상태 체크 |
| 즉시 결과 | ⚡ 뱃지, 프로그레스 바, 실패 자동 펼침, 토스트 |
| 선택지 최소화 | 메뉴 1개 → 시트에서 전부 처리, adb -s 자동 |
| 빈 상태 안내 | 예시 명령어 + "첫 명령어 만들기" CTA |
| TC 인라인 편집 | TC 테이블에서 드롭다운으로 즉시 등록 (별도 화면 불필요) |
| 충돌 자동 해결 | TC 등록 시 슬롯 Pre-Command 자동 해제 |

자세한 UX 설계 원칙은 [UX 설계 철학](/developer/ux-philosophy) 참조.

### 상태 관리

플로팅 카드는 **부모(+page.svelte)에서 상태를 직접 관리**합니다. Svelte 5 runes 모드에서 `bind:this` + `export function` 패턴이 동작하지 않기 때문입니다.

```typescript
// 부모에서 progress 객체를 직접 업데이트
let preCommandProgress = $state<PreCommandProgress>({...});

function updatePreCommandProgress(type, data) {
    // 매번 새 객체 생성으로 Svelte 반응성 보장
    preCommandProgress = { ...preCommandProgress, ... };
}

// 플로팅 카드는 props로 수신 (순수 표시)
<PreCommandFloatingCard progress={preCommandProgress} />
```

### SSE 클라이언트

`preCommand.ts`의 `executePreCommand()` 함수가 SSE 스트림을 처리합니다.

- `fetch()` + `ReadableStream`으로 SSE 수신
- `\n\n` 블록 단위로 이벤트 파싱
- 멀티라인 `data:` 지원 (Spring이 JSON을 여러 줄로 분할할 수 있음)
- 스트림 종료 시 `done` 이벤트 강제 발행

---

## 9. 파일 목록

| 파일 경로 | 타입 | 역할 |
|-----------|------|------|
| `entity/PreCommand.java` | Entity | 명령어 템플릿 |
| `entity/SlotPreCommand.java` | Entity | 슬롯-템플릿 매핑 |
| `entity/TcPreCommand.java` | Entity | TC-템플릿 매핑 |
| `repository/PreCommandRepository.java` | Repository | 템플릿 CRUD |
| `repository/SlotPreCommandRepository.java` | Repository | 슬롯 등록 CRUD |
| `repository/TcPreCommandRepository.java` | Repository | TC 등록 CRUD |
| `service/PreCommandService.java` | Service | CRUD + SSH 실행 + SSE |
| `service/PreCommandAutoExecutor.java` | Component | init 감지 + 우선순위 판정 |
| `service/HeadSlotStateStore.java` | Component | 상태 변화 → AutoExecutor 호출 |
| `controller/PreCommandController.java` | Controller | REST API (슬롯 + TC) |
| `frontend/.../api/preCommand.ts` | API Client | REST + SSE 클라이언트 |
| `frontend/.../PreCommandSheet.svelte` | Component | 통합 시트 (3단 뷰) |
| `frontend/.../PreCommandFloatingCard.svelte` | Component | 실행 진행 플로팅 카드 |
| `frontend/.../TcPreCommandCell.svelte` | Component | TC 테이블 드롭다운 |
| `frontend/.../SlotCard.svelte` | Component | ⚡ 뱃지 표시 |
