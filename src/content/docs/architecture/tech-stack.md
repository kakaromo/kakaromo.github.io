---
title: 기술 스택
description: Samsung Portal의 Backend, Frontend, 외부 시스템에 사용된 기술과 버전 정보를 정리합니다.
---

## Backend

| 기술 | 버전 | 용도 |
|------|------|------|
| Java | 17 | 런타임 |
| Spring Boot | 4.0.2 | 프레임워크 |
| Spring Data JPA | - | ORM (Multi-DataSource) |
| Spring Security | - | 인증/인가 (OAuth2 + CSRF) |
| Spring WebSocket | - | Guacamole 터널 |
| Spring gRPC | 1.0.2 | Excel Service 연동 |
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
| Lucide | - | 아이콘 |
| guacamole-common-js | 1.5.0 | 원격 터미널 클라이언트 |

:::tip
Svelte 5 Runes(`$state`, `$derived`, `$effect`, `$props`)를 사용하여 명시적 반응성을 구현합니다. SPA 모드로 빌드하여 Spring Boot가 정적 파일을 서빙하므로 서버사이드 렌더링은 불필요합니다.
:::

## 외부 시스템

| 시스템 | 포트 | 용도 |
|--------|------|------|
| Head Server | 10001, 10030 | 하드웨어 테스트 제어 (TCP) |
| guacd | 4822 | 원격 프로토콜 데몬 (Guacamole Daemon) |
| MinIO | 9000 | S3 호환 파일 스토리지 |
| Go Excel Service | 50052 | gRPC 기반 네이티브 Excel 생성 |
| Redis | 6379 | 캐시 서버 |
| Samsung Galaxy SSO | HTTPS | OAuth2/OIDC 인증 (ADFS) |

:::caution
Go Excel Service가 50052 포트에서 먼저 실행되어 있어야 Excel Export 기능이 동작합니다.
:::
