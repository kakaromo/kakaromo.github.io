---
title: Debug API
description: Debug Types/Tools 조회, DLM 추출, 바이너리 다운로드, MinIO 업로드 등 디버깅 관련 API
---

Debug API는 슬롯 디바이스의 디버깅 기능을 제공합니다. Debug Types/Tools는 DB로 관리되며, 현재 DLM(Device Log Memory) 추출 기능이 구현되어 있습니다.

## Debug Types/Tools (Admin)

### GET `/api/admin/debug-types`

등록된 디버그 타입 목록을 조회합니다.

**응답:**

```json
[
  {
    "id": 1,
    "name": "DLM",
    "typeKey": "dlm",
    "enabled": true,
    "description": null,
    "createdAt": "2026-03-19T12:00:00",
    "updatedAt": "2026-03-19T12:00:00"
  }
]
```

### POST `/api/admin/debug-types`

새 디버그 타입을 생성합니다.

**요청 Body:**

```json
{
  "name": "DLM",
  "typeKey": "dlm",
  "enabled": true,
  "description": "Device Log Memory 디버그"
}
```

### PUT `/api/admin/debug-types/{id}`

디버그 타입을 수정합니다. 요청 Body는 POST와 동일합니다.

### DELETE `/api/admin/debug-types/{id}`

디버그 타입을 삭제합니다. 연결된 `debug_tools`도 CASCADE 삭제됩니다.

---

### GET `/api/admin/debug-tools`

등록된 디버그 툴 목록을 조회합니다.

**응답:**

```json
[
  {
    "id": 1,
    "typeId": 1,
    "typeName": "DLM",
    "toolName": "dlm_250106",
    "toolPath": "/home/octo/tentacle/apps",
    "description": "2025-01-06 빌드",
    "createdAt": "2026-03-19T12:00:00",
    "updatedAt": "2026-03-19T12:00:00"
  }
]
```

### POST `/api/admin/debug-tools`

새 디버그 툴을 생성합니다.

**요청 Body:**

```json
{
  "typeId": 1,
  "toolName": "dlm_250106",
  "toolPath": "/home/octo/tentacle/apps",
  "description": "2025-01-06 빌드"
}
```

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `typeId` | number | 소속 debug_types ID |
| `toolName` | string | 바이너리 파일명 |
| `toolPath` | string | VM 내 디렉토리 경로 |
| `description` | string | 설명 (선택) |

소스 경로 조합: `toolPath + "/" + toolName`

### PUT `/api/admin/debug-tools/{id}`

디버그 툴을 수정합니다. 요청 Body는 POST와 동일합니다.

### DELETE `/api/admin/debug-tools/{id}`

디버그 툴을 삭제합니다.

---

## DLM

### GET `/api/debug/dlm/tools`

`type_key = "dlm"`인 디버그 툴 목록을 조회합니다. DLM 다이얼로그의 Tool 드롭다운에 사용됩니다.

**응답:**

```json
[
  {
    "id": 1,
    "toolName": "dlm_250106",
    "toolPath": "/home/octo/tentacle/apps",
    "description": "2025-01-06 빌드"
  }
]
```

---

### POST `/api/debug/dlm/execute`

슬롯의 디바이스에서 DLM 바이너리를 추출합니다. VM에 SSH 접속하여 adb push → shell → pull을 순차 실행합니다.

**요청 Body:**

```json
{
  "tentacleName": "T50",
  "slotNumber": 1,
  "serial": "0123456789ABCDEF",
  "testToolName": "perf_tool",
  "toolId": 1
}
```

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `tentacleName` | string | VM(텐타클) 이름 |
| `slotNumber` | int | 슬롯 번호 |
| `serial` | string | 디바이스 USB 시리얼 (HeadSlotData의 `usbId`) |
| `testToolName` | string | 테스트 도구 이름 |
| `toolId` | long | 사용할 debug_tools ID |

**응답:**

```json
{
  "fileName": "T501-perf_tool-20260319.bin",
  "filePath": "/home/octo/tentacle/dlm/T501-perf_tool-20260319.bin",
  "stdout": "dlm output content..."
}
```

| 필드 | 설명 |
|------|------|
| `fileName` | 생성된 파일명 (`{tentacleName}{slotNum}-{testToolName}-{date}.bin`) |
| `filePath` | VM 내 파일 절대 경로 |
| `stdout` | DLM 실행 stdout 출력 |

---

### GET `/api/debug/dlm/download`

VM에서 DLM 바이너리 파일을 SFTP로 스트리밍 다운로드합니다.

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `tentacleName` | string (query) | VM 이름 |
| `filePath` | string (query) | VM 내 파일 절대 경로 |

**응답:** `application/octet-stream` (Content-Disposition: attachment)

---

### POST `/api/debug/dlm/upload-minio`

VM의 DLM 파일을 MinIO `dlm` 버킷에 업로드합니다. 버킷이 없으면 자동 생성합니다.

**요청 Body:**

```json
{
  "tentacleName": "T50",
  "filePath": "/home/octo/tentacle/dlm/T501-perf_tool-20260319.bin",
  "fileName": "T501-perf_tool-20260319.bin"
}
```

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `tentacleName` | string | VM 이름 |
| `filePath` | string | VM 내 파일 절대 경로 |
| `fileName` | string | MinIO에 저장할 오브젝트 이름 |

**응답:**

```json
{
  "objectName": "T501-perf_tool-20260319.bin"
}
```

---

## 인증

| 엔드포인트 | 권한 |
|------------|------|
| `GET/POST/PUT/DELETE /api/admin/debug-types` | `ADMIN` |
| `GET/POST/PUT/DELETE /api/admin/debug-tools` | `ADMIN` |
| `GET /api/debug/dlm/tools` | 인증된 사용자 |
| `POST /api/debug/dlm/execute` | `ADMIN` |
| `GET /api/debug/dlm/download` | 인증된 사용자 |
| `POST /api/debug/dlm/upload-minio` | `ADMIN` |
