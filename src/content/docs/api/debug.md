---
title: Debug API
description: DLM 추출, 바이너리 다운로드, MinIO 업로드 등 디버깅 관련 API
---

Debug API는 슬롯 디바이스의 디버깅 기능을 제공합니다. 현재 DLM(Device Log Memory) 추출 및 관련 파일 관리 기능을 포함합니다.

## DLM

### POST `/api/debug/dlm/execute`

슬롯의 디바이스에서 DLM 바이너리를 추출합니다. VM에 SSH 접속하여 adb push → shell → pull을 순차 실행합니다.

**요청 Body:**

```json
{
  "tentacleName": "T50",
  "slotNumber": 1,
  "serial": "0123456789ABCDEF",
  "testToolName": "perf_tool"
}
```

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `tentacleName` | string | VM(텐타클) 이름 |
| `slotNumber` | int | 슬롯 번호 |
| `serial` | string | 디바이스 USB 시리얼 (HeadSlotData의 `usbId`) |
| `testToolName` | string | 테스트 도구 이름 |

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
| `POST /api/debug/dlm/execute` | `ADMIN` |
| `GET /api/debug/dlm/download` | 인증된 사용자 |
| `POST /api/debug/dlm/upload-minio` | `ADMIN` |
