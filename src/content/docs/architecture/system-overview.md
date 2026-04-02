---
title: 시스템 개요
description: Samsung Portal의 프로젝트 목적, 핵심 기능, 배포 모델, 전체 구성도 및 설계 결정 근거를 설명합니다.
---

## 프로젝트 목적

Samsung Portal은 **UFS(Universal Flash Storage) 테스트 자동화 관리 시스템**입니다. 호환성/성능 테스트의 전체 생명주기를 관리하고, 실시간 슬롯 모니터링, 원격 접속, 로그 분석, 바이너리 디버깅 도구까지 통합 제공합니다.

## 핵심 기능

| 영역 | 기능 | 설명 |
|------|------|------|
| **테스트 관리** | 호환성/성능 테스트 | TestRequest → TestCase → History 3계층 CRUD |
| **성능 시각화** | 15종 파서별 차트 | ECharts 기반 동적 시각화 + Excel Export |
| **성능 비교** | Chart Overlay / Delta Table | Baseline 대비 수치 비교 |
| **실시간 모니터링** | 슬롯 상태 | HEAD TCP → SSE 실시간 푸시 |
| **원격 접속** | SSH/RDP 터미널 | Guacamole WebSocket 터널 + 다중 탭 |
| **로그 분석** | 로그 브라우저 | SSH/Local 모드 파일 탐색 + ripgrep 검색 |
| **파일 관리** | S3 스토리지 | MinIO 브라우저 (업로드/다운로드/폴더 관리) |
| **개발 도구** | Binary Struct Mapper | C/C++ 구조체 → 바이너리 매핑 |
| **TC 그룹** | TC 조합 관리 | 자주 사용하는 TC 조합 저장/빠른 적용 |

## 배포 모델

**단일 JAR 배포** — SvelteKit 프론트엔드 빌드 결과물을 Spring Boot `static/` 디렉토리에 포함하여 별도 웹서버 없이 하나의 JAR로 서빙합니다.

:::tip
Maven 빌드 시 프론트엔드 빌드가 자동으로 실행되며, 결과물이 `src/main/resources/static/`에 복사됩니다.
:::

## 전체 구성도

```
┌──────────────────────────────────────────────────────────────────────┐
│                       Browser (SvelteKit 5 SPA)                      │
│                                                                      │
│  ┌────────────┐ ┌────────────┐ ┌──────────┐ ┌──────────┐ ┌────────┐│
│  │ Dashboard  │ │ TestDB     │ │ Remote   │ │ Storage  │ │DevTools││
│  │ 통계 차트   │ │ Compat/Perf│ │ SSH/RDP  │ │ (MinIO)  │ │BinMap  ││
│  │            │ │ Slots/Sets │ │          │ │ 파일관리  │ │PerfGen ││
│  └────────────┘ └────────────┘ └──────────┘ └──────────┘ └────────┘│
└──────────────────────────────────────────────────────────────────────┘
                              │
                 REST API / SSE / WebSocket
                              │
┌──────────────────────────────────────────────────────────────────────┐
│                    Spring Boot 4.0.2 Backend                         │
│                                                                      │
│  ┌───────────────┐  ┌──────────────┐  ┌────────────────────────────┐│
│  │ REST API      │  │ Head TCP     │  │ Guacamole WebSocket        ││
│  │ Controllers   │  │ Client       │  │ Tunnel Endpoint            ││
│  │ (23개)        │  │ (듀얼 소켓)   │  │                            ││
│  └───────┬───────┘  └──────┬───────┘  └────────────┬───────────────┘│
│          │                 │                        │                │
│     ┌────┴────┐       ┌────┴────┐                   │                │
│     │ Redis   │       │ State   │                   │                │
│     │ Cache   │       │ State   │                   │                │
│     └─────────┘       └─────────┘                   │                │
└──────────┬─────────────┬───────────────┬────────────┼────────────────┘
           │             │               │            │
           ▼             ▼               ▼            ▼
┌──────────────┐ ┌──────────────┐ ┌────────────┐ ┌──────────────┐
│   MySQL      │ │  Head        │ │ MinIO S3   │ │    guacd     │
│              │ │  Server      │ │ Storage    │ │   (4822)     │
│ - testdb     │ │ (10001,     │ │ (9000)     │ └──────┬───────┘
│ - UFSInfo    │ │  10030)     │ └────────────┘        │
│ - binmapper  │ └──────────────┘                      ▼
└──────────────┘                              ┌──────────────────┐
       │                                      │  Tentacle 서버    │
       ▼                                      │  T1, T2, T3, T4  │
┌──────────────┐                              │  HEAD (SSH/RDP)  │
│ Go Excel     │                              └──────────────────┘
│ Service      │
│ (gRPC:50052) │
└──────────────┘
```

## 통신 프로토콜

| 경로 | 프로토콜 | 용도 |
|------|----------|------|
| Browser ↔ Backend | HTTP REST + SSE + WebSocket | API, 실시간 슬롯, 원격 터미널 |
| Backend ↔ Head Server | TCP (커스텀 프로토콜) | 하드웨어 테스트 제어 |
| Backend ↔ Go Excel Service | gRPC (protobuf) | Excel 파일 생성 |
| Backend ↔ guacd | Guacamole 프로토콜 (TCP) | SSH/RDP 중계 |
| Backend ↔ MinIO | S3 API (HTTP) | 오브젝트 스토리지 |
| Backend ↔ MySQL | JDBC | 데이터 영속화 |
| Backend ↔ Redis | Redis 프로토콜 | 캐시 |
| Backend ↔ Tentacle (SSH) | SSH (JSch) | 로그 파일 원격 접근 |

## 설계 결정 근거

| 설계 결정 | 이유 |
|-----------|------|
| **단일 JAR 배포** | 프론트엔드를 Maven 빌드 시 `static/`에 복사하여 Spring Boot가 서빙. 별도 웹서버 불필요 |
| **Multi-DataSource (3개 DB)** | testdb(레거시 공유), UFSInfo(참조코드 공유), binmapper(Portal 전용) 각각 독립적 |
| **Head TCP 듀얼 소켓** | 레거시 Head 서버 프로토콜이 명령 전송과 상태 수신을 분리 |
| **SSE (Server-Sent Events)** | 슬롯 상태 실시간 푸시. WebSocket 대비 단방향이라 단순, 자동 재연결 |
| **Guacamole 터널** | guacd 통해 SSH/RDP를 웹으로 중계. 별도 VPN 없이 원격 접속 |
| **Go Excel Service (gRPC)** | 네이티브 Excel 차트 생성에 excelize 필요. Java에서 직접 처리 어려움 |
| **Redis 캐시 (JDK 직렬화)** | Hibernate 프록시 문제 회피. 동일 엔티티 반복 조회 최적화 |
| **SvelteKit 5 SPA** | Svelte 5 Runes로 명시적 반응성, 작은 번들, SSR 불필요 |

## Performance Reparse 아키텍처

성능 TC의 로그 파일을 재파싱하여 결과 데이터를 새로 생성하는 시스템입니다. 파싱 로직이 업데이트되었거나 기존 파싱 결과에 오류가 있을 때 사용합니다.

### 전체 흐름

```
Browser                    Spring Boot                  Tentacle VM
  │                            │                            │
  │ POST /api/reparse/{id}     │                            │
  │ ──────────────────────>    │                            │
  │                            │  SSH 연결                   │
  │                            │ ──────────────────────>    │
  │                            │  parsingcontroller 실행     │
  │                            │  (로그 디렉토리 재파싱)       │
  │   SSE: init (job 목록)      │ <──────────────────────    │
  │ <──────────────────────    │  진행 상황 피드백             │
  │                            │                            │
  │   SSE: update (진행률)      │                            │
  │ <──────────────────────    │                            │
  │   SSE: update (완료)        │                            │
  │ <──────────────────────    │                            │
```

1. 프론트엔드에서 `POST /api/reparse/{historyId}` 요청
2. 백엔드가 SSH로 해당 Tentacle VM에 접속하여 `parsingcontroller` 명령 실행
3. 파싱 진행 상황을 SSE 스트림(`GET /api/reparse/stream`)으로 실시간 전달
4. 프론트엔드의 `reparseStore`가 SSE 이벤트를 수신하여 상태 관리

### ReparseJob 상태

| 상태 | 설명 |
|------|------|
| `preparing` | SSH 연결 및 파싱 명령 준비 중 |
| `running` | 파싱 진행 중 (파일별 진행률 추적) |
| `completed` | 파싱 완료 |
| `failed` | 파싱 실패 (에러 메시지 포함) |

### SSE 이벤트 구조

```
event: init
data: {"jobs": [{"jobId": "...", "historyId": 123, "state": "running", "totalFiles": 15, "currentIndex": 3, ...}]}

event: update
data: {"jobs": [...]}
```

- `init`: SSE 연결 시 현재 진행 중인 모든 reparse job 목록 전달
- `update`: job 상태 변경 시마다 전체 job 목록 전달

## 글로벌 Floating Card 아키텍처

장시간 실행되는 백그라운드 작업의 진행 상황을 화면 우하단의 **플로팅 카드**로 표시하는 패턴입니다. 사용자가 다른 페이지로 이동해도 진행 상황을 계속 확인할 수 있습니다.

### 구성 요소

| 컴포넌트 | 역할 |
|----------|------|
| `reparseStore` (Svelte 5 store) | 글로벌 상태 관리. SSE 연결, job 목록, localStorage 영속화 |
| `ReparseFloatingCard` | 화면 우하단 고정 UI. 진행 중/완료/실패 job 표시 |
| 루트 레이아웃 (`+layout.svelte`) | 앱 전역에서 Floating Card 렌더링 |

### 작동 방식

1. **앱 초기화**: `reparseStore.init()`이 localStorage를 확인하여 활성 job이 있으면 SSE 연결 자동 복원
2. **Reparse 시작**: `reparseStore.startReparse(historyId)` 호출 시 API 요청 + SSE 연결
3. **실시간 업데이트**: SSE `update` 이벤트 수신 시 job 목록 갱신 + localStorage 동기화
4. **자동 숨김**: 완료된 job은 60초 후, 실패한 job은 120초 후 자동으로 카드에서 사라짐
5. **수동 제거**: 사용자가 개별 job을 dismiss 가능
6. **접기/펼치기**: 카드 헤더를 클릭하여 축소/확장 전환 (축소 시에도 진행 바 표시)

### localStorage 영속화

활성 상태(`preparing`, `running`)의 job ID가 `reparse-active-jobs` 키로 localStorage에 저장됩니다. 브라우저를 새로고침하거나 탭을 닫았다가 다시 열어도, 활성 job이 있으면 SSE 연결이 자동 복원되어 진행 상황을 계속 추적합니다.

### SSE 재연결

SSE 연결이 끊어지면(네트워크 오류 등), localStorage에 활성 job이 남아 있는 경우 5초 후 자동 재연결을 시도합니다. 모든 job이 완료/실패하면 SSE 연결을 종료합니다.

## SSE 스트리밍 패턴

Portal에서는 여러 기능에 걸쳐 SSE(Server-Sent Events) 스트리밍 패턴이 사용됩니다. 각 용도별 특성을 정리합니다.

| 용도 | 엔드포인트 | 타임아웃 | 이벤트 타입 | 재연결 |
|------|-----------|----------|------------|--------|
| **슬롯 상태** | `/api/slots/stream` | 서버 설정 | `message` | EventSource 자동 |
| **Performance Reparse** | `/api/reparse/stream` | 없음 | `init`, `update` | 5초 후 수동 |
| **Agent 벤치마크 진행** | `/api/agent/benchmark/progress` | 없음 | `progress`, `complete`, `error` | 수동 |
| **Agent 디바이스 모니터링** | `/api/agent/monitoring/stream` | 없음 (0L) | `metrics` | 수동 |

### 공통 패턴

- 백엔드: `SseEmitter`로 이벤트 전송. 타임아웃 0L 설정 시 무제한 연결 유지
- 프론트엔드: `EventSource` API 또는 `fetch` + ReadableStream 사용
- 에러 처리: `onCompletion`, `onTimeout`, `onError` 콜백으로 정리 로직 실행
- 연결 관리: 페이지 이탈 시 `EventSource.close()` 또는 AbortController로 정리
