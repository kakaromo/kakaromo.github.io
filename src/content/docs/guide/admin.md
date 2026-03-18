---
title: 관리자 대시보드
description: 시스템 상태 모니터링, 캐시 관리, 사용자/서버/메뉴/Head 연결 관리 기능
---

관리자 대시보드는 시스템 전반의 상태를 모니터링하고, 사용자/서버/연결 등 주요 설정을 관리하는 페이지입니다. **ADMIN** 역할을 가진 사용자만 접근할 수 있습니다.

## System Health

9개 주요 서비스의 상태를 실시간으로 모니터링합니다. 각 서비스는 상태 표시등(녹색/빨간색)으로 정상 여부를 나타냅니다.

모니터링 대상: Spring Boot, MySQL, Redis, MinIO, guacd, Head TCP, Go Excel Service 등

## Active Connections

현재 활성화된 Head TCP 연결 상태를 표시합니다. 각 연결의 접속 상태, 마지막 통신 시각 등을 확인할 수 있습니다.

## Cache Management

Redis 캐시 목록을 조회하고 개별 캐시를 무효화(evict)할 수 있습니다.

| 기능 | 설명 |
|------|------|
| 캐시 목록 | 현재 저장된 캐시 키 목록 표시 |
| 캐시 무효화 | 특정 캐시 항목을 삭제하여 다음 조회 시 DB에서 새로 로드 |

:::tip
데이터를 직접 DB에서 수정한 후 캐시가 오래된 값을 반환할 때 캐시 무효화를 사용하세요.
:::

## App Info

애플리케이션의 런타임 정보를 표시합니다.

| 항목 | 설명 |
|------|------|
| **JVM** | Java 버전, 힙 메모리 사용량 |
| **GC** | Garbage Collector 종류, 실행 횟수 |
| **시스템** | OS, CPU, 디스크 사용량 |

## Configuration

현재 적용된 주요 설정값을 뷰 형태로 표시합니다. `application.yaml`에 정의된 커스텀 설정, 데이터소스 연결 정보, gRPC 채널 설정 등을 확인할 수 있습니다.

:::note
설정값은 읽기 전용으로 표시됩니다. 변경이 필요하면 `application.yaml`을 수정하고 서비스를 재시작하세요.
:::

## Menu Management

사이드바 메뉴의 표시/숨김을 관리합니다. 특정 기능이 아직 준비되지 않았거나 특정 사용자에게 불필요한 메뉴를 숨길 수 있습니다.

## User Management

사용자 계정을 관리합니다.

| 기능 | 설명 |
|------|------|
| **생성** | 새 사용자 계정 추가 |
| **조회** | 등록된 사용자 목록 |
| **수정** | 사용자 정보 변경 |
| **삭제** | 사용자 계정 제거 |

### 역할

| 역할 | 설명 |
|------|------|
| **ADMIN** | 관리자 대시보드 접근 가능, 전체 기능 사용 |
| **USER** | 일반 사용자, 관리자 대시보드 접근 불가 |

## Server Management

테스트 서버(VM) 정보를 관리합니다.

| 기능 | 설명 |
|------|------|
| **CRUD** | 서버 정보 생성/조회/수정/삭제 |
| **연결 타입** | 서버의 연결 타입 설정 (SSH/RDP 등) |

등록된 서버 정보는 원격 터미널 접속 시 VM 선택 목록에 반영됩니다.

## Head Connections Management

Head 서버 연결을 관리합니다.

| 기능 | 설명 |
|------|------|
| **CRUD** | Head 연결 정보 생성/조회/수정/삭제 |
| **enable/disable 토글** | 연결 활성화/비활성화 |
| **test mode** | 테스트 모드 토글 (실제 Head 없이 테스트) |

:::caution
Head 연결을 disable하면 해당 Head에 속한 슬롯이 Slots 페이지에서 비활성화됩니다. 진행 중인 테스트가 없는지 확인 후 변경하세요.
:::

Head 연결 정보는 Slots 페이지의 탭 구성에 직접 영향을 줍니다. 새 Head를 추가하면 Slots 페이지에 새 탭이 자동 생성됩니다.

## Sets Management

SetInfomation(디바이스 Set) 데이터를 관리합니다.

| 기능 | 설명 |
|------|------|
| **CRUD** | Set 정보 생성/조회/수정/삭제 |
| **인라인 편집** | 테이블 행에서 직접 수정 |

관리 필드: Number, Serial, Product Name, Model Name, Device Name, Push Path, OS Version, Kernel Version, Vendor Command, Power Level Min, Power Button Click Time, Battery Off Stay Time, Booting Time

## Slots Management

SlotInfomation 데이터를 조회하고 수정합니다. (삭제/생성 불가)

| 기능 | 설명 |
|------|------|
| **조회** | 전체 슬롯 목록 표시 |
| **수정** | 인라인 편집으로 슬롯 정보 변경 |

주요 표시 필드: Tentacle Name, Slot Number, IP, Status, Request ID, TC 수, Memo 등

:::note
SlotInfomation은 복합 Primary Key (tentacleName + slotNumber)를 사용합니다. 생성/삭제는 Head 시스템에서 관리됩니다.
:::

## UFS Info Management

7개 UFS 참조 코드 테이블을 서브탭으로 관리합니다. 모든 테이블은 동일한 `ID + NAME` 구조입니다.

| 테이블 | 설명 |
|--------|------|
| OEM | 호스트 OEM 정보 |
| NAND Size | NAND 공정 크기 |
| Density | 용량 |
| Cell Type | NAND 셀 타입 |
| NAND Type | NAND 종류 |
| Controller | UFS 컨트롤러 |
| UFS Version | UFS 규격 버전 |

:::caution
이미 TR에서 참조 중인 코드 항목을 삭제하면 해당 TR의 표시에 영향을 줄 수 있습니다.
:::

## Perf Generator

성능 차트 컴포넌트(perf-content) 코드를 자동 생성하는 개발 도구입니다. JSON 데이터 구조를 입력하면 Svelte 컴포넌트 코드가 생성됩니다.

:::tip
새로운 파서를 추가할 때 이 도구를 사용하면 초기 코드를 빠르게 작성할 수 있습니다.
:::

## Slot Override

Admin 전용 탭으로, Head TCP 메시지로 수신된 슬롯 데이터를 관리자가 직접 덮어쓸 수 있습니다.

### 주요 기능

| 기능 | 설명 |
|------|------|
| **Override** | 특정 슬롯의 `testState`, `connection`, `product` 등 필드 값을 수동으로 설정 |
| **Lock** | 덮어쓰기 적용 후 잠금 — 이후 Head TCP 업데이트가 해당 슬롯에 반영되지 않음 |
| **Restore** | 잠금 해제 및 오버라이드 삭제 — 슬롯이 Head TCP 원래 값으로 복원됨 |

### 사용 흐름

1. 대상 슬롯의 source와 slotIndex를 선택합니다.
2. 변경할 필드 값을 입력하고 **Override** 버튼을 클릭합니다.
3. 슬롯이 즉시 덮어쓴 값으로 표시되며, **Lock** 상태가 됩니다.
4. 원래 값으로 되돌리려면 **Restore** 버튼을 클릭합니다.

:::caution
Lock 상태에서는 Head TCP에서 실제 슬롯 데이터가 수신되어도 화면에 반영되지 않습니다. 테스트 완료 후 반드시 Restore하세요.
:::
