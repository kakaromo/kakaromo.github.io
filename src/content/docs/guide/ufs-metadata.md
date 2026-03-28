---
title: UFS Metadata 모니터링
description: TC 평가 중 UFS 메타데이터를 주기적으로 수집하여 시간별 변화를 차트/테이블로 시각화하는 기능
---

UFS Metadata 모니터링은 슬롯에서 TC가 실행되는 동안 UFS 디바이스의 메타데이터(SSR, Telemetry, Read10Debug 등)를 주기적으로 수집하고, 시간에 따른 변화를 차트와 테이블로 시각화하는 기능입니다.

## 개요

UFS 제품마다 지원하는 메타데이터 종류가 다릅니다. 이 기능은 다음을 DB로 관리합니다:

| 관리 항목 | 설명 | 예시 |
|-----------|------|------|
| **Metadata Type** | 수집할 메타데이터 종류 | SSR, Telemetry, Read10Debug |
| **Command** | 각 타입별 adb shell 실행 명령어 | `/data/local/tmp/ufs-utils /dev/block/sda ssr --json` |
| **Product Mapping** | UFS 제품(controller/nandType/cellType)별 지원 타입 매핑 | Santos + TLC + V7 → SSR, Telemetry |

## 수집 활성화 (기본값: OFF)

메타데이터 수집은 **기본적으로 비활성** 상태입니다. 수집을 원하는 슬롯에서 명시적으로 켜야 합니다.

### 슬롯별 토글

1. **Slots 페이지**에서 슬롯을 선택합니다
2. Slot Info 영역에 **Metadata 수집** 토글 버튼이 표시됩니다
3. **OFF** → **ON**으로 전환하면 해당 슬롯의 수집이 활성화됩니다

:::note
토글을 ON으로 설정해도 즉시 수집이 시작되지는 않습니다. 해당 슬롯에서 TC가 **Running** 상태가 되면 자동으로 수집이 시작됩니다. TC가 종료되면 수집도 자동 중지됩니다.
:::

## 수집 프로세스

수집은 다음 순서로 자동 진행됩니다:

### 1. 상태 감지

시스템은 5초 간격으로 HeadSlotStateStore를 폴링하여 슬롯의 `testState` 변화를 감지합니다.

| 감지 패턴 | 동작 |
|-----------|------|
| 다른 상태 → **Running** | 수집 시작 |
| **Running** → 다른 상태 | 수집 중지 + 최종 결과 저장 |
| **Running** 유지 + `testToolName` 변경 | TC 전환 — 이전 TC 수집 종료 → 새 TC 수집 시작 |

### 2. Debug Tool Push

수집 시작 시, 필요한 debug tool 바이너리를 디바이스에 push합니다:

```
adb -s {serial} push {tool_path}/{tool_name} /data/local/tmp/{tool_name}
adb -s {serial} shell 'chmod +x /data/local/tmp/{tool_name}'
```

동일한 tool은 한 번만 push하며, TC 전환 시에는 이미 push된 tool은 재전송하지 않습니다.

### 3. 주기적 수집

설정된 간격(기본 5분)마다 각 메타데이터 타입의 명령어를 실행합니다:

```
adb -s {serial} shell '{command_template}'
```

예시:
```
adb -s 12345678 shell '/data/local/tmp/ufs-utils /dev/block/sda ssr --json'
```

### 4. JSON 저장

수집된 결과는 시간 정보와 함께 JSON 배열로 누적 저장됩니다:

**저장 경로**: `/home/octo/tentacle/slot{N}/log/debug_{typeKey}.json`

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

- `time`: 수집 시작으로부터 경과 시간 (분)
- 나머지 필드: adb 명령어의 JSON 응답

### 5. Hang 방지

디바이스 상태 이상으로 adb 명령이 hang될 수 있습니다. 모든 adb 명령에는 **60초 timeout**이 적용되며, timeout 발생 시 해당 명령을 강제 종료하고 다음 메타데이터 타입으로 진행합니다. adb push는 **120초 timeout**입니다.

## 데이터 조회

### Slots 페이지 (실시간)

Assigned TCs 테이블의 **Meta** 컬럼 버튼을 클릭하면 MetadataDialog가 열립니다.

- TC가 **Running** 중이면: 인메모리 데이터를 실시간으로 조회 (30초 간격 자동 새로고침)
- TC가 **완료**된 경우: VM에 저장된 JSON 파일을 읽어서 표시

### History 페이지 (저장된 데이터)

Compatibility/Performance history 테이블에서도 **Meta** 버튼이 제공됩니다. TC의 `logPath` 기반으로 저장된 메타데이터 JSON 파일을 조회합니다.

## MetadataDialog 사용법

### 1. 메타데이터 타입 선택

Dialog 상단에 해당 UFS 제품이 지원하는 메타데이터 타입 목록이 표시됩니다. 수집 중인 타입은 초록색 점으로 표시됩니다. 원하는 타입을 클릭하면 데이터가 로드됩니다.

### 2. Chart 탭 (숫자 데이터)

JSON 값이 **숫자**(`number`)인 필드를 시간 축 차트로 시각화합니다.

- X축: 시간(분), Y축: 값
- 필드별 체크박스로 표시 항목 선택/해제
- 여러 필드를 선택하면 하나의 차트에 라인이 겹쳐 표시됩니다
- 마우스 휠로 줌 가능

### 3. Table 탭 (전체 데이터)

모든 필드를 DataTable로 표시합니다.

- Row: 각 수집 시점 (time 순서)
- Column: 모든 필드 (time + 데이터 키)
- 컬럼 표시/숨김 토글 가능

### 4. Tree View 탭 (중첩 JSON)

원본 JSON을 트리 형태로 표시합니다. 모든 메타데이터 타입이 중첩 구조를 가질 수 있으므로, 중첩된 object는 재귀적으로 펼쳐볼 수 있습니다.

### 중첩 JSON 처리

중첩된 JSON은 자동으로 **dot notation**으로 flatten됩니다:

```json
// 원본
{ "gc": { "nMinEc": 1, "nMaxEc": 2 }, "status": "PASS" }

// Flatten 결과
{ "gc.nMinEc": 1, "gc.nMaxEc": 2, "status": "PASS" }
```

flatten된 결과에서:
- `gc.nMinEc`, `gc.nMaxEc` → 숫자이므로 Chart 탭에서 선택 가능
- `status` → 문자열이므로 Table 탭에서만 표시

## Admin 설정

[관리자 대시보드](/guide/admin/)의 **Metadata** 탭에서 메타데이터 타입, 명령어, 제품 매핑을 관리합니다.

### Metadata Types

| 필드 | 설명 |
|------|------|
| Name | 표시 이름 (예: SSR) |
| Type Key | URL-safe 키 (예: ssr) — 파일명에 사용 |
| Category | `common` (공통) 또는 `feature` (지원 기능별) |
| Enabled | 활성/비활성 |

### Commands

각 타입에 연결된 adb 실행 명령어입니다.

| 필드 | 설명 |
|------|------|
| Metadata Type | 연결할 타입 |
| Command Template | adb shell로 실행할 명령어 (예: `/data/local/tmp/ufs-utils /dev/block/sda ssr --json`) |
| Debug Tool | 필요한 바이너리 (debug_tools에서 선택, 없으면 None) |

### Product Mappings

UFS 제품별로 지원하는 메타데이터 타입을 매핑합니다.

| 필드 | 설명 |
|------|------|
| Controller | UFS 컨트롤러 (비워두면 전체 매칭) |
| Cell Type | 셀 타입 (비워두면 전체 매칭) |
| NAND Type | NAND 타입 (비워두면 전체 매칭) |
| OEM | OEM (비워두면 전체 매칭) |
| Metadata Type | 연결할 메타데이터 타입 |

:::tip
필드를 비워두면 "모든 값에 매칭"됩니다. 예를 들어 Controller만 지정하고 나머지를 비우면, 해당 Controller의 모든 제품에 대해 매핑됩니다.
:::

## 수집 간격 변경

기본 수집 간격은 5분이며, `application.yaml`에서 변경하거나 API로 동적 변경 가능합니다:

```yaml
metadata:
  monitor:
    enabled: true
    poll-interval-ms: 5000        # 상태 체크 간격 (5초)
    collection-interval-min: 5    # 수집 간격 (1 또는 5분)
```

REST API로 런타임 변경:
```bash
curl -X PUT /api/metadata/config -d '{"collectionIntervalMin": 1}'
```

:::caution
수집 간격을 변경하면 **새로 시작되는 수집**부터 적용됩니다. 이미 진행 중인 수집은 기존 간격을 유지합니다.
:::
