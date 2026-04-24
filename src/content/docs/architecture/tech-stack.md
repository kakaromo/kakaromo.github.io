---
title: 기술 스택
description: MOVE의 Backend, Frontend, 외부 시스템에 사용된 기술과 버전 정보를 정리합니다.
---

## Backend

| 기술 | 버전 | 용도 |
|------|------|------|
| Java | 17 | 런타임 |
| Spring Boot | 4.0.2 | 프레임워크 |
| Spring Data JPA | - | ORM (Multi-DataSource) |
| Spring Security | - | CSRF + 수동 ADFS OIDC Hybrid Flow (OAuth2 Client 미사용) |
| Spring WebSocket | - | Guacamole 터널 + Agent 화면 프록시 |
| Spring gRPC | 1.0.2 | Go Agent(:50051) · Go Excel(:50052) · Rust Trace(:50053) 3 서비스 연동 |
| Apache Arrow (JS) | - | `apache-arrow` 패키지 — Trace 차트의 IPC bytes 디코딩 |
| Redis | - | 캐시 |
| MySQL | - | testdb, UFSInfo, binmapper |
| MinIO SDK | 8.5.14 | S3 오브젝트 스토리지 |
| JSch | 0.2.21 | SSH 연결 (로그 브라우저) |
| Guacamole Common | 1.5.5 | 원격 접속 터널 |
| Protobuf | 4.34.0 | gRPC 메시지 직렬화 |
| gRPC | 1.77.1 | RPC 프레임워크 |
| Lombok | - | 보일러플레이트 제거 |

:::note
Protobuf 컴파일은 Maven 빌드 시 `protobuf-maven-plugin`에 의해 자동 실행됩니다. macOS에서는 `protocExecutable=/opt/homebrew/bin/protoc` 설정이 필요할 수 있습니다.
:::

## Frontend

| 기술 | 버전 | 용도 |
|------|------|------|
| SvelteKit | 5 | 프레임워크 (SPA 모드) |
| TypeScript | - | 타입 안전성 |
| TailwindCSS | v4 | 유틸리티 CSS |
| DaisyUI | v5 | UI 테마 (라이트/다크) |
| bits-ui (shadcn-svelte) | - | UI 컴포넌트 |
| TanStack Table | v8 | 데이터 테이블 |
| ECharts | 6.0.0 | 성능 차트 |
| ExcelJS | 4.4.0 | Excel Export (dynamic import) |
| Lucide | - | 아이콘 (이모지 교체 후 일괄 적용) |
| guacamole-common-js | 1.5.0 | 원격 터미널 클라이언트 |
| @xyflow/svelte | - | Agent 시나리오 캔버스 (DAG + Loop/Condition) |
| apache-arrow | - | Trace 차트의 Arrow IPC 디코딩 (컬럼 지향 TypedArrays) |
| @deck.gl/core, @deck.gl/layers | - | Trace WebGL 렌더러 (opt-in, `VITE_TRACE_RENDERER=deckgl`) |
| paneforge | - | 분할 패널 (Trace/Agent 3-pane 레이아웃) |

:::tip
Svelte 5 Runes(`$state`, `$derived`, `$effect`, `$props`)를 사용하여 명시적 반응성을 구현합니다. SPA 모드로 빌드하여 Spring Boot가 정적 파일을 서빙하므로 서버사이드 렌더링은 불필요합니다.
:::

## 외부 시스템

| 시스템 | 포트 | 용도 |
|--------|------|------|
| Head Server | 10001, 10030 | 하드웨어 테스트 제어 (TCP 듀얼 소켓) |
| guacd | 4822 | 원격 프로토콜 데몬 (Guacamole Daemon) |
| MinIO | 9000 | S3 호환 파일 스토리지 (+ Trace 서비스가 직접 range-GET) |
| **Go Agent Service** | **50051** | gRPC 기반 Android ADB/벤치마크/trace/매크로 (`~/project/agent`) |
| **Go Excel Service** | **50052** | gRPC 기반 네이티브 Excel 생성 (`~/project/excel-service`, `excelize/v2`) |
| **Rust Trace Service** | **50053** | gRPC + parquet + Arrow IPC (`~/project/trace`, `arrow`/`parquet`/`rayon`) |
| Redis | 6379 | 캐시 서버 |
| ADFS (Samsung SSO) | HTTPS | OIDC Hybrid Flow 수동 구현 |

:::caution
외부 gRPC 서비스 3개(Agent:50051, Excel:50052, Trace:50053) 중 **사용 기능에 해당하는 서비스**가 기동되어 있어야 합니다. 예: Excel Export 시 50052, Trace Analysis 시 50053 필요. 없으면 해당 기능만 장애, 나머지는 정상.
:::
