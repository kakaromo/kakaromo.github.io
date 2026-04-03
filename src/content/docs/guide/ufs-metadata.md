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
| **Command** | 각 타입별 수집 명령어 (Tool: adb 명령어, Sysfs: 경로+정규식) | Tool: `/data/local/tmp/ufs-utils ... --json`, Sysfs: `/sys/block/sda/stat` |
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

설정된 간격(기본 5분)마다 각 메타데이터 타입의 명령어를 command_type에 따라 실행합니다:

**Tool 모드**: `adb shell`로 명령어 실행 → JSON 출력
```
adb -s 12345678 shell '/data/local/tmp/ufs-utils /dev/block/sda ssr --json'
```

**Sysfs 모드**: `adb shell cat`으로 경로 읽기 → 정규식 파싱 → JSON 변환
```
adb -s 12345678 shell 'cat /sys/block/sda/stat'
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
- 필드별 버튼으로 표시 항목 선택/해제
- 여러 필드를 선택하면 하나의 차트에 라인이 겹쳐 표시됩니다
- 마우스 휠로 줌 가능

#### 키 일괄 선택/해제

키 목록 상단의 **Key 전체 선택** / **Key 전체 해제** 버튼으로 모든 숫자 키를 한 번에 선택하거나 해제할 수 있습니다. Delta도 **Δ 전체 선택** / **Δ 전체 해제** 버튼으로 일괄 전환됩니다.

#### Delta 모드 (Δ)

누적 값으로 저장되는 메타데이터의 경우, 시간별 **변화량**을 보고 싶을 수 있습니다. 각 키 선택 버튼 옆의 **Δ** 버튼을 클릭하면 delta 모드가 활성화됩니다.

| 모드 | 표시 값 | 예시 |
|------|---------|------|
| **원본** (기본) | 수집된 그대로의 값 | `100, 105, 112, 120` |
| **Delta** (Δ) | 이전 값과의 차이, 시작=0 | `0, 5, 7, 8` |

- 키마다 독립적으로 on/off 가능
- Delta 적용된 키는 차트 범례와 테이블 헤더에 `(Δ)` 표시
- 주황색 Δ 버튼 = delta 활성, 회색 = 비활성

#### Line / Scatter 토글

차트 하단의 **Line** / **Scatter** 버튼으로 차트 유형을 전환할 수 있습니다.

| 모드 | 설명 |
|------|------|
| **Line** (기본) | 시간순 라인 차트 — 데이터 포인트를 선으로 연결 |
| **Scatter** | 산점도 — 각 데이터 포인트를 독립적으로 표시 (분포 확인에 유용) |

#### Y축 이름

Chart 탭 하단의 **Y축** 입력 필드에 이름을 입력하면 차트 Y축에 표시됩니다 (예: `EC Count`, `Latency (ms)`). 비워두면 이름 없이 표시됩니다.

#### Excel 내보내기

차트 옆의 **Excel** 버튼을 클릭하면 현재 표시된 데이터를 `.xlsx` 파일로 다운로드합니다. 선택된 키와 Delta 설정이 그대로 반영됩니다.

### 3. Table 탭 (전체 데이터)

모든 필드를 DataTable로 표시합니다. Delta 모드가 적용된 키는 테이블에서도 delta 값으로 표시됩니다.

- Row: 각 수집 시점 (time 순서)
- Column: 모든 필드 (time + 데이터 키), delta 적용 키는 `(Δ)` 표시
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

각 타입에 연결된 수집 명령어입니다. **Command Type**에 따라 실행 방식이 달라집니다.

| 필드 | 설명 |
|------|------|
| Metadata Type | 연결할 타입 |
| Command Type | **Tool** (기본) 또는 **Sysfs** |
| Command Template | 실행할 명령어 또는 sysfs 경로 (아래 참조) |
| Debug Tool | 필요한 바이너리 — Tool 모드에서만 사용. Sysfs 모드에서는 불필요 |

#### Tool 모드 (기본)

기존 방식. `adb shell`로 명령어를 실행하고 JSON 출력을 기대합니다.

```
/data/local/tmp/ufs-utils /dev/block/sda ssr --json
```

#### Sysfs 모드

`adb shell cat`으로 sysfs/proc 경로를 읽고, 정규식으로 값을 추출하여 JSON으로 변환합니다. 줄바꿈으로 여러 경로를 나열합니다.

**포맷**: `경로 | regex:패턴 | keys:키1,키2`

```
/sys/block/sda/size
/sys/block/sda/stat | regex:(\d+)\s+\d+\s+(\d+) | keys:read_ios,read_sectors
/proc/meminfo | regex:MemTotal:\s+(\d+) | keys:mem_total_kb
```

| 구성요소 | 필수 | 설명 |
|----------|------|------|
| 경로 | 필수 | `adb shell cat`으로 읽을 파일 경로 |
| `regex:패턴` | 선택 | 정규식 캡처 그룹으로 원하는 값만 추출. 생략 시 전체 출력을 값으로 사용 |
| `keys:키1,키2` | 선택 | 캡처 그룹에 대응하는 JSON key. 생략 시 경로 마지막 세그먼트가 key |

위 예시의 수집 결과:
```json
{ "size": "125034840", "read_ios": "1234", "read_sectors": "56789", "mem_total_kb": "3891204" }
```

:::tip
sysfs/proc의 출력이 단순 숫자든, `key : value` 형태든, 공백 구분 여러 값이든 정규식으로 원하는 부분만 추출할 수 있습니다. 캡처 그룹 `()` 개수와 `keys` 개수를 맞추면 됩니다.
:::

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
