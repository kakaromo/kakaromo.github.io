---
title: 프로젝트 구조
description: Portal 프로젝트의 디렉토리 구조와 주요 모듈 설명
---

## 전체 구조

```
portal/
├── docs/                              # 프로젝트 문서
├── frontend/                          # SvelteKit 5 프론트엔드
│   ├── src/
│   │   ├── lib/
│   │   │   ├── api/                  # API 클라이언트
│   │   │   ├── components/           # UI 컴포넌트
│   │   │   ├── stores/               # Svelte 5 상태 스토어
│   │   │   ├── utils/                # 유틸리티
│   │   │   └── types/                # TypeScript 타입
│   │   └── routes/                   # 페이지 라우트
│   └── package.json
├── src/main/
│   ├── java/com/samsung/portal/
│   │   ├── config/                   # 설정
│   │   │   ├── datasource/          #   DataSource 3개 (portal, testdb, ufsinfo)
│   │   │   └── websocket/           #   WebSocket 설정
│   │   ├── auth/                     # 인증 + 엔티티
│   │   ├── head/                     # Head TCP 클라이언트 + SSE
│   │   │   ├── config/              #   HeadConnectionProperties
│   │   │   ├── entity/              #   HeadConnection, HeadSlotData
│   │   │   ├── repository/          #   JPA 리포지토리
│   │   │   ├── service/             #   연결, 파싱, 상태, 이미지 업로드
│   │   │   ├── tcp/                 #   HeadTcpClient, HeadConnectionManager
│   │   │   └── controller/          #   REST/SSE 컨트롤러
│   │   ├── guacamole/               # Guacamole 원격 접속 (RDP)
│   │   │   ├── config/              #   GuacamoleProperties
│   │   │   ├── dto/                 #   VmInfo
│   │   │   ├── endpoint/            #   WebSocket 엔드포인트
│   │   │   ├── service/             #   서비스
│   │   │   └── controller/          #   REST 컨트롤러
│   │   ├── terminal/                # xterm.js SSH 터미널
│   │   ├── testdb/                  # TestDB 도메인 (entity/repo/service/controller)
│   │   ├── ufsinfo/                 # UFSInfo 도메인
│   │   ├── logbrowser/              # 로그 브라우저 (SSH/Local)
│   │   ├── binmapper/               # BinMapper 엔진
│   │   ├── tcgroup/                 # TC Group
│   │   ├── minio/                   # MinIO 스토리지
│   │   └── PortalApplication.java
│   ├── resources/
│   │   ├── application.yaml
│   │   └── static/                  # 빌드된 프론트엔드
│   └── proto/
│       └── excel_service.proto      # gRPC 서비스 정의
├── sql/                              # DB 스키마 SQL
├── pom.xml
└── CLAUDE.md
```

## 백엔드 패키지

| 패키지 | 역할 | 하위 패키지 |
|--------|------|------------|
| `config` | 설정 | `datasource/` (3개 DB), `websocket/`, Security, Redis |
| `auth` | 인증/사용자 | `AuthController`, `PortalUser` |
| `head` | Head TCP 통신 | `config/`, `entity/`, `repository/`, `service/`, `tcp/`, `controller/` |
| `guacamole` | 원격 접속 (RDP) | `config/`, `dto/`, `endpoint/`, `service/`, `controller/` |
| `terminal` | SSH 터미널 (xterm.js) | `SshTerminalEndpoint`, `SshConnectionService`, `SshConnectionInfo` |
| `testdb` | 테스트 관리 | `entity/`, `repository/`, `service/`, `controller/`, `excel/` |
| `ufsinfo` | 참조 데이터 | 7개 엔티티 × 4계층 (entity/repo/service/controller) |
| `logbrowser` | 로그 탐색 | `SshLogBrowserService`, `LocalLogBrowserService` |
| `binmapper` | 바이너리 매핑 | `CppStructLexer`, `CppStructParser`, `BinaryReaderService` |
| `tcgroup` | TC 그룹 관리 | `TcGroupService`, `TcGroupController` |
| `minio` | 파일 스토리지 | `MinioStorageService`, `MinioController` |

## 프론트엔드 라우트

| 경로 | 페이지 | 설명 |
|------|--------|------|
| `/` | 대시보드 | 통계 차트, 최근 테스트 |
| `/testdb/compatibility` | 호환성 테스트 | TR/TC/History 관리 |
| `/testdb/performance` | 성능 테스트 | TR/History 관리 + 차트 |
| `/testdb/performance/compare` | 성능 비교 | Overlay/SideBySide/Delta |
| `/testdb/slots` | 슬롯 모니터링 | 카드 UI + 명령 전송 |
| `/ufsinfo` | UFS 참조 데이터 | 7개 코드 테이블 |
| `/remote` | 원격 접속 | SSH/RDP 터미널 |
| `/storage` | 파일 스토리지 | MinIO 브라우저 |
| `/devtools/bin-mapper` | BinMapper | 바이너리 구조체 매핑 |

## 설정 파일

주요 설정은 `src/main/resources/application.yaml`에서 관리합니다.

```yaml title="application.yaml (요약)"
server:
  port: 8080

spring:
  datasource:
    testdb:
      url: jdbc:mysql://127.0.0.1:3306/testdb
    ufsinfo:
      url: jdbc:mysql://127.0.0.1:3306/UFSInfo
    binmapper:
      url: jdbc:mysql://127.0.0.1:3307/binmapper
  data:
    redis:
      host: 127.0.0.1
      port: 6379

head:
  reconnect-delay-ms: 5000
  sse:
    push-interval-ms: 1000

guacamole:
  guacd-host: 192.168.1.248
  guacd-port: 4822

minio:
  endpoint: http://192.168.1.248
  port: 9000
```
