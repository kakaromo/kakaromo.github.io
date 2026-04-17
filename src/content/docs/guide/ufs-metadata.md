---
title: UFS Metadata 모니터링
description: TC 평가 중 UFS 메타데이터를 주기적으로 모니터링하여 시간별 변화를 차트/히트맵/테이블로 시각화하는 기능
---

UFS Metadata 모니터링은 슬롯에서 TC가 실행되는 동안 UFS 디바이스의 메타데이터(SSR, Telemetry, Read10Debug 등)를 주기적으로 모니터링하고, 시간에 따른 변화를 차트와 테이블로 시각화하는 기능입니다.

## 개요

UFS 제품마다 지원하는 메타데이터 종류가 다릅니다. 이 기능은 다음을 DB로 관리합니다:

| 관리 항목 | 설명 | 예시 |
|-----------|------|------|
| **Metadata Type** | 모니터링할 메타데이터 종류 | SSR, Telemetry, Read10Debug |
| **Command** | 각 타입별 모니터링 명령어 (Tool/Sysfs/KeyValue/Raw) | Tool: `/data/local/tmp/ufs-utils ... --json`, Sysfs: `/sys/block/sda/stat` |
| **Product Mapping** | UFS 제품(controller/nandType/cellType)별 지원 타입 매핑 | Santos + TLC + V7 → SSR, Telemetry |

## 모니터링 활성화

메타데이터 모니터링은 **기본적으로 비활성** 상태입니다. MetadataDialog에서 설정합니다.

### 진입 방법

1. **슬롯 카드 우클릭** → Context Menu → **Metadata** 클릭
2. **TC 테이블**의 **Meta** 버튼 클릭 (Running 또는 완료된 TC)

### 모니터링 컨트롤

MetadataDialog 상단에 모니터링 컨트롤 바가 있습니다:

- **모니터링 ON/OFF**: 토글 버튼으로 시작/중지
- **주기 설정**: 초 단위 입력 (최소 10초, 기본값 300초=5분), 슬롯별 개별 설정
- **타입별 ON/OFF**: 각 메타데이터 타입 옆 ON/OFF 버튼으로 개별 제외 가능

:::note
ON으로 설정해도 즉시 모니터링이 시작되지는 않습니다. 해당 슬롯에서 TC가 **Running** 상태가 되면 자동으로 시작됩니다. TC가 종료되면 자동 중지됩니다.
:::

### 멀티 슬롯

여러 슬롯을 체크박스로 선택한 후 Metadata를 열면 **슬롯 탭**이 표시됩니다. 각 탭에서 독립적으로 데이터를 확인하고 설정할 수 있습니다.

## 모니터링 프로세스

모니터링은 다음 순서로 자동 진행됩니다:

### 1. 상태 감지

시스템은 5초 간격으로 HeadSlotStateStore를 폴링하여 슬롯의 `testState` 변화를 감지합니다.

| 감지 패턴 | 동작 |
|-----------|------|
| 다른 상태 → **Running** | 모니터링 시작 |
| **Running** → 다른 상태 | 모니터링 중지 + 최종 결과 저장 |
| **Running** 유지 + `testToolName` 변경 | TC 전환 — 이전 TC 모니터링 종료 → 새 TC 모니터링 시작 |

### 2. Debug Tool Push

모니터링 시작 시, 필요한 debug tool 바이너리를 디바이스에 push합니다:

```
adb -s {serial} push {tool_path}/{tool_name} /data/local/tmp/{tool_name}
adb -s {serial} shell 'chmod +x /data/local/tmp/{tool_name}'
```

동일한 tool은 한 번만 push하며, TC 전환 시에는 이미 push된 tool은 재전송하지 않습니다.

### 3. 주기적 모니터링

설정된 간격(기본 300초, 슬롯별 개별 설정 가능)마다 각 메타데이터 타입의 명령어를 command_type에 따라 실행합니다:

| Command Type | 실행 방식 | 용도 |
|---|---|---|
| **tool** | `adb shell '명령어'` → JSON 출력 | UFS 전용 tool |
| **sysfs** | `adb shell cat 경로` → 정규식 파싱 | sysfs/proc 값 추출 |
| **keyvalue** | `adb shell cat 경로` → key:value 자동 파싱 | meminfo, ext4/f2fs stats |
| **raw** | `adb shell cat 경로` → 텍스트 그대로 | 테이블/비트맵 |

### 4. JSON 저장

모니터링 결과는 시간 정보와 함께 JSON 배열로 누적 저장됩니다:

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

- `time`: 모니터링 시작으로부터 경과 시간 (초)
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

Dialog 상단에 해당 UFS 제품이 지원하는 메타데이터 타입 목록이 표시됩니다. 모니터링 중인 타입은 초록색 점으로 표시됩니다. 원하는 타입을 클릭하면 데이터가 로드됩니다.

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
| **원본** (기본) | 모니터링된 그대로의 값 | `100, 105, 112, 120` |
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

- Row: 각 모니터링 시점 (time 순서)
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

각 타입에 연결된 모니터링 명령어입니다. **Command Type**에 따라 실행 방식이 달라집니다.

| 필드 | 설명 |
|------|------|
| Metadata Type | 연결할 타입 |
| Command Type | **Tool** / **Sysfs** / **KeyValue** / **Raw** |
| Command Template | 실행할 명령어 또는 경로 (아래 참조) |
| Debug Tool | 필요한 바이너리 — Tool 모드에서만 사용, 나머지는 불필요 |

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

위 예시의 모니터링 결과:
```json
{ "size": "125034840", "read_ios": "1234", "read_sectors": "56789", "mem_total_kb": "3891204" }
```

:::tip
캡처 그룹 `()` 개수와 `keys` 개수를 맞추면 됩니다.
:::

#### KeyValue 모드

`key: value` 형태 출력을 자동으로 파싱합니다. meminfo, ext4/f2fs stats 등에 적합합니다.

```
/proc/meminfo
/proc/fs/f2fs/sda1/status
/proc/fs/ext4/sda1/mb_stats
```

- 들여쓰기 → dot notation (`mballoc.reqs`)
- 숫자 자동 추출, 단위(kB, ms, %) 제거
- 괄호 내 값 추출 (`GC calls: 234 (BG: 189)` → 2개 키)
- `#`, `=`, `[...]` 줄 자동 무시

#### Raw 모드

출력 전체를 텍스트 그대로 저장합니다. 테이블/비트맵 등 자동 파싱이 어려운 출력용.

```
/proc/fs/ext4/sda1/mb_groups
/proc/fs/f2fs/sda1/segment_info
/proc/fs/f2fs/sda1/victim_bits
```

Tree View에서 원본 텍스트를 확인할 수 있습니다.

### Product Mappings

UFS 제품별로 지원하는 메타데이터 타입을 매핑합니다. **체크박스로 여러 타입을 한번에 선택**하여 등록할 수 있습니다.

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

## 모니터링 주기 변경

### 전역 기본값

`application.yaml`에서 기본 주기를 설정합니다 (분 단위):

```yaml
metadata:
  monitor:
    enabled: true
    poll-interval-ms: 5000        # 상태 체크 간격 (5초)
    collection-interval-min: 5    # 기본 모니터링 주기 (5분 = 300초)
```

### 슬롯별 개별 설정

MetadataDialog의 모니터링 컨트롤 바에서 슬롯별 주기를 초 단위로 설정할 수 있습니다 (최소 10초). 슬롯별 설정이 있으면 전역 기본값보다 우선합니다.

REST API:
```bash
# 전역 설정 변경
curl -X PUT /api/metadata/config -d '{"collectionIntervalMin": 1}'

# 슬롯별 개별 설정 (초 단위)
curl -X PUT /api/metadata/slot/T01/0/interval -d '{"intervalSeconds": 60}'
```

:::caution
주기를 변경하면 **새로 시작되는 모니터링**부터 적용됩니다. 이미 진행 중인 모니터링은 기존 주기를 유지합니다.
슬롯별 설정은 인메모리이므로 서버 재시작 시 전역 기본값으로 초기화됩니다.
:::
