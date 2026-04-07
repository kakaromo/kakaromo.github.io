---
title: Pre-Command API
description: 사전 명령어 템플릿 CRUD, 슬롯/TC 등록, SSE 실행 API 레퍼런스
---

## 템플릿 CRUD

### GET `/api/pre-commands`

전체 템플릿 목록 반환.

### POST `/api/pre-commands`

새 템플릿 생성.

```json
{ "name": "tiotest 설치", "description": "...", "commands": "[\"adb push tiotest /dev\"]" }
```

### PUT `/api/pre-commands/{id}`

템플릿 수정.

### DELETE `/api/pre-commands/{id}`

템플릿 삭제. `pre_command_id` FK는 SET NULL 처리.

---

## 슬롯 등록 관리

### GET `/api/pre-commands/slots?setLocation={setLocation}`

슬롯별 등록 현황. `setLocation`은 접두사 매칭으로 필터링합니다 (예: `T3`이면 T3-0, T3-1, ... 모두 반환).

```json
[{ "setLocation": "T3-0", "preCommandId": 3, "preCommandName": "tiotest 설치" }]
```

### POST `/api/pre-commands/slots/assign`

```json
{ "preCommandId": 3, "setLocations": ["T3-0", "T3-1", "T3-3"] }
```

### POST `/api/pre-commands/slots/unassign`

```json
{ "setLocations": ["T3-0", "T3-1", "T3-3"] }
```

슬롯 Pre-Command 해제. TC Pre-Command가 남아 있으면 행은 유지(`pre_command_id` = NULL).

---

## TC 등록 관리

TC Pre-Command는 `tc_pre_command_ids` (comma 구분 문자열)로 관리됩니다. `testcaseIds`와 position이 1:1 대응합니다.

### GET `/api/pre-commands/tc?setLocation={setLocation}`

해당 슬롯의 TC Pre-Command 현황.

```json
{ "tcPreCommandIds": "0,3,0,5,0" }
```

- `0`: 미등록
- 숫자: `portal_pre_commands.id`

### POST `/api/pre-commands/tc/assign`

특정 position에 Pre-Command 등록.

```json
{ "preCommandId": 3, "setLocation": "T3-0", "tcPosition": 2 }
```

**동작:** `tc_pre_command_ids`의 position 2를 `3`으로 변경.
```
변경 전: "0,3,0,5,0"
변경 후: "0,3,3,5,0"
              ↑ position 2
```

### POST `/api/pre-commands/tc/unassign`

특정 position의 Pre-Command 해제.

```json
{ "setLocation": "T3-0", "tcPosition": 2 }
```

**동작:** 해당 position을 `0`으로 변경. 슬롯 Pre-Command도 없고 모든 TC가 0이면 행 삭제.

### POST `/api/pre-commands/tc/sync`

TC 순서 변경 시 `tc_pre_command_ids`를 재배열.

```json
{ "setLocation": "T3-0", "tcPreCommandIds": "5,3,0" }
```

프론트에서 TC 순서를 변경한 후, 같은 순서로 재배열된 문자열을 전달합니다.

---

## SSE 실행

### POST `/api/pre-commands/execute`

즉시 실행. SSE 스트림으로 진행 상황 반환.

```json
{ "preCommandId": 3, "setLocations": ["T3-0", "T3-1", "T3-3"] }
```

### SSE 이벤트

| 이벤트 | 설명 | 데이터 |
|--------|------|--------|
| `start` | 실행 시작 | `{totalSlots, totalCommands, preCommandName}` |
| `slot-skip` | 슬롯 건너뜀 | `{slotIndex, slotLabel, reason}` |
| `slot-start` | 슬롯 실행 시작 | `{slotIndex, slotLabel, usbId, vmName, commandCount}` |
| `cmd-start` | 명령어 시작 | `{slotIndex, cmdIndex, command}` |
| `cmd-done` | 명령어 완료 | `{slotIndex, cmdIndex, command, status, exitCode, output}` |
| `slot-done` | 슬롯 완료 | `{slotIndex, slotLabel, status}` |
| `summary` | 진행 요약 | `{total, completed, failed, skipped}` |
| `done` | 전체 완료 | `{total, failed, skipped, success}` |
| `error` | 예외 발생 | `{message}` |

---

## 엔드포인트 요약

```
REST API Base: /api/pre-commands

CRUD:
  GET    /                         전체 템플릿 목록
  GET    /{id}                     템플릿 상세
  POST   /                         템플릿 생성
  PUT    /{id}                     템플릿 수정
  DELETE /{id}                     템플릿 삭제

슬롯 등록:
  GET    /slots?setLocation=X      슬롯별 등록 현황
  POST   /slots/assign             슬롯에 등록
  POST   /slots/unassign           슬롯 해제

TC 등록:
  GET    /tc?setLocation=X         TC Pre-Command 현황 (comma 문자열)
  POST   /tc/assign                position에 등록
  POST   /tc/unassign              position 해제
  POST   /tc/sync                  TC 순서 변경 시 동기화

실행:
  POST   /execute (SSE)            즉시 실행
```
