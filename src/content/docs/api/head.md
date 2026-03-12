---
title: Head & Slots API
description: Head 서버 실시간 슬롯 상태 SSE 스트림, 명령 전송, 연결 관리 API
---

Head API는 Head TCP 서버와의 실시간 통신을 제공합니다. SSE 스트림으로 슬롯 상태를 수신하고, REST API로 테스트 명령을 전송합니다.

## SSE 스트림

### GET `/api/head/slots/stream`

Server-Sent Events 스트림으로 실시간 슬롯 상태를 수신합니다.

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `source` | string | 선택 | `compatibility` 또는 `performance`. 생략 시 전체 |

**이벤트 유형:**

| 이벤트 | 설명 |
|--------|------|
| `init` | 연결 시 전체 슬롯 초기 상태 |
| `update` | 슬롯 상태 변경 발생 시 |

**응답 데이터:**

```json
{
  "slots": [
    {
      "source": "compatibility",
      "slotIndex": 0,
      "modelName": "Galaxy S24",
      "battery": "85",
      "testState": "running",
      "setLocation": "T1-0",
      "trName": "FW_v1.2",
      "runningState": "Testing"
    }
  ],
  "version": 42,
  "connections": [
    { "name": "compatibility", "connected": true, "testMode": false }
  ]
}
```

:::tip
SSE 스트림은 버전 기반 최적화를 사용합니다. 클라이언트별 `lastVersion`을 추적하여 변경이 없으면 데이터를 푸시하지 않습니다.
:::

### GET `/api/head/slots`

슬롯 상태 스냅샷을 일회성으로 반환합니다. SSE 없이 현재 상태만 필요할 때 사용합니다.

---

## 명령 전송

### POST `/api/head/command`

Head 서버에 테스트 명령을 전송합니다.

**요청 Body:**

```json
{
  "source": "compatibility",
  "command": "test",
  "slotNumbers": [0, 1, 2],
  "data": "",
  "newOrder": null,
  "currentOrder": null
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `source` | string | 연결 이름 (compatibility, performance 등) |
| `command` | string | 명령어 종류 |
| `slotNumbers` | number[] | 대상 슬롯 번호 배열 |
| `data` | string | 명령에 필요한 추가 데이터 |
| `newOrder` | string | reordertest 시 새로운 순서 |
| `currentOrder` | string | reordertest 시 현재 순서 |

### 명령어 종류

| command | 설명 | 필수 필드 |
|---------|------|----------|
| `settr` | TR 설정 | `slotNumbers`, `data` (trId) |
| `settc` | TC 설정 | `slotNumbers`, `data` (tcData) |
| `initslot` | 슬롯 초기화 | `slotNumbers` |
| `test` | 테스트 시작 | `slotNumbers` |
| `stop` | 테스트 중지 | `slotNumbers` |
| `reordertest` | TC 순서 변경 | `slotNumbers`, `newOrder`, `currentOrder` |
| `disconnect` | 연결 해제 | `slotNumbers` |

**응답:**

```json
{
  "success": true,
  "message": "Command sent to 3 slots"
}
```

---

## 연결 관리

### GET `/api/head/connections`

Head TCP 연결 목록을 반환합니다.

- **Admin**: 모든 연결 (testMode 포함)
- **일반 사용자**: testMode 연결 제외

**응답:**

```json
[
  {
    "name": "compatibility",
    "connected": true,
    "testMode": false,
    "host": "192.168.1.248",
    "port": 10001
  }
]
```

### POST `/api/head/reconnect/{source}`

특정 source의 TCP 연결을 재시작합니다.

| 파라미터 | 설명 |
|----------|------|
| `source` | 연결 이름 (compatibility, performance 등) |

### POST `/api/head/reconnect`

모든 Head TCP 연결을 재시작합니다.
