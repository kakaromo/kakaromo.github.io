---
title: 인프라 구성
description: Samsung Portal의 서버 구성, 포트 매핑, 네트워크 다이어그램
---

Samsung Portal의 인프라 구성을 설명합니다.

## 서버 구성

| 서버 | 역할 | 포트 | 비고 |
|------|------|------|------|
| **Portal Server** | Spring Boot 웹 애플리케이션 | 8080 | REST API, SSE, WebSocket |
| **MySQL (testdb)** | 호환성/성능 테스트 데이터 | 3306 | testdb, UFSInfo 스키마 |
| **MySQL (portal)** | Portal 도구/사용자 데이터 | 3307 | binmapper 스키마 |
| **Redis** | 캐시 | 6379 | JDK Serialization |
| **Head Server** | 하드웨어 테스트 제어 | 10001, 10030 | TCP 듀얼 소켓 |
| **guacd** | Guacamole 원격 접속 데몬 | 4822 | SSH/RDP 프로토콜 변환 |
| **MinIO** | S3 호환 오브젝트 스토리지 | 9000 | 파일 관리 |
| **Go Excel Service** | Excel 차트 생성 | 50052 | gRPC 서버 |
| **Tentacle T1** | 테스트 디바이스 서버 | SSH 22, RDP 3389 | 디바이스 연결 |
| **Tentacle T2** | 테스트 디바이스 서버 | SSH 22, RDP 3389 | 디바이스 연결 |
| **Tentacle T3** | 테스트 디바이스 서버 | SSH 22, RDP 3389 | 디바이스 연결 |
| **Tentacle T4** | 테스트 디바이스 서버 | SSH 22, RDP 3389 | 디바이스 연결 |
| **HEAD** | Head 서버 (SSH 접속용) | SSH 22 | 로그 조회, 원격 접속 |

---

## 네트워크 다이어그램

```
┌─────────────────────────────────────────────────────────────┐
│                      Browser (SvelteKit SPA)                 │
│                                                              │
│  REST API    SSE (slots)    WebSocket (Guacamole)           │
└──────┬───────────┬──────────────┬────────────────────────────┘
       │           │              │
       ▼           ▼              ▼
┌──────────────────────────────────────────────────────────────┐
│                   Portal Server (:8080)                       │
│                                                              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────────┐ │
│  │ REST API │  │   SSE    │  │ WebSocket│  │ gRPC Client │ │
│  │Controller│  │Controller│  │  Tunnel  │  │(Excel Export)│ │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └──────┬──────┘ │
│       │              │              │               │        │
└───────┼──────────────┼──────────────┼───────────────┼────────┘
        │              │              │               │
   ┌────▼────┐   ┌─────▼─────┐  ┌────▼────┐   ┌─────▼──────┐
   │  MySQL  │   │   Head    │  │  guacd  │   │ Go Excel   │
   │:3306    │   │  Server   │  │  :4822  │   │  Service   │
   │:3307    │   │:10001     │  │         │   │  :50052    │
   └─────────┘   │:10030     │  └────┬────┘   └────────────┘
                 └───────────┘       │
   ┌─────────┐                  ┌────▼──────────────────────┐
   │  Redis  │                  │   Tentacle / HEAD 서버     │
   │  :6379  │                  │   T1~T4 (:22, :3389)      │
   └─────────┘                  │   HEAD  (:22)             │
                                └───────────────────────────┘
   ┌─────────┐
   │  MinIO  │
   │  :9000  │
   └─────────┘
```

---

## 포트 정리

### Portal Server

| 포트 | 프로토콜 | 용도 |
|------|----------|------|
| 8080 | HTTP | REST API, SSE, WebSocket, 정적 파일 서빙 |

### 데이터베이스

| 포트 | 프로토콜 | 용도 |
|------|----------|------|
| 3306 | MySQL | testdb (호환성/성능 테스트), UFSInfo (참조 데이터) |
| 3307 | MySQL | binmapper (Portal 도구), portal_users, tc_groups |
| 6379 | Redis | 엔티티 캐시 (TTL: TestDB 10분, UFSInfo 1시간) |

### Head TCP

| 포트 | 프로토콜 | 용도 |
|------|----------|------|
| 10001 | TCP | Compatibility Head 명령 전송 (outSocket) |
| 10002 | TCP | Compatibility Head 상태 수신 (inSocket) |
| 10030 | TCP | Performance Head 명령 전송 (outSocket) |
| 10032 | TCP | Performance Head 상태 수신 (inSocket) |

포트 계산: `10000 + portSuffix` (DB `portal_head_connections` 테이블에서 관리)

### 외부 서비스

| 포트 | 프로토콜 | 용도 |
|------|----------|------|
| 4822 | TCP | guacd (Guacamole 데몬) |
| 9000 | HTTP | MinIO S3 API |
| 50052 | gRPC | Go Excel Service |

### Tentacle / HEAD 서버

| 포트 | 프로토콜 | 용도 |
|------|----------|------|
| 22 | SSH | 로그 브라우저, 원격 접속 |
| 3389 | RDP | 원격 데스크톱 접속 |
