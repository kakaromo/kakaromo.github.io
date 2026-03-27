---
title: Agent API
description: Android 디바이스 벤치마크, 시나리오, Trace REST API 엔드포인트
---

Base path: `/api/agent`

## Server CRUD

| Method | Path | 설명 |
|--------|------|------|
| GET | `/servers` | 서버 목록 |
| POST | `/servers` | 서버 추가 |
| PUT | `/servers/{id}` | 서버 수정 |
| DELETE | `/servers/{id}` | 서버 삭제 |
| POST | `/servers/{id}/test` | 기존 서버 접속 테스트 |
| POST | `/servers/test` | host:port 접속 테스트 |

### 요청 본문 (POST/PUT)

```json
{
  "name": "agent-1",
  "host": "192.168.1.100",
  "port": 50051,
  "enabled": true,
  "description": "메인 Agent 서버"
}
```

## Device Management

| Method | Path | 설명 |
|--------|------|------|
| GET | `/devices?serverId={id}` | 디바이스 목록 |
| POST | `/devices/{serial}/connect?serverId={id}` | TCP 디바이스 연결 |
| POST | `/devices/{serial}/disconnect?serverId={id}` | 디바이스 연결 해제 |

### 응답

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

## Benchmarking

| Method | Path | 설명 |
|--------|------|------|
| POST | `/benchmark/run?serverId={id}` | 벤치마크 실행 |
| GET | `/benchmark/status?serverId={id}&jobId={id}` | Job 상태 조회 |
| GET | `/benchmark/progress?serverId={id}&jobId={id}` | SSE 진행률 스트림 |
| GET | `/benchmark/result?serverId={id}&jobId={id}` | 결과 조회 |

### 벤치마크 실행 요청

```json
{
  "deviceIds": ["2-1.1.2"],
  "tool": "FIO",
  "params": {
    "bs": "4k",
    "rw": "randread",
    "size": "1g",
    "direct": "1",
    "iodepth": "32"
  },
  "jobName": "randread-4k-test"
}
```

### SSE 이벤트 (benchmark/progress)

```
event: progress
data: {"jobId":"abc","deviceId":"2-1.1.2","state":"running","progressPercent":45,"metrics":{...},"rawOutput":"..."}

event: complete
data: {}

event: error
data: {"error":"..."}
```

`metrics`와 `rawOutput`은 benchmark step 완료 시 포함됩니다.

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
    {"type": "benchmark", "tool": "BENCHMARK_TOOL_FIO", "params": {"rw": "write", "bs": "128k", "size": "1G", "trace": "on", "trace_type": "ufs"}},
    {"type": "benchmark", "tool": "BENCHMARK_TOOL_FIO", "params": {"rw": "randread", "bs": "4k", "runtime": "10", "use_file_from_step": "0"}},
    {"type": "cleanup", "params": {"delete_files_from_steps": "0"}}
  ],
  "loops": [{"startStep": 0, "endStep": 2, "count": 10}],
  "repeat": 1
}
```

### Step params 특수 키

| Key | 설명 |
|-----|------|
| `use_file_from_step` | 이전 step 파일 재사용 (0-based index) |
| `delete_files_from_steps` | cleanup: 특정 step 파일 삭제 (comma-separated) |
| `path` | cleanup: 경로 직접 삭제 |
| `trace` | `"on"` → 자동 trace start/stop |
| `trace_type` | `"ufs"` / `"block"` / `"both"` |

## Job Management

| Method | Path | 설명 |
|--------|------|------|
| DELETE | `/jobs/{jobId}?serverId={id}` | Job 삭제 (benchmark/scenario/trace 모두) |

시나리오 job 삭제 시 관련 trace job(parquet 파일)도 자동 삭제됩니다.

## I/O Trace

| Method | Path | 설명 |
|--------|------|------|
| POST | `/trace/start?serverId={id}` | Trace 시작 |
| POST | `/trace/{jobId}/stop?serverId={id}` | Trace 중지 |
| POST | `/trace/result?serverId={id}` | 통계 조회 (여러 job 합치기) |
| POST | `/trace/raw?serverId={id}` | Raw data 조회 (여러 job 합치기) |

### Trace 시작

```json
{
  "deviceId": "2-1.1.2",
  "traceType": "ufs",
  "windowSeconds": 0,
  "jobName": "trace-test"
}
```

### 통계/Raw 조회

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

### 통계 응답 구조

```json
{
  "stats": {
    "totalEvents": 500000,
    "durationSeconds": 30.5,
    "dtoc": {"min": 0.01, "max": 5.0, "avg": 0.23, "p99": 1.5, ...},
    "cmdStats": [{"cmd": "0x28", "count": 300000, "ratio": 0.6, "dtoc": {...}}],
    "latencyHistograms": [{"cmd": "0x28", "latencyType": "dtoc", "buckets": [...]}],
    "cmdSizeCounts": [{"cmd": "0x28", "size": 4096, "count": 250000}],
    "continuousRatio": 0.85,
    "alignedRatio": 0.99
  }
}
```

## Screen Streaming (WebSocket)

| Protocol | Path | 설명 |
|----------|------|------|
| WebSocket | `/api/agent/screen/{deviceId}?serverId={id}` | 디바이스 화면 스트리밍 |

### Binary 메시지 (Server → Client)

H.264 Annex B 프레임 (scrcpy 패킷 헤더 제거됨). JMuxer 등으로 디코딩.

### Text 메시지 (Server → Client)

```json
{"type": "info", "device": "2-1.1.2", "serial": "R3CN...", "width": 1080, "height": 2340, "name": "Galaxy S24", "message": "scrcpy session started"}
```

### Text 메시지 (Client → Server)

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

## Monitoring (SSE)

| Method | Path | 설명 |
|--------|------|------|
| GET | `/monitoring/stream?serverId={id}&deviceIds={ids}&interval={sec}` | 실시간 모니터링 |

타임아웃 없음 (유저가 중지할 때까지 유지).

## Scenario Templates

| Method | Path | 설명 |
|--------|------|------|
| GET | `/scenario-templates` | 템플릿 목록 |
| POST | `/scenario-templates` | 템플릿 생성 |
| PUT | `/scenario-templates/{id}` | 템플릿 수정 |
| DELETE | `/scenario-templates/{id}` | 템플릿 삭제 |
| POST | `/scenario-templates/{id}/duplicate` | 템플릿 복제 |

## Benchmark Presets

| Method | Path | 설명 |
|--------|------|------|
| GET | `/benchmark-presets` | 프리셋 목록 |
| POST | `/benchmark-presets` | 프리셋 생성 |
| PUT | `/benchmark-presets/{id}` | 프리셋 수정 |
| DELETE | `/benchmark-presets/{id}` | 프리셋 삭제 |
