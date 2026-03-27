---
title: Agent 아키텍처
description: Android 디바이스 평가 시스템의 gRPC 연동 및 내부 설계
---

## 시스템 구성

```
┌─────────────┐     gRPC (50051)     ┌──────────────┐     ADB/USB     ┌──────────┐
│   Portal    │ ◄──────────────────► │  Go Agent    │ ◄──────────────► │ Android  │
│ (Spring Boot)│  WebSocket (screen) │  Server      │   scrcpy (H.264)│ Devices  │
└─────────────┘ ◄──────────────────► └──────────────┘                  └──────────┘
      │                                    │
   MySQL 3307                        ftrace / fio
  (portal DB)                        iozone / tiotest
```

## 백엔드 패키지 구조

```
com.samsung.portal.agent
├── controller/
│   └── AgentController.java        # REST API (/api/agent/*)
├── endpoint/
│   └── AgentScreenEndpoint.java    # WebSocket 프록시 (화면 스트리밍)
├── entity/
│   ├── AgentServer.java            # gRPC 서버 관리
│   ├── BenchmarkPreset.java        # 벤치마크 프리셋
│   └── ScenarioTemplate.java       # 시나리오 템플릿
├── repository/
│   ├── AgentServerRepository.java
│   ├── BenchmarkPresetRepository.java
│   └── ScenarioTemplateRepository.java
├── service/
│   ├── AgentServerService.java
│   ├── BenchmarkPresetService.java
│   └── ScenarioTemplateService.java
└── grpc/
    ├── AgentGrpcClient.java         # gRPC stub wrapper (blocking + async)
    └── AgentConnectionManager.java  # 서버별 동적 채널 관리
```

## gRPC 연동

### Proto 정의

`src/main/proto/device_agent.proto` — Go agent의 `proto/agent.proto`와 동일한 메시지 정의에 Java option만 추가.

```proto
option java_package = "com.samsung.portal.agent.proto";
option java_outer_classname = "DeviceAgentProto";
```

### 동적 채널 관리

Excel 서비스는 `GrpcChannelFactory`로 정적 채널을 사용하지만, Agent는 여러 서버를 DB로 관리하므로 **동적 채널**을 사용합니다.

```java
// AgentConnectionManager
ConcurrentHashMap<Long, AgentGrpcClient> clients;

// 서버 ID로 client 가져오기 (없으면 생성)
AgentGrpcClient getOrCreate(Long serverId, String host, int port);

// 접속 테스트 (임시 채널)
boolean testConnection(String host, int port);
```

`maxInboundMessageSize`는 256MB로 설정 (trace raw data 전송용).

### SSE 브릿지

gRPC 서버 스트리밍 → SSE로 변환하여 프론트엔드에 전달합니다.

- **벤치마크 진행**: `SubscribeJobProgress` → SSE `/api/agent/benchmark/progress`
- **모니터링**: `MonitorDevices` → SSE `/api/agent/monitoring/stream`

`AtomicBoolean` 가드로 emitter가 닫힌 후 send 시도를 방지합니다.

### 화면 스트리밍 (WebSocket 프록시)

scrcpy H.264 비디오를 WebSocket으로 릴레이합니다.

```
Browser ◄─ WS ─► AgentScreenEndpoint ◄─ WS ─► Go Agent ◄─ scrcpy ─► Android
         (Spring WebSocket proxy)        (/ws/screen/{deviceId})
```

- **AgentScreenEndpoint** (`@ServerEndpoint("/api/agent/screen/{deviceId}")`): 브라우저 ↔ Agent 양방향 프록시
- **Go Agent handler**: scrcpy 패킷 헤더(12B) 파싱 → 순수 H.264 데이터만 전송
- **config/keyframe 캐시**: SPS/PPS + IDR 프레임 저장, `requestSync` 메시지로 재전송 (시트 재오픈 시 즉시 화면 표시)
- **입력 릴레이**: JSON text 메시지 (touch, key, scroll, back) → scrcpy control protocol 변환

## 데이터베이스

portal datasource (MySQL 3307)에 3개 테이블:

### portal_agent_servers

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | 자동 증가 |
| name | VARCHAR(100) UNIQUE | 서버 이름 |
| host | VARCHAR(100) | gRPC 서버 호스트 |
| port | INT | gRPC 포트 (기본 50051) |
| enabled | BOOLEAN | 활성화 여부 |

### portal_scenario_templates

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | 자동 증가 |
| name | VARCHAR(200) | 템플릿 이름 |
| repeat_count | INT | 전체 반복 횟수 |
| steps_json | TEXT | JSON: [{type, tool, params}] |
| loops_json | TEXT | JSON: [{startStep, endStep, count}] |

### portal_benchmark_presets

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | 자동 증가 |
| name | VARCHAR(200) | 프리셋 이름 |
| tool | VARCHAR(20) | FIO / IOZONE / TIOTEST |
| params_json | TEXT | JSON: {key: value} |

## 프론트엔드 구조

### 3패널 레이아웃

토스 UX 철학 적용: 서버/디바이스를 한 번 선택하면 모든 기능에 공유.

```
┌─────────────────────────────────────────────┐
│ LEFT (w-60)    │ CENTER (flex-1)            │
│ ContextPanel   │ Mode별 컴포넌트             │
│                │                            │
│ Server ▼       │ BenchmarkForm              │
│ Devices ☑☐     │ ScenarioBuilder            │
│ Quick Actions  │ TraceForm                  │
│                │ ResultsView                │
├────────────────┴────────────────────────────┤
│              FloatingJobCard (fixed)        │
└─────────────────────────────────────────────┘
```

### 상태 관리

- **전역 상태** (`+page.svelte`): servers, selectedServerId, devices, selectedDeviceIds, centerMode, activeJobs, jobHistory, activeTraceJobId
- **localStorage 영속**: selectedServerId (`agent:lastServerId`), jobHistory (`agent:jobHistory`, 최대 100건)
- **페이지 레벨 SSE**: 모니터링 연결은 시트가 아닌 페이지에서 관리 (시트 닫아도 유지)
- **Trace job 복구**: 새로고침 시 Trace job은 SSE 대신 `GetJobStatus`로 상태만 확인하여 `activeTraceJobId` 복원 (벤치마크 SSE와 분리)

### Trace Scatter Chart

`TraceScatterChart.svelte` — ECharts wrapper에 brush + legend 동기화 추가:

- **brush**: rect 선택 → X+Y 범위 추출 → filter에 반영
- **legend 동기화**: 여러 차트의 cmd 표시 상태를 `legendSelected` state로 공유
- **대용량**: `large: true`, `progressive: 5000` 자동 설정

### CMD 색상 매핑

| 계열 | 키워드/Opcode | 색상 |
|------|-------------|------|
| Read | read, rd, 0x28, 0x88 | 파란색 계열 |
| Write | write, wr, 0x2a, 0x8a | 주황색 계열 |
| Flush | flush, sync, 0x35, 0x91 | 초록색 계열 |
| Discard | discard, trim, unmap, 0x42 | 보라색 계열 |
| Other | 기타 | 회색 계열 |
