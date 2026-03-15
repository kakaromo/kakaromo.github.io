---
title: Head TCP 프로토콜
description: Head 서버와의 Dual-Socket TCP 프로토콜, 연결 절차, 명령어, 메시지 파싱, 상태 저장소를 설명합니다.
---

## 개요

Head 서버는 UFS 하드웨어 테스트를 제어하는 서버입니다. Spring Boot 애플리케이션은 TCP 소켓을 통해 Head 서버와 양방향 통신하며, 실시간 슬롯 상태를 수신하고 테스트 명령을 전송합니다.

## Dual-Socket 프로토콜

Head 프로토콜은 두 개의 소켓을 사용합니다:

```
┌──────────────────┐          ┌──────────────────┐
│   Spring Boot    │          │   Head Server     │
│                  │          │                   │
│  HeadTcpClient   │─outSocket──>  명령 수신      │
│                  │          │                   │
│  ServerSocket    │<─inSocket──  상태 전송       │
│  (listenPort)    │          │                   │
└──────────────────┘          └──────────────────┘
```

1. **outSocket (명령 소켓)**: Spring Boot → Head. 명령 전송용
2. **inSocket (데이터 소켓)**: Head → Spring Boot. 실시간 슬롯 상태 수신용

## 연결 절차

1. Spring Boot가 `listenPort`에 `ServerSocket` 오픈 (15초 타임아웃)
2. `outSocket`으로 Head 서버(`headHost:headPort`)에 연결
3. 연결 메시지 전송: `{localIp}:{listenSuffix}:{headHost}[{headSuffix}] connection!!\n`
4. Head가 `listenPort`로 역방향 연결 (inSocket)
5. `ServerSocket` 닫기 (더 이상 불필요)
6. `readLoop()` 진입 - 데이터 수신 대기

## 포트 규칙

포트 = `10000 + suffix`

| 연결 | portSuffix | listenPortSuffix | headPort | listenPort |
|------|-----------|-----------------|----------|------------|
| compatibility | 1 | 2 | 10001 | 10002 |
| performance | 30 | 32 | 10030 | 10032 |

## 설정

### 연결 정보 (DB 관리)

Head 연결 정보는 `portal_head_connections` 테이블에서 관리합니다. Admin 페이지의 Head 탭에서 동적으로 추가/수정/삭제/활성화 토글이 가능합니다.

### Test Mode

`test_mode = TRUE`인 연결은 실제 TCP 소켓을 열지 않고 연결된 것으로 간주합니다. UI 개발/테스트 시 Head 서버 없이 슬롯 페이지를 사용할 수 있습니다.

:::note
Test Mode 연결은 **Admin에게만** 표시됩니다. `/api/head/connections` API에서 일반 사용자에게 필터링됩니다.
:::

### YAML 설정 (인프라)

```yaml
head:
  reconnect-delay-ms: 5000       # 재접속 대기 시간
  sse:
    push-interval-ms: 1000       # SSE 푸시 주기
    timeout-ms: 300000           # SSE 타임아웃 (5분)
```

## TCP 명령어

Head 서버로 전송하는 명령어 목록:

| 명령어 | TCP 메시지 형식 | 설명 |
|--------|----------------|------|
| `disconnect` | `{ip}:{port%10000}:exit[0]0;0\n` | 연결 해제 |
| `settr` | `{ip}:{port%10000}:set{type}testrequest[{slot}]trid;{trId}\n` | TR 설정 |
| `settc` | `{ip}:{port%10000}:assign{type}cmd[{slot}]{tcData}\n` | TC 설정 |
| `initslot` | `{ip}:{port%10000}:initslot[{slot}]all;0\n` | 슬롯 초기화 |
| `test` | `{ip}:{port%10000}:starttest[{slot}]\n` | 테스트 시작 |
| `stop` | `{ip}:{port%10000}:stoptest[{slot}]\n` | 테스트 중지 |
| `reordertest` | `{ip}:{port%10000}:reordertest[{slot}]neworder;{new},currentorder;{cur}^\n` | TC 순서 변경 |

`{type}`은 `compatibility` 또는 `performance`입니다.

## 수신 메시지 파싱

### 메시지 프레이밍

수신 데이터는 `^end^` 구분자로 청크 단위 분리됩니다.

### 메시지 패턴

| 패턴 | 설명 |
|------|------|
| `initslot[N]runningtime;{time};{csv},^` | 슬롯 초기 상태 |
| `update[N]runningtime;{time};{csv},^` | 슬롯 상태 업데이트 |
| `slotcount[N]^` | 슬롯 개수 정보 |

### CSV 필드 순서 (29개)

| Index | 필드명 | 설명 |
|-------|--------|------|
| 0 | `modelName` | 모델명 |
| 1 | `battery` | 배터리 잔량 |
| 2 | `free` | 여유 공간 |
| 3 | `toolName` | 도구명 |
| 4 | `trName` | Test Request 이름 |
| 5 | `setState` | Set 상태 |
| 6 | `testState` | 테스트 상태 |
| 7 | `testArea` | 테스트 영역 |
| 8 | `setLocation` | 슬롯 위치 (T1-0, T1-1 등) |
| 9 | `isInstalled` | 설치 여부 |
| 10 | `runningState` | 실행 상태 |
| 11 | `minBattery` | 최소 배터리 |
| 12 | `maxBattery` | 최대 배터리 |
| 13 | `testHistoryId` | History ID |
| 14 | `period` | 주기 |
| 15 | `ufsId` | UFS ID |
| 16 | `npoCount` | NPO 카운트 |
| 17 | `productName` | 제품명 |
| 18 | `deviceName` | 디바이스명 |
| 19 | `fileSystem` | 파일 시스템 |
| 20 | `fwVer` | 펌웨어 버전 |
| 21 | `fwDate` | 펌웨어 날짜 |
| 22 | `controller` | 컨트롤러 |
| 23 | `nandType` | NAND 타입 |
| 24 | `nandSize` | NAND 크기 |
| 25 | `cellType` | 셀 타입 |
| 26 | `density` | 밀도 |
| 27 | `specVer` | UFS 스펙 버전 |
| 28 | `host` | 호스트 |
| 29 | `testCaseIds` | TC ID 목록 (`/` 구분) |

## HeadSlotStateStore

Thread-safe 인메모리 저장소로, `ConcurrentHashMap`을 사용합니다.

- **키 형식**: `"{source}:{slotIndex}"` (예: `"compatibility:0"`)
- **버전 관리**: `AtomicLong` 카운터로 모든 변경 추적
- **SSE 최적화**: 클라이언트별 `lastVersion`을 추적하여 변경이 없으면 푸시하지 않음

## 재시도 로직

- 최대 3회 재시도
- 3회 모두 실패 시 에러 상태로 전환 (`"3회 연결 시도 실패"`)
- `HeadConnectionManager`를 통해 수동 재접속 가능
- Admin 페이지에서 enable/disable 토글로 연결 제어

:::caution
disable/stop/reconnect 시 Head 서버에 exit 메시지를 강제 전송합니다. `connected` 상태 체크 없이 `sendDisconnectForce()`로 전송하여 race condition으로 인한 exit 누락을 방지합니다.
:::

## 관련 클래스

`com.samsung.portal.head` 패키지는 기능별 하위 패키지로 구성됩니다:

| 패키지 | 클래스 | 역할 |
|--------|--------|------|
| `entity` | `HeadConnection` | DB 엔티티 (portal_head_connections) |
| `entity` | `HeadSlotData` | 슬롯 상태 DTO |
| `repository` | `HeadConnectionRepository` | JPA Repository |
| `config` | `HeadConnectionProperties` | YAML 설정 바인딩 (reconnect, SSE) |
| `service` | `HeadConnectionService` | 연결 정보 CRUD |
| `service` | `HeadMessageParser` | 수신 메시지 파싱 |
| `service` | `HeadSlotStateStore` | 인메모리 상태 저장소 |
| `service` | `ImageUploadService` | 이미지 업로드 설정 (SSH) |
| `tcp` | `HeadTcpClient` | TCP 연결 및 명령 전송 |
| `tcp` | `HeadConnectionManager` | 모든 HeadTcpClient 생명주기 관리 |
| `controller` | `HeadSseController` | SSE 스트리밍 API |
| `controller` | `HeadCommandController` | 명령 전송 API |
| `controller` | `ImageUploadController` | 이미지 업로드 옵션 API |
