---
title: Agent API
description: Android 디바이스 벤치마크, 시나리오, Trace, 매크로, 스케줄링 REST API 전체 레퍼런스
---

Base path: `/api/agent`

모든 요청/응답은 JSON (`Content-Type: application/json`). SSE 엔드포인트는 `text/event-stream`.

---

## Server CRUD

| Method | Path | 설명 |
|--------|------|------|
| GET | `/servers` | 서버 목록 조회 |
| POST | `/servers` | 서버 추가 |
| PUT | `/servers/{id}` | 서버 수정 |
| DELETE | `/servers/{id}` | 서버 삭제 |
| POST | `/servers/{id}/test` | 기존 서버 접속 테스트 |
| POST | `/servers/test` | host:port 접속 테스트 (서버 저장 전 확인용) |

### 요청 본문 (POST/PUT `/servers`)

```json
{
  "name": "agent-1",
  "host": "192.168.1.100",
  "port": 50051,
  "enabled": true,
  "description": "메인 Agent 서버"
}
```

### 응답 (서버 객체)

```json
{
  "id": 1,
  "name": "agent-1",
  "host": "192.168.1.100",
  "port": 50051,
  "enabled": true,
  "description": "메인 Agent 서버",
  "connectionState": "READY",
  "connected": true,
  "createdAt": "2026-01-15T10:30:00",
  "updatedAt": "2026-01-15T10:30:00"
}
```

### 접속 테스트 (POST `/servers/test`)

```json
// 요청
{ "host": "192.168.1.100", "port": 50051 }

// 응답
{ "success": true, "message": "연결 성공" }
```

---

## Connection Status

| Method | Path | 설명 |
|--------|------|------|
| GET | `/servers/{id}/status` | 서버 gRPC 연결 상태 조회 |
| POST | `/servers/{id}/reconnect` | 서버 gRPC 재연결 |

### 상태 조회 응답

```json
{
  "serverId": 1,
  "state": "READY",
  "connected": true,
  "host": "192.168.1.100",
  "port": 50051
}
```

`state` 값: `READY`, `IDLE`, `CONNECTING`, `TRANSIENT_FAILURE`, `SHUTDOWN`

### 재연결 응답

```json
{
  "success": true,
  "state": "READY",
  "message": "재연결 성공"
}
```

---

## Device Management

| Method | Path | 설명 |
|--------|------|------|
| GET | `/devices?serverId={id}` | 디바이스 목록 |
| POST | `/devices/{serial}/connect?serverId={id}` | TCP 디바이스 연결 |
| POST | `/devices/{serial}/disconnect?serverId={id}` | 디바이스 연결 해제 |

### 디바이스 목록 응답

```json
{
  "devices": [
    {
      "deviceId": "2-1.1.2",
      "serial": "R3CN...",
      "state": "online",
      "androidVersion": "14",
      "model": "Galaxy S24"
    }
  ]
}
```

### 연결/해제 응답

```json
{ "success": true, "message": "연결 성공" }
```

---

## Benchmarking

| Method | Path | 설명 |
|--------|------|------|
| POST | `/benchmark/run?serverId={id}` | 벤치마크 실행 |
| GET | `/benchmark/status?serverId={id}&jobId={id}` | Job 상태 조회 |
| GET | `/benchmark/progress?serverId={id}&jobId={id}` | SSE 진행률 스트림 |
| GET | `/benchmark/result?serverId={id}&jobId={id}` | 결과 조회 |

### 벤치마크 실행 요청 (POST `/benchmark/run`)

```json
{
  "deviceIds": ["2-1.1.2"],
  "tool": "FIO",
  "params": {
    "bs": "4k",
    "rw": "randread",
    "size": "1g",
    "direct": "1",
    "iodepth": "32",
    "numjobs": "4",
    "runtime": "60"
  },
  "jobName": "randread-4k-test"
}
```

`tool` 값: `FIO`, `IOZONE`, `TIOTEST`

### 실행 응답

```json
{ "jobId": "abc-123-def" }
```

### 상태 조회 응답 (GET `/benchmark/status`)

```json
{
  "jobId": "abc-123-def",
  "state": "running",
  "progressPercent": 45
}
```

`state` 값: `pending`, `running`, `completed`, `failed`, `cancelled`

### SSE 이벤트 (GET `/benchmark/progress`)

```
event: progress
data: {"jobId":"abc","deviceId":"2-1.1.2","state":"running","progressPercent":45,"metrics":{...},"rawOutput":"..."}

event: complete
data: {}

event: error
data: {"error":"..."}
```

`metrics`와 `rawOutput`은 benchmark step 완료 시 포함됩니다.

### 결과 조회 응답 (GET `/benchmark/result`)

```json
{
  "jobId": "abc-123-def",
  "results": [
    {
      "deviceId": "2-1.1.2",
      "tool": "FIO",
      "metrics": { "iops": 150000, "bw_kbps": 600000, "lat_avg_us": 26.5 },
      "rawOutput": "..."
    }
  ]
}
```

---

## Scenario

| Method | Path | 설명 |
|--------|------|------|
| POST | `/scenario/run?serverId={id}` | 시나리오 실행 |

### 시나리오 실행 요청

```json
{
  "deviceIds": ["2-1.1.2"],
  "scenarioName": "seq-write-read",
  "steps": [
    {
      "type": "benchmark",
      "tool": "BENCHMARK_TOOL_FIO",
      "params": {
        "rw": "write", "bs": "128k", "size": "1G",
        "trace": "on", "trace_type": "ufs"
      }
    },
    {
      "type": "benchmark",
      "tool": "BENCHMARK_TOOL_FIO",
      "params": {
        "rw": "randread", "bs": "4k", "runtime": "10",
        "use_file_from_step": "0"
      }
    },
    { "type": "sleep", "params": { "seconds": "5" } },
    { "type": "cleanup", "params": { "delete_files_from_steps": "0" } }
  ],
  "loops": [{ "startStep": 0, "endStep": 2, "count": 10 }],
  "repeat": 1,
  "conditions": [
    {
      "stepIndex": 1,
      "field": "iops",
      "operator": "lt",
      "value": "50000",
      "action": "skip_remaining"
    }
  ]
}
```

### Step Types

| Type | 설명 | 주요 params |
|------|------|-------------|
| `benchmark` | 벤치마크 실행 | `tool`, `rw`, `bs`, `size` 등 |
| `shell` | 셸 명령어 실행 | `cmd` |
| `cleanup` | 파일 삭제 | `delete_files_from_steps`, `path` |
| `sleep` | 대기 | `seconds` |
| `trace_start` | Trace 시작 | `trace_type` (`ufs`/`block`/`both`) |
| `trace_stop` | Trace 중지 | (자동으로 이전 trace_start 매칭) |

### Step params 특수 키

| Key | 설명 |
|-----|------|
| `use_file_from_step` | 이전 step이 생성한 파일 재사용 (0-based index) |
| `delete_files_from_steps` | cleanup: 특정 step 파일 삭제 (comma-separated) |
| `path` | cleanup: 경로 직접 삭제 |
| `trace` | `"on"` 설정 시 자동 trace start/stop |
| `trace_type` | `"ufs"` / `"block"` / `"both"` |

### 실행 응답

```json
{ "jobId": "scenario-abc-123" }
```

---

## Job Management

| Method | Path | 설명 |
|--------|------|------|
| DELETE | `/jobs/{jobId}?serverId={id}` | Job 삭제 |
| POST | `/jobs/{jobId}/cancel?serverId={id}` | Job 취소 (실행 중인 Job 중지) |

### 삭제 응답

```json
{ "success": true, "message": "Job deleted" }
```

시나리오 job 삭제 시 관련 trace job(parquet 파일)도 자동 삭제됩니다.

### 취소 응답

```json
{ "success": true, "message": "Job cancelled" }
```

---

## I/O Trace

| Method | Path | 설명 |
|--------|------|------|
| POST | `/trace/start?serverId={id}` | Trace 시작 |
| POST | `/trace/{jobId}/stop?serverId={id}` | Trace 중지 |
| POST | `/trace/{jobId}/reparse?serverId={id}` | Trace 데이터 재파싱 |
| POST | `/trace/result?serverId={id}` | 통계 조회 (여러 job 합치기 가능) |
| POST | `/trace/raw?serverId={id}` | Raw data 조회 (여러 job 합치기 가능) |

### Trace 시작 요청

```json
{
  "deviceId": "2-1.1.2",
  "traceType": "ufs",
  "windowSeconds": 0,
  "jobName": "trace-test"
}
```

`traceType`: `"ufs"`, `"block"`, `"both"`
`windowSeconds`: 0이면 수동 중지까지 계속, 양수면 해당 초 후 자동 중지

### Trace 시작 응답

```json
{ "jobId": "trace-abc-123" }
```

### Trace 중지 응답

```json
{ "success": true, "message": "Trace stopped" }
```

### Trace 재파싱 (POST `/trace/{jobId}/reparse`)

수집된 raw trace 데이터를 다시 파싱합니다. 파싱 로직 변경 후 기존 데이터를 재처리할 때 사용합니다.

```json
// 응답
{ "success": true, "message": "Reparse started" }
```

### 통계/Raw 조회 요청

```json
{
  "jobIds": ["abc-123", "def-456"],
  "filter": {
    "startTime": 1.5,
    "endTime": 10.2,
    "startLba": 0,
    "endLba": 1000000,
    "minDtoc": 0.01,
    "maxDtoc": 5.0,
    "minQd": 1,
    "maxQd": 64,
    "cmdList": ["0x28", "0x2a"],
    "sizeList": [4096, 8192]
  },
  "latencyRangesMs": [0.1, 0.5, 1, 5, 10, 50, 100]
}
```

여러 `jobIds`를 전달하면 데이터를 합쳐서 분석합니다. `filter`는 선택 사항이며, brush 드래그 등으로 범위를 좁힐 때 사용합니다.

### 통계 응답 구조

```json
{
  "jobId": "merged",
  "stats": {
    "totalEvents": 500000,
    "durationSeconds": 30.5,
    "dtoc": { "min": 0.01, "max": 5.0, "avg": 0.23, "p50": 0.15, "p90": 0.8, "p99": 1.5, "p999": 3.2 },
    "ctod": { "min": 0.001, "max": 0.5, "avg": 0.02, ... },
    "ctoc": { "min": 0.01, "max": 2.0, "avg": 0.06, ... },
    "qd": { "min": 1, "max": 64, "avg": 16.3, ... },
    "cmdStats": [
      { "cmd": "0x28", "cmdName": "Read(10)", "count": 300000, "ratio": 0.6, "dtoc": {...}, "ctod": {...} }
    ],
    "latencyHistograms": [
      { "cmd": "0x28", "latencyType": "dtoc", "buckets": [{ "rangeMs": "0-0.1", "count": 50000 }, ...] }
    ],
    "cmdSizeCounts": [
      { "cmd": "0x28", "size": 4096, "count": 250000 }
    ],
    "continuousRatio": 0.85,
    "alignedRatio": 0.99
  }
}
```

### Raw 데이터 응답 구조

```json
{
  "events": [
    {
      "time": 1.523,
      "lba": 123456,
      "qd": 8,
      "dtoc": 0.23,
      "ctod": 0.01,
      "ctoc": 0.06,
      "cmd": "0x2a",
      "size": 4096,
      "continuous": true,
      "action": "C"
    }
  ]
}
```

---

## MinIO Upload

| Method | Path | 설명 |
|--------|------|------|
| POST | `/upload/trace?serverId={id}` | Trace 데이터를 MinIO에 업로드 |
| POST | `/upload/benchmark?serverId={id}` | 벤치마크 결과를 MinIO에 업로드 |

### Trace 업로드 요청

```json
{
  "jobIds": ["abc-123", "def-456"],
  "remotePath": "traces/2026-04-03/"
}
```

### Benchmark 업로드 요청

```json
{
  "jobId": "bench-abc-123",
  "remotePath": "benchmarks/2026-04-03/"
}
```

### 업로드 응답

```json
{
  "success": true,
  "message": "Uploaded 3 files",
  "uploadedFiles": ["file1.parquet", "file2.parquet", "file3.json"]
}
```

---

## Monitoring (SSE)

| Method | Path | 설명 |
|--------|------|------|
| GET | `/monitoring/stream?serverId={id}&deviceIds={ids}&interval={sec}` | 실시간 모니터링 |

타임아웃 없음 (사용자가 중지할 때까지 유지). `interval`은 데이터 수집 간격(초, 기본 5).

### SSE 이벤트

```
event: metrics
data: {
  "deviceId": "2-1.1.2",
  "timestamp": "2026-04-03T10:30:00",
  "cpu": { "usage": 45.2, "cores": [...] },
  "memory": { "totalMb": 8192, "usedMb": 3456, "usagePercent": 42.1 },
  "disk": { "readKbps": 12000, "writeKbps": 8500, "iops": 450 }
}
```

---

## Screen Streaming (WebSocket)

| Protocol | Path | 설명 |
|----------|------|------|
| WebSocket | `/api/agent/screen/{deviceId}?serverId={id}` | 디바이스 화면 스트리밍 |

### Binary 메시지 (Server -> Client)

H.264 Annex B 프레임 (scrcpy 패킷 헤더 제거됨). JMuxer 등으로 디코딩.

### Text 메시지 (Server -> Client)

```json
{
  "type": "info",
  "device": "2-1.1.2",
  "serial": "R3CN...",
  "width": 1080,
  "height": 2340,
  "name": "Galaxy S24",
  "message": "scrcpy session started"
}
```

### Text 메시지 (Client -> Server)

```json
// 터치
{"type": "touch", "touch": {"action": 0, "x": 0.5, "y": 0.3, "width": 1080, "height": 2340, "pressure": 1.0, "pointer_id": 0}}

// 키 입력
{"type": "key", "key": {"action": 0, "keycode": 3, "repeat": 0, "meta_state": 0}}

// 스크롤
{"type": "scroll", "scroll": {"x": 0.5, "y": 0.5, "width": 1080, "height": 2340, "h_scroll": 0, "v_scroll": -1}}

// Back 버튼
{"type": "back"}

// 디코더 재초기화 요청 (시트 재오픈 시)
{"type": "requestSync"}
```

action: 0=down, 1=up, 2=move. 좌표는 0~1 정규화.

---

## Scenario Templates

| Method | Path | 설명 |
|--------|------|------|
| GET | `/scenario-templates` | 템플릿 목록 |
| POST | `/scenario-templates` | 템플릿 생성 |
| PUT | `/scenario-templates/{id}` | 템플릿 수정 |
| DELETE | `/scenario-templates/{id}` | 템플릿 삭제 |
| POST | `/scenario-templates/{id}/duplicate` | 템플릿 복제 |

### 요청 본문 (POST/PUT)

```json
{
  "name": "seq-write-read-template",
  "description": "Sequential write 후 random read",
  "stepsJson": "[{\"type\":\"benchmark\",...}]",
  "loopsJson": "[{\"startStep\":0,\"endStep\":2,\"count\":10}]",
  "conditionsJson": "[]"
}
```

### 응답 (템플릿 객체)

```json
{
  "id": 1,
  "name": "seq-write-read-template",
  "description": "Sequential write 후 random read",
  "stepsJson": "[...]",
  "loopsJson": "[...]",
  "conditionsJson": "[]",
  "createdAt": "2026-01-15T10:30:00",
  "updatedAt": "2026-01-15T10:30:00"
}
```

---

## Benchmark Presets

| Method | Path | 설명 |
|--------|------|------|
| GET | `/benchmark-presets` | 프리셋 목록 |
| POST | `/benchmark-presets` | 프리셋 생성 |
| PUT | `/benchmark-presets/{id}` | 프리셋 수정 |
| DELETE | `/benchmark-presets/{id}` | 프리셋 삭제 |

### 요청 본문 (POST/PUT)

```json
{
  "name": "4k-randread-standard",
  "description": "4K Random Read 표준 테스트",
  "tool": "FIO",
  "paramsJson": "{\"bs\":\"4k\",\"rw\":\"randread\",\"size\":\"1g\",\"direct\":\"1\",\"iodepth\":\"32\"}"
}
```

### 응답 (프리셋 객체)

```json
{
  "id": 1,
  "name": "4k-randread-standard",
  "description": "4K Random Read 표준 테스트",
  "tool": "FIO",
  "paramsJson": "{...}",
  "createdAt": "2026-01-15T10:30:00",
  "updatedAt": "2026-01-15T10:30:00"
}
```

---

## App Macros

| Method | Path | 설명 |
|--------|------|------|
| GET | `/app-macros` | 매크로 목록 |
| GET | `/app-macros/{id}` | 매크로 상세 조회 |
| POST | `/app-macros` | 매크로 생성 |
| PUT | `/app-macros/{id}` | 매크로 수정 |
| DELETE | `/app-macros/{id}` | 매크로 삭제 |
| POST | `/app-macros/{id}/duplicate` | 매크로 복제 |

### 요청 본문 (POST/PUT)

```json
{
  "name": "앱 실행 후 스크롤",
  "description": "카메라 앱 열고 아래로 스크롤",
  "packageName": "com.samsung.android.camera",
  "eventsJson": "[{\"t\":0,\"type\":\"tap\",\"x\":540,\"y\":1200},{\"t\":500,\"type\":\"swipe\",\"x\":540,\"y\":1200,\"x2\":540,\"y2\":600,\"duration\":300}]",
  "deviceWidth": 1080,
  "deviceHeight": 2400
}
```

### 응답 (매크로 객체)

```json
{
  "id": 1,
  "name": "앱 실행 후 스크롤",
  "description": "카메라 앱 열고 아래로 스크롤",
  "packageName": "com.samsung.android.camera",
  "eventsJson": "[...]",
  "deviceWidth": 1080,
  "deviceHeight": 2400,
  "createdAt": "2026-01-15T10:30:00",
  "updatedAt": "2026-01-15T10:30:00"
}
```

### 이벤트 JSON 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| `t` | long | 이벤트 발생 시점 (ms, 시작점 기준 상대값) |
| `type` | string | 이벤트 타입: `tap`, `swipe`, `long_press`, `key`, `wait`, `scroll_find`, `ocr_read` |
| `x`, `y` | int | 터치 좌표 (픽셀) |
| `x2`, `y2` | int | swipe 종료 좌표 |
| `duration` | int | swipe/long_press 지속 시간 (ms) |
| `keycode` | int | key 이벤트용 Android keycode |
| `seconds` | int | wait 대기 시간 (초) |
| `waitMethod` | string | `ocr`, `ui_element` 등 대기 조건 |
| `waitPattern` | string | 대기 조건 패턴 문자열 |
| `timeout` | int | 대기 타임아웃 (ms) |
| `pollInterval` | int | 대기 조건 폴링 간격 (ms) |
| `name` | string | 이벤트 라벨/설명 |
| `direction` | string | scroll 방향 (`up`/`down`/`left`/`right`) |
| `maxScrolls` | int | scroll_find 최대 스크롤 횟수 |
| `scrollPause` | int | scroll_find 스크롤 간 대기 (ms) |
| `ocrPattern` | string | OCR 인식 패턴 (regex) |
| `ocrRegion` | object | OCR 영역: `{ x, y, width, height }` |

---

## Macro Recording / Replay / OCR

| Method | Path | 설명 |
|--------|------|------|
| GET | `/macro/installed-apps?serverId={id}&deviceId={id}` | 설치된 앱 목록 |
| POST | `/macro/start-recording?serverId={id}` | 이벤트 녹화 시작 |
| POST | `/macro/stop-recording?serverId={id}` | 이벤트 녹화 중지 + 이벤트 반환 |
| POST | `/macro/replay?serverId={id}` | 매크로 재생 |
| POST | `/macro/screenshot?serverId={id}` | 스크린샷 촬영 |
| POST | `/macro/ocr?serverId={id}` | 스크린샷 + OCR 텍스트 인식 |

### 설치된 앱 목록 응답

```json
[
  { "packageName": "com.samsung.android.camera", "appName": "Camera" },
  { "packageName": "com.android.chrome", "appName": "Chrome" }
]
```

### 녹화 시작 요청

```json
{ "deviceId": "2-1.1.2" }
```

### 녹화 시작 응답

```json
{ "success": true, "sessionId": "rec-abc-123" }
```

### 녹화 중지 요청

```json
{ "deviceId": "2-1.1.2", "sessionId": "rec-abc-123" }
```

### 녹화 중지 응답

```json
{
  "success": true,
  "deviceWidth": 1080,
  "deviceHeight": 2400,
  "events": [
    { "t": 0, "type": "tap", "x": 540, "y": 1200 },
    { "t": 500, "type": "swipe", "x": 540, "y": 1200, "x2": 540, "y2": 600, "duration": 300 }
  ]
}
```

### 매크로 재생 요청

```json
{
  "deviceId": "2-1.1.2",
  "events": [
    { "t": 0, "type": "tap", "x": 540, "y": 1200 },
    { "t": 500, "type": "swipe", "x": 540, "y": 1200, "x2": 540, "y2": 600, "duration": 300 }
  ],
  "sourceWidth": 1080,
  "sourceHeight": 2400,
  "jobId": "optional-job-id"
}
```

`sourceWidth`/`sourceHeight`는 이벤트가 녹화된 해상도. 디바이스 해상도가 다르면 자동으로 좌표를 변환합니다.

### 매크로 재생 응답

```json
{
  "success": true,
  "message": "Replay completed",
  "ocrResults": { "step_3_ocr": "인식된 텍스트" },
  "metrics": { "totalDurationMs": 5200, "eventsExecuted": 12 }
}
```

### 스크린샷 요청

```json
{ "deviceId": "2-1.1.2" }
```

### 스크린샷 응답

```json
{
  "success": true,
  "width": 1080,
  "height": 2340,
  "imageBase64": "/9j/4AAQ..."
}
```

### OCR 요청

```json
{
  "deviceId": "2-1.1.2",
  "extractPattern": "\\d+\\.\\d+%",
  "region": {
    "x": 100,
    "y": 200,
    "width": 400,
    "height": 100
  }
}
```

`extractPattern`은 선택 사항. 설정 시 전체 OCR 텍스트에서 해당 regex에 매칭되는 값만 추출합니다. `region`도 선택 사항이며, 지정하면 해당 영역만 OCR 수행합니다.

### OCR 응답

```json
{
  "success": true,
  "fullText": "Battery: 85.3%\nTemperature: 32C",
  "extractedValue": "85.3%",
  "imageBase64": "/9j/4AAQ..."
}
```

---

## Job Executions

Base path: `/api/agent/executions`

벤치마크/시나리오/trace 실행 이력을 서버 DB에 영속적으로 저장합니다.

| Method | Path | 설명 |
|--------|------|------|
| GET | `/` | 실행 이력 목록 (필터/페이징) |
| GET | `/{id}` | ID로 조회 |
| GET | `/by-job-id/{jobId}` | Job ID로 조회 |
| DELETE | `/{id}` | 이력 삭제 |
| GET | `/stats` | 통계 조회 |

### 목록 조회 파라미터 (GET `/`)

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `serverId` | Long | X | 서버 ID 필터 |
| `type` | String | X | 타입 필터: `benchmark`, `scenario`, `trace` |
| `state` | String | X | 상태 필터: `running`, `completed`, `failed` |
| `from` | ISO DateTime | X | 시작일시 (예: `2026-04-01T00:00:00`) |
| `to` | ISO DateTime | X | 종료일시 |
| `page` | int | X | 페이지 번호 (기본 0) |
| `size` | int | X | 페이지 크기 (기본 30) |

### 목록 응답

```json
{
  "content": [
    {
      "id": 1,
      "jobId": "abc-123",
      "serverId": 1,
      "serverName": "agent-1",
      "type": "benchmark",
      "tool": "FIO",
      "jobName": "randread-4k-test",
      "deviceIds": "[\"2-1.1.2\"]",
      "state": "completed",
      "config": "{...}",
      "resultSummary": "{...}",
      "scheduledJobId": null,
      "retryAttempt": 0,
      "errorMessage": null,
      "startedAt": "2026-04-03T10:30:00",
      "completedAt": "2026-04-03T10:31:05",
      "createdAt": "2026-04-03T10:30:00"
    }
  ],
  "totalElements": 156,
  "totalPages": 6,
  "page": 0,
  "size": 30
}
```

### 통계 응답 (GET `/stats`)

```json
{
  "total": 156,
  "byType": { "benchmark": 80, "scenario": 50, "trace": 26 },
  "byState": { "completed": 140, "failed": 10, "running": 6 },
  "todayCount": 12
}
```

`serverId` 쿼리 파라미터로 특정 서버의 통계만 조회할 수 있습니다.

---

## Scheduled Jobs

Base path: `/api/agent/schedules`

Cron 기반 자동 실행 스케줄을 관리합니다.

| Method | Path | 설명 |
|--------|------|------|
| GET | `/` | 스케줄 목록 |
| GET | `/{id}` | 스케줄 상세 |
| POST | `/` | 스케줄 생성 |
| PUT | `/{id}` | 스케줄 수정 |
| DELETE | `/{id}` | 스케줄 삭제 |
| POST | `/{id}/trigger` | 즉시 실행 (수동 트리거) |
| POST | `/{id}/enable` | 활성/비활성 토글 |

### 요청 본문 (POST/PUT)

```json
{
  "name": "매일 4K 랜덤읽기 벤치마크",
  "description": "매일 02시에 자동 실행",
  "enabled": true,
  "type": "benchmark",
  "serverId": 1,
  "deviceIds": "[\"2-1.1.2\"]",
  "config": "{\"tool\":\"FIO\",\"params\":{\"bs\":\"4k\",\"rw\":\"randread\"}}",
  "cronExpression": "0 0 2 * * ?",
  "busyPolicy": "reject",
  "retryCount": 3,
  "retryDelaySeconds": 60,
  "notifyOnFailure": true,
  "notifyOnSuccess": false,
  "notifyWebhookUrl": "https://hooks.slack.com/..."
}
```

| 필드 | 설명 |
|------|------|
| `type` | `benchmark`, `scenario`, `trace` |
| `busyPolicy` | `reject` (스킵), `queue` (대기열) |
| `cronExpression` | Spring Cron 표현식 (6자리: 초 분 시 일 월 요일) |
| `retryCount` | 실패 시 재시도 횟수 |
| `retryDelaySeconds` | 재시도 간 대기 시간 (초) |

### 응답 (스케줄 객체)

```json
{
  "id": 1,
  "name": "매일 4K 랜덤읽기 벤치마크",
  "description": "매일 02시에 자동 실행",
  "enabled": true,
  "type": "benchmark",
  "serverId": 1,
  "deviceIds": "[\"2-1.1.2\"]",
  "config": "{...}",
  "cronExpression": "0 0 2 * * ?",
  "busyPolicy": "reject",
  "retryCount": 3,
  "retryDelaySeconds": 60,
  "notifyOnFailure": true,
  "notifyOnSuccess": false,
  "notifyWebhookUrl": "https://hooks.slack.com/...",
  "lastRunAt": "2026-04-03T02:00:00",
  "lastRunStatus": "completed",
  "nextRunAt": "2026-04-04T02:00:00",
  "createdAt": "2026-01-15T10:30:00",
  "updatedAt": "2026-04-03T02:01:05"
}
```

### 즉시 실행 응답 (POST `/{id}/trigger`)

```json
{ "success": true, "jobId": "triggered-abc-123" }
```

### 활성 토글 응답 (POST `/{id}/enable`)

스케줄 객체 전체를 반환합니다 (`enabled` 필드가 토글된 상태).
