---
title: Pre-Command API
description: 사전 명령어 템플릿 CRUD, 슬롯 등록, SSE 실행 API 레퍼런스
---

Pre-Command API는 명령어 템플릿 관리, 슬롯별 등록, 즉시 실행 기능을 제공합니다.

## 템플릿 CRUD

### GET `/api/pre-commands`

전체 템플릿 목록을 반환합니다.

**응답:**
```json
[
  {
    "id": 1,
    "name": "tiotest 설치",
    "description": "tiotest 바이너리를 디바이스에 배포",
    "commands": "[\"adb push tiotest-0.52 /dev\",\"adb shell chmod +x /dev/tiotest-0.52\"]",
    "createdAt": "2026-04-04T15:00:00",
    "updatedAt": "2026-04-04T15:00:00"
  }
]
```

### GET `/api/pre-commands/{id}`

특정 템플릿을 반환합니다.

### POST `/api/pre-commands`

새 템플릿을 생성합니다.

**요청:**
```json
{
  "name": "tiotest 설치",
  "description": "tiotest 바이너리를 디바이스에 배포",
  "commands": "[\"adb push tiotest-0.52 /dev\",\"adb shell chmod +x /dev/tiotest-0.52\"]"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| name | string | O | 템플릿 이름 (최대 100자) |
| description | string | X | 설명 (최대 500자) |
| commands | string | O | 명령어 JSON 배열 문자열 |

### PUT `/api/pre-commands/{id}`

기존 템플릿을 수정합니다. 요청 형식은 POST와 동일합니다.

### DELETE `/api/pre-commands/{id}`

템플릿을 삭제합니다. 해당 템플릿에 등록된 슬롯 매핑도 CASCADE 삭제됩니다.

**응답:**
```json
{ "success": true }
```

---

## 슬롯 등록 관리

### GET `/api/pre-commands/slots`

특정 source의 슬롯별 등록 현황을 조회합니다.

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| source | string | O | `compatibility` 또는 `performance` |

**응답:**
```json
[
  {
    "slotIndex": 0,
    "preCommandId": 1,
    "preCommandName": "tiotest 설치"
  },
  {
    "slotIndex": 3,
    "preCommandId": 2,
    "preCommandName": "fio 설치"
  }
]
```

### POST `/api/pre-commands/slots/assign`

선택한 슬롯들에 Pre-Command를 등록합니다. 이미 등록된 슬롯은 새 템플릿으로 교체됩니다.

**요청:**
```json
{
  "preCommandId": 1,
  "source": "compatibility",
  "slotNumbers": [0, 1, 3, 5]
}
```

**응답:**
```json
{ "success": true, "count": 4 }
```

### POST `/api/pre-commands/slots/unassign`

선택한 슬롯들의 등록을 해제합니다.

**요청:**
```json
{
  "source": "compatibility",
  "slotNumbers": [0, 1, 3, 5]
}
```

**응답:**
```json
{ "success": true }
```

---

## SSE 실행

### POST `/api/pre-commands/execute`

선택한 슬롯들에 Pre-Command를 즉시 실행하고, SSE 스트림으로 진행 상황을 반환합니다.

**Content-Type:** `text/event-stream`

**요청:**
```json
{
  "preCommandId": 1,
  "source": "compatibility",
  "slotNumbers": [0, 1, 3]
}
```

### SSE 이벤트

#### `start`

실행 시작.

```json
{
  "totalSlots": 3,
  "totalCommands": 2,
  "preCommandName": "tiotest 설치"
}
```

#### `slot-skip`

슬롯 검증 실패로 건너뜀.

```json
{
  "slotIndex": 1,
  "slotLabel": "T1-S01",
  "reason": "슬롯 데이터 없음"
}
```

#### `slot-start`

슬롯 실행 시작.

```json
{
  "slotIndex": 0,
  "slotLabel": "T1-S00",
  "usbId": "usb:9-1.4.1",
  "vmName": "T1",
  "commandCount": 2
}
```

#### `cmd-start`

명령어 실행 시작.

```json
{
  "slotIndex": 0,
  "cmdIndex": 0,
  "command": "adb -s usb:9-1.4.1 push tiotest-0.52 /dev"
}
```

#### `cmd-done`

명령어 실행 완료.

```json
{
  "slotIndex": 0,
  "cmdIndex": 0,
  "command": "adb -s usb:9-1.4.1 push tiotest-0.52 /dev",
  "status": "success",
  "exitCode": 0,
  "output": "tiotest-0.52: 1 file pushed."
}
```

| status | 설명 |
|--------|------|
| `success` | exit code 0 |
| `failed` | exit code ≠ 0 |
| `error` | SSH 접속 실패 등 예외 |

#### `slot-done`

슬롯의 모든 명령어 실행 완료.

```json
{
  "slotIndex": 0,
  "slotLabel": "T1-S00",
  "status": "success"
}
```

#### `summary`

진행 상황 요약 (슬롯 하나 완료될 때마다 전송).

```json
{
  "total": 3,
  "completed": 1,
  "failed": 0,
  "skipped": 0
}
```

#### `done`

전체 실행 완료.

```json
{
  "total": 3,
  "failed": 0,
  "skipped": 1,
  "success": 2
}
```

#### `error`

실행 중 예외 발생.

```json
{
  "message": "에러 메시지"
}
```
