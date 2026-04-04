---
title: Pre-Command 실행 흐름
description: 사전 명령어의 즉시 실행과 자동 실행의 전체 데이터 흐름 상세
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
    Page->>Page: preCommandProgress 초기화
    Page->>Card: visible=true, progress 전달
    Page->>API: executePreCommand(id, source, slots)
    API->>CTRL: POST /api/pre-commands/execute
    CTRL->>SVC: execute(id, source, slotNumbers)
    SVC-->>API: SSE stream 시작

    Note over SVC: 슬롯별 순차 처리

    SVC-->>API: event: start
    API-->>Page: onEvent → updatePreCommandProgress
    Page-->>Card: progress 갱신 (반응성)

    loop 각 슬롯
        SVC->>SVC: HeadSlotStateStore에서 슬롯 데이터 조회

        alt vmName 추출 실패
            SVC-->>API: event: slot-skip
        else 검증 통과
            SVC-->>API: event: slot-start

            loop 각 명령어
                SVC-->>API: event: cmd-start
                SVC->>SVC: adb → adb -s {usbId} 치환
                SVC->>SSH: JSch ChannelExec
                SSH-->>SVC: stdout + stderr + exit code
                SVC-->>API: event: cmd-done

                alt exit code ≠ 0
                    Note over SVC: break (나머지 명령어 중단)
                end
            end

            SVC-->>API: event: slot-done
        end
        SVC-->>API: event: summary
    end

    SVC-->>API: event: done
    API-->>Page: onEvent(done)
    Page-->>Card: progress.done = true
    Card->>Card: 스피너 → 완료 아이콘
```

### 타이밍 상세

1. **SSE 연결**: `fetch()` + `ReadableStream`으로 SSE 수신
2. **이벤트 파싱**: `\n\n` 블록 단위 파싱, 멀티라인 `data:` 지원
3. **상태 갱신**: 매 이벤트마다 `updatePreCommandProgress()` 호출 → 새 `progress` 객체 생성
4. **반응성**: Svelte 5 `$state`가 변경 감지 → 플로팅 카드 자동 리렌더
5. **스트림 종료**: reader.read()의 `done`이 true → 추가 `done` 이벤트 강제 발행 (안전장치)

---

## 2. 자동 실행 흐름

슬롯이 init 상태에 진입했을 때의 자동 실행 흐름입니다.

```mermaid
sequenceDiagram
    participant HEAD as Head TCP Server
    participant TCP as HeadTcpClient
    participant STORE as HeadSlotStateStore
    participant AUTO as PreCommandAutoExecutor
    participant REPO as SlotPreCommandRepository
    participant SVC as PreCommandService
    participant SSH as Tentacle (SSH)

    HEAD->>TCP: 슬롯 상태 메시지 (backtick 구분)
    TCP->>TCP: HeadMessageParser.parseMessage()
    TCP->>STORE: updateSlots(source, slotDataList)

    STORE->>STORE: slots.put(key, newData) → oldData 반환
    STORE->>AUTO: onSlotStateChanged(source, oldData, newData)

    AUTO->>AUTO: newState.contains("init") 체크
    AUTO->>AUTO: oldState가 init 아닌지 체크
    AUTO->>AUTO: executedSlots.add(slotKey) 중복 체크

    AUTO->>REPO: findBySourceAndSlotIndex()

    alt 등록된 Pre-Command 있음
        REPO-->>AUTO: SlotPreCommand (preCommandId 포함)
        AUTO->>AUTO: executor.submit() — 비동기 실행
        AUTO->>SVC: executeSync(preCommandId, source, [slotIndex])

        loop 각 명령어
            SVC->>SVC: adb -s {usbId} 치환
            SVC->>SSH: JSch ChannelExec
            SSH-->>SVC: exit code + output
            SVC->>SVC: 로그 기록

            alt exit code ≠ 0
                Note over SVC: break + warn 로그
            end
        end
    else 등록 없음
        Note over AUTO: 아무 동작 없음
    end
```

### 자동 실행 특성

| 항목 | 설명 |
|------|------|
| **비동기** | `ExecutorService.submit()`으로 별도 스레드에서 실행 |
| **SSE 없음** | `executeSync()` 사용 — 로그만 남김 |
| **중복 방지** | `ConcurrentHashMap.newKeySet()`으로 추적 |
| **재실행** | 슬롯이 init → 다른 상태 → 다시 init이 되면 재실행 |

---

## 3. SSH 명령어 실행 상세

하나의 명령어가 실행되는 과정입니다.

```mermaid
flowchart TD
    A["resolveCommand(rawCmd, usbId)"] --> B{"adb로 시작?"}
    B -->|예| C["adb -s {usbId} {나머지}"]
    B -->|아니오| D["rawCmd 그대로"]

    C --> E["extractVmName(setLocation)"]
    D --> E

    E --> F["PortalServerService.findByName(vmName)"]
    F --> G["JSch 세션 생성 (SSH)"]
    G --> H["ChannelExec 열기 + 명령어 설정"]
    H --> I["channel.connect(10초 타임아웃)"]
    I --> J["stdout + stderr 읽기 (60초 deadline)"]
    J --> K{"타임아웃?"}

    K -->|예| L["channel.disconnect() + TIMEOUT 반환"]
    K -->|아니오| M["channel.isClosed() 대기"]
    M --> N["exitStatus 조회"]
    N --> O["session.disconnect()"]
    O --> P["CommandResult(exitCode, output)"]
```

### SSH 세션 관리

- 명령어 **하나당 하나의 SSH 세션**을 생성하고 종료
- 세션 재사용을 하지 않는 이유: 명령어 간 환경 격리, ChannelExec는 세션당 하나
- 성능보다 안정성 우선 (세션 풀링은 추후 최적화 가능)

---

## 4. 프론트엔드 상태 관리 흐름

플로팅 카드의 상태 관리는 부모 컴포넌트에서 직접 수행합니다.

```mermaid
flowchart LR
    subgraph "+page.svelte"
        A["preCommandProgress\n($state)"]
        B["updatePreCommandProgress(type, data)"]
    end

    subgraph "preCommand.ts"
        C["executePreCommand()\nSSE fetch + parse"]
    end

    subgraph "PreCommandFloatingCard"
        D["progress (props)\n순수 표시"]
    end

    C -->|"onEvent 콜백"| B
    B -->|"새 객체 할당"| A
    A -->|"Svelte 반응성"| D
```

### 설계 이유

Svelte 5 runes 모드에서 `bind:this` + `export function` 패턴이 외부 호출을 지원하지 않습니다. 따라서:

1. 플로팅 카드는 **순수 표시 컴포넌트** (상태 로직 없음)
2. 부모가 `PreCommandProgress` 객체를 매 이벤트마다 **새로 생성** (`{...spread}`)
3. Svelte의 참조 비교로 변경을 감지하여 리렌더

---

## 5. SSE 파싱 상세

Spring의 `SseEmitter`는 다음 형식으로 이벤트를 전송합니다:

```
event:cmd-start
data:{"slotIndex":0,"cmdIndex":0,"command":"adb -s usb:9-1.4.1 push file /dev"}

event:cmd-done
data:{"slotIndex":0,"cmdIndex":0,"status":"success","exitCode":0,
data:"output":"file pushed"}

```

### 파싱 규칙

1. `\n\n`으로 이벤트 블록 분리
2. 각 블록에서 `event:` 라인으로 이벤트 이름 추출
3. `data:` 라인은 **여러 줄일 수 있음** — 모두 합쳐서 JSON 파싱
4. `event` + `data` 모두 있을 때만 이벤트 발행
5. 스트림 종료(`reader.done`) 시 `done` 이벤트 강제 발행 (서버 이벤트 누락 대비)

### 에러 처리

| 상황 | 동작 |
|------|------|
| HTTP 에러 (4xx, 5xx) | `onError` 콜백 호출 |
| JSON 파싱 실패 | 해당 이벤트 무시 (다음 이벤트 계속 처리) |
| 네트워크 끊김 | AbortError 무시, 기타 에러는 `onError` 호출 |
| 사용자 중단 | `AbortController.abort()` → fetch 취소 |
