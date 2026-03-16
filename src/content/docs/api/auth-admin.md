---
title: Auth & Admin API
description: 인증 상태 확인, 사용자 관리, 서버/Head 연결 관리, 캐시, 헬스체크, 메뉴 설정 API
---

Auth API는 인증 상태를 확인하고, Admin API는 시스템 관리 기능을 제공합니다.

## Auth

### GET `/api/auth/me`

현재 인증 상태를 반환합니다.

**응답 (미인증):**

```json
{ "authenticated": false }
```

**응답 (인증):**

```json
{
  "authenticated": true,
  "name": "홍길동",
  "email": "hong@samsung.com",
  "role": "ADMIN"
}
```

### POST `/api/auth/login`

자체 인증 로그인입니다. 세션 기반으로 동작합니다.

**요청 Body:**

```json
{ "username": "admin", "password": "password" }
```

### GET `/api/auth/login-url`

OAuth2 로그인 URL을 반환합니다. (OAuth2 활성화 시)

### POST `/api/auth/logout`

로그아웃합니다. 세션을 무효화합니다.

---

## Users

사용자 계정 관리 API입니다. (Admin 전용)

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/admin/users` | 사용자 목록 |
| POST | `/api/admin/users` | 사용자 생성 |
| PUT | `/api/admin/users/{id}` | 사용자 정보 수정 |
| PUT | `/api/admin/users/{id}/password` | 비밀번호 변경 |
| DELETE | `/api/admin/users/{id}` | 사용자 삭제 |

**요청 (POST):**

```json
{
  "username": "newuser",
  "password": "securePassword",
  "displayName": "새 사용자",
  "role": "USER",
  "enabled": true
}
```

---

## Servers

서버(VM) 관리 API입니다. (Admin 전용) Guacamole 원격 접속 및 Log Browser에서 사용됩니다.

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/admin/servers` | 서버 목록 |
| POST | `/api/admin/servers` | 서버 추가 |
| PUT | `/api/admin/servers/{id}` | 서버 수정 |
| DELETE | `/api/admin/servers/{id}` | 서버 삭제 |

**요청 (POST/PUT):**

```json
{
  "name": "T1",
  "ip": "192.168.1.101",
  "username": "user",
  "password": "pass",
  "sshPort": 22,
  "rdpPort": 3389,
  "connectionType": 3,
  "visible": true
}
```

---

## Head Connections

Head TCP 연결 관리 API입니다. (Admin 전용)

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/admin/head-connections` | 연결 목록 (실시간 상태 포함) |
| POST | `/api/admin/head-connections` | 연결 추가 |
| PUT | `/api/admin/head-connections/{id}` | 연결 수정 |
| DELETE | `/api/admin/head-connections/{id}` | 연결 삭제 (exit 메시지 전송) |
| POST | `/api/admin/head-connections/{id}/toggle` | 활성화/비활성화 토글 |

**요청 (POST/PUT):**

```json
{
  "name": "compatibility",
  "host": "192.168.1.248",
  "portSuffix": "1",
  "listenPortSuffix": "2",
  "enabled": true,
  "testMode": false
}
```

---

## Cache

Redis 캐시 관리 API입니다. (Admin 전용)

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/admin/caches` | 캐시 목록 (이름, 그룹, TTL) |
| DELETE | `/api/admin/caches/{name}` | 특정 캐시 클리어 |
| DELETE | `/api/admin/caches` | 전체 캐시 클리어 |

**응답 (GET):**

```json
[
  { "name": "compatibilityTestRequests", "group": "TestDB", "ttl": "10m" },
  { "name": "ufsVersions", "group": "UFSInfo", "ttl": "1h" }
]
```

---

## Health

### GET `/api/admin/health`

9개 서비스의 병렬 헬스체크를 수행합니다.

**응답:**

```json
{
  "mysql-testdb": "UP",
  "mysql-ufsinfo": "UP",
  "mysql-portal": "UP",
  "redis": "UP",
  "head-compatibility": "UP",
  "head-performance": "DOWN",
  "grpc-excel": "UP",
  "minio": "UP",
  "guacd": "UP"
}
```

---

## App Info

### GET `/api/admin/app-info`

JVM 메모리, GC 통계, 스레드, CPU 정보를 반환합니다.

### GET `/api/admin/connections`

활성 연결 수, DB 커넥션 풀(HikariCP), Head TCP 상세 정보를 반환합니다.

### GET `/api/admin/scheduled-tasks`

등록된 스케줄 작업 목록을 반환합니다.

### GET `/api/admin/config`

애플리케이션 설정을 반환합니다. 비밀번호 등 민감 정보는 마스킹 처리됩니다.

---

## Slot Override

슬롯 데이터 오버라이드 관리 API입니다. (Admin 전용)

HeadSlotData의 특정 필드를 수동으로 덮어쓰고, Head TCP 업데이트를 차단(Lock)하거나 원래 값으로 복원합니다.

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/admin/slot-overrides` | 현재 적용된 오버라이드 목록 조회 |
| PUT | `/api/admin/slot-override` | 슬롯 오버라이드 적용 (Lock 포함) |
| DELETE | `/api/admin/slot-override/{source}/{slotIndex}` | 오버라이드 삭제 및 원래 값 복원 |

**요청 (PUT):**

```json
{
  "source": "compatibility",
  "slotIndex": 3,
  "testState": "stop",
  "connection": "disconnected",
  "product": "CTRL_SLC_2D_256GB_1.0"
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `source` | string | 연결 이름 (compatibility, performance 등) |
| `slotIndex` | number | 슬롯 인덱스 |
| `testState` | string (선택) | 덮어쓸 테스트 상태 |
| `connection` | string (선택) | 덮어쓸 연결 상태 |
| `product` | string (선택) | 덮어쓸 product 값 |

**응답 (GET):**

```json
[
  {
    "source": "compatibility",
    "slotIndex": 3,
    "testState": "stop",
    "connection": "disconnected",
    "product": "CTRL_SLC_2D_256GB_1.0",
    "locked": true
  }
]
```

:::note
오버라이드가 적용된 슬롯은 `locked: true` 상태가 되어, 이후 Head TCP 메시지가 수신되어도 해당 슬롯의 값이 변경되지 않습니다. DELETE 호출 시 Lock이 해제되고 슬롯이 원래 Head TCP 값으로 복원됩니다.
:::

---

## Menus

메뉴 가시성 관리 API입니다. (Admin 전용)

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/admin/menus` | 메뉴 목록 + 가시성 상태 |
| PUT | `/api/admin/menus` | 메뉴 가시성 일괄 업데이트 |

**요청 (PUT):**

```json
{
  "menus": {
    "dashboard": true,
    "compatibility": true,
    "performance": true,
    "storage": false,
    "bin-mapper": true
  }
}
```
