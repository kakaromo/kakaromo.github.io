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
│     │ Cache   │       │ Store   │                   │                │
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
