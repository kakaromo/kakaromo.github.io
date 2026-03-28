---
title: Metadata API
description: UFS 메타데이터 수집 상태 조회, 슬롯별 토글, 저장 파일 읽기 및 Admin CRUD API
---

Metadata API는 UFS 메타데이터의 수집 상태 조회, 슬롯별 수집 on/off 토글, 저장된 데이터 조회, 그리고 Admin CRUD 기능을 제공합니다.

## 데이터 조회 API

### GET `/api/metadata/types`

활성화된 메타데이터 타입 목록을 조회합니다.

**응답:**

```json
[
  {
    "id": 1,
    "name": "SSR",
    "typeKey": "ssr",
    "category": "common",
    "enabled": true,
    "description": "System Status Report"
  }
]
```

### GET `/api/metadata/types/for-product`

특정 UFS 제품이 지원하는 메타데이터 타입을 조회합니다.

**쿼리 파라미터:**

| 파라미터 | 필수 | 설명 |
|----------|------|------|
| `controller` | No | UFS 컨트롤러 이름 |
| `nandType` | No | NAND 타입 |
| `cellType` | No | 셀 타입 |

**예시:**

```
GET /api/metadata/types/for-product?controller=Santos&nandType=TLC&cellType=V7
```

**응답:** 위와 동일한 `MetadataType[]` 형식

### GET `/api/metadata/slot/{tentacleName}/{slotNumber}`

특정 슬롯의 메타데이터 수집 상태를 조회합니다.

**경로 파라미터:**

| 파라미터 | 설명 |
|----------|------|
| `tentacleName` | Tentacle/VM 이름 (예: T1) |
| `slotNumber` | 슬롯 번호 |

**응답 (수집 중):**

```json
{
  "collecting": true,
  "testToolName": "CTS_Basic_RW",
  "startTimeMs": 1711584000000,
  "elapsedMinutes": 15,
  "types": ["ssr", "telemetry"],
  "entryCounts": {
    "ssr": 4,
    "telemetry": 4
  }
}
```

**응답 (수집 안 함):**

```json
{
  "collecting": false
}
```

### GET `/api/metadata/slot/{tentacleName}/{slotNumber}/{typeKey}`

수집 중인 특정 타입의 메타데이터를 조회합니다. 인메모리 `activeCollections`에서 직접 읽습니다.

**응답:**

```json
[
  {
    "time": 0,
    "nMinSLCEc": 1,
    "nMaxSLCEc": 1,
    "nAvgSLCEc": 1,
    "status": "PASS"
  },
  {
    "time": 5,
    "nMinSLCEc": 2,
    "nMaxSLCEc": 3,
    "nAvgSLCEc": 2,
    "status": "PASS"
  }
]
```

수집 중이 아니면 `404 Not Found`를 반환합니다.

### GET `/api/metadata/file`

VM에 저장된 메타데이터 JSON 파일을 읽습니다.

**쿼리 파라미터:**

| 파라미터 | 필수 | 설명 |
|----------|------|------|
| `tentacleName` | Yes | Tentacle/VM 이름 |
| `path` | Yes | 파일 경로 (예: `/home/octo/tentacle/slot0/log/debug_ssr.json`) |

**응답:** JSON 문자열 (파일 내용 그대로)

### GET `/api/metadata/slot/{tentacleName}/{slotNumber}/files`

VM에 저장된 `debug_*.json` 파일 목록을 조회합니다.

**응답:** 줄바꿈으로 구분된 파일 경로 목록 (문자열)

### GET `/api/metadata/status`

전체 모니터링 상태를 조회합니다. 현재 수집 중인 모든 슬롯 정보를 반환합니다.

**응답:**

```json
[
  {
    "slotKey": "compatibility:5",
    "tentacleName": "T1",
    "slotNumber": 5,
    "testToolName": "CTS_Basic_RW",
    "types": ["ssr"],
    "elapsedMinutes": 10
  }
]
```

## 슬롯별 수집 토글 API

### GET `/api/metadata/slot/{tentacleName}/{slotNumber}/enabled`

슬롯의 메타데이터 수집 활성 여부를 조회합니다.

**응답:**

```json
{
  "enabled": false
}
```

:::note
기본값은 `false`(OFF)입니다. 사용자가 명시적으로 ON해야 수집이 시작됩니다.
:::

### PUT `/api/metadata/slot/{tentacleName}/{slotNumber}/enabled`

슬롯의 메타데이터 수집을 활성/비활성화합니다.

**요청 Body:**

```json
{
  "enabled": true
}
```

**응답:**

```json
{
  "enabled": true
}
```

`enabled: false`로 설정하면 진행 중인 수집도 즉시 중지됩니다.

## 설정 API

### PUT `/api/metadata/config`

수집 설정을 동적으로 변경합니다.

**요청 Body:**

```json
{
  "collectionIntervalMin": 1,
  "enabled": true
}
```

| 필드 | 설명 |
|------|------|
| `collectionIntervalMin` | 수집 간격 (1 또는 5분만 허용) |
| `enabled` | 전체 모니터링 활성/비활성 |

**응답:**

```json
{
  "enabled": true,
  "collectionIntervalMin": 1
}
```

:::caution
`collectionIntervalMin` 변경은 **새로 시작되는** 수집부터 적용됩니다. 이미 진행 중인 수집은 기존 간격을 유지합니다.
:::

## Admin CRUD API

### Metadata Types

#### GET `/api/admin/metadata/types`

모든 메타데이터 타입을 조회합니다 (enabled/disabled 모두 포함).

#### POST `/api/admin/metadata/types`

새 메타데이터 타입을 생성합니다.

**요청 Body:**

```json
{
  "name": "SSR",
  "typeKey": "ssr",
  "category": "common",
  "enabled": true,
  "description": "System Status Report"
}
```

#### PUT `/api/admin/metadata/types/{id}`

기존 메타데이터 타입을 수정합니다. Body 형식은 POST와 동일합니다.

#### DELETE `/api/admin/metadata/types/{id}`

메타데이터 타입을 삭제합니다. 연관된 명령어(commands)와 제품 매핑(product-mappings)도 함께 삭제됩니다.

### Commands

#### GET `/api/admin/metadata/commands`

모든 명령어를 조회합니다.

**응답:**

```json
[
  {
    "id": 1,
    "metadataType": { "id": 1, "name": "SSR", "typeKey": "ssr", ... },
    "commandTemplate": "/data/local/tmp/ufs-utils /dev/block/sda ssr --json",
    "debugTool": { "id": 1, "toolName": "ufs-utils", "toolPath": "/opt/tools" },
    "description": "SSR 수집 명령어"
  }
]
```

#### GET `/api/admin/metadata/commands/by-type/{typeId}`

특정 타입의 명령어만 조회합니다.

#### POST `/api/admin/metadata/commands`

새 명령어를 생성합니다.

**요청 Body:**

```json
{
  "metadataTypeId": 1,
  "commandTemplate": "/data/local/tmp/ufs-utils /dev/block/sda ssr --json",
  "debugToolId": 1,
  "description": "SSR 수집"
}
```

`debugToolId`는 선택 사항이며, 별도 바이너리가 필요 없으면 생략합니다.

#### PUT `/api/admin/metadata/commands/{id}`

기존 명령어를 수정합니다.

**요청 Body:**

```json
{
  "commandTemplate": "/data/local/tmp/ufs-utils /dev/block/sda ssr --json --verbose",
  "debugToolId": 1,
  "description": "SSR 수집 (verbose)"
}
```

#### DELETE `/api/admin/metadata/commands/{id}`

명령어를 삭제합니다.

### Product Mappings

#### GET `/api/admin/metadata/product-mappings`

모든 제품-타입 매핑을 조회합니다.

**응답:**

```json
[
  {
    "id": 1,
    "controller": "Santos",
    "cellType": "V7",
    "nandType": "TLC",
    "oem": null,
    "metadataType": { "id": 1, "name": "SSR", ... }
  }
]
```

#### GET `/api/admin/metadata/product-mappings/by-type/{typeId}`

특정 타입의 매핑만 조회합니다.

#### POST `/api/admin/metadata/product-mappings`

새 매핑을 생성합니다.

**요청 Body:**

```json
{
  "controller": "Santos",
  "cellType": "V7",
  "nandType": "TLC",
  "oem": null,
  "metadataTypeId": 1
}
```

`controller`, `cellType`, `nandType`, `oem` 필드는 모두 선택 사항입니다. `null`이나 빈 문자열로 전달하면 "모든 값에 매칭"됩니다.

#### DELETE `/api/admin/metadata/product-mappings/{id}`

매핑을 삭제합니다.
