---
title: UFS Metadata 아키텍처
description: UFS 메타데이터 모니터링 시스템의 설계, 데이터 흐름, Command Type, 멀티 슬롯 지원
---

## 한줄 요약

**TC 평가 중** UFS 디바이스/파일시스템의 상태(SSR, Telemetry, ext4/f2fs stats, meminfo 등)를 **N초 간격으로 자동 모니터링**하여 JSON 파일로 저장하고, 시간별 변화를 차트/히트맵/테이블로 보여주는 시스템입니다.

---

## 전체 흐름

```
[MetadataDialog에서 모니터링 ON]
    ↓
[슬롯에서 TC 시작]
    ↓
MetadataMonitorService (5초마다 상태 체크)
    ↓ testState가 "Running"으로 변경 감지
    ↓
[모니터링 시작]
    ├── 1. 이 UFS 제품이 지원하는 메타데이터 타입 조회 (DB)
    ├── 2. 제외된 타입 필터링 (사용자가 OFF한 타입 제외)
    ├── 3. 필요한 debug tool을 디바이스에 push (adb push)
    └── 4. N초 간격으로 모니터링 반복 (command_type별 실행)
         ↓
    모니터링 결과를 JSON 배열로 누적
    ├── 인메모리 저장 (실시간 조회용)
    └── VM에 JSON 파일 저장 (나중에 History에서 조회용)
         ↓
[TC 종료 감지]
    └── 모니터링 중지 + 최종 결과 저장
```

---

## 데이터 모델

3개 테이블로 "어떤 제품에, 어떤 명령어로, 어떤 메타데이터를 모니터링할지"를 관리합니다.

### 테이블 관계

```
ufs_metadata_types (메타데이터 종류)
    │   예: SSR, Telemetry, f2fs Status, meminfo
    │
    ├── ufs_metadata_commands (모니터링 명령어)
    │       각 타입별 실행 명령어 (4가지 command_type)
    │       필요한 debug tool 바이너리 연결
    │
    └── ufs_product_metadata (제품별 지원 매핑)
            controller/nandType/cellType 조합 → 지원 타입 매핑
            체크박스로 여러 타입 한번에 등록 가능
```

### 테이블 상세

#### ufs_metadata_types

| 컬럼 | 설명 | 예시 |
|------|------|------|
| name | 표시 이름 | SSR, f2fs Status |
| type_key | URL/파일명용 키 | ssr, f2fs_status |
| category | `common` 또는 `feature` | common |
| enabled | 활성화 여부 | true |

#### ufs_metadata_commands

| 컬럼 | 설명 | 예시 |
|------|------|------|
| metadata_type_id | 연결된 타입 | → SSR |
| command_type | 실행 방식 (4가지) | `tool`, `sysfs`, `keyvalue`, `raw` |
| command_template | 실행할 명령어/경로 | 아래 참조 |
| debug_tool_id | 필요한 바이너리 | → ufs-utils (tool 모드만) |

#### ufs_product_metadata

| 컬럼 | 설명 |
|------|------|
| controller | UFS 컨트롤러 (NULL = 와일드카드) |
| cell_type | 셀 타입 (NULL = 와일드카드) |
| nand_type | NAND 타입 (NULL = 와일드카드) |
| metadata_type_id | 지원하는 메타데이터 타입 |

- NULL 필드 = **모든 값에 매칭** (와일드카드)
- Admin UI에서 체크박스로 여러 타입 한번에 등록
- 같은 product 조건의 매핑은 그룹으로 표시

---

## Command Type (4가지)

모니터링 대상에 따라 적합한 command_type을 선택합니다.

| Command Type | 용도 | 실행 방식 | 출력 기대 | Debug Tool |
|---|---|---|---|---|
| **tool** (기본) | UFS 전용 tool | `adb shell '명령어'` | JSON 직접 출력 | 필요 |
| **sysfs** | sysfs/proc 경로 + 정규식 | `adb shell cat 경로` | 정규식 파싱 → JSON | 불필요 |
| **keyvalue** | key:value 형태 출력 | `adb shell cat 경로` | 자동 파싱 → JSON | 불필요 |
| **raw** | 테이블/비트맵 등 | `adb shell cat 경로` | 텍스트 그대로 저장 | 불필요 |

### tool

```
/data/local/tmp/ufs-utils /dev/block/sda ssr --json
```
adb shell로 명령어 실행, JSON 출력을 그대로 사용.

### sysfs

```
/sys/block/sda/size
/sys/block/sda/stat | regex:(\d+)\s+\d+\s+(\d+) | keys:read_ios,read_sectors
/proc/meminfo | regex:MemTotal:\s+(\d+) | keys:mem_total_kb
```

| 구성 | 필수 | 설명 |
|------|------|------|
| 경로 | 필수 | `adb shell cat`으로 읽을 파일 |
| `regex:패턴` | 선택 | 캡처 그룹으로 값 추출 |
| `keys:키1,키2` | 선택 | 캡처 그룹에 대응하는 JSON key |

### keyvalue

```
/proc/meminfo
/proc/fs/f2fs/sda1/status
/proc/fs/ext4/sda1/mb_stats
```

`key: value` 형태 출력을 자동 파싱합니다:
- 들여쓰기 → dot notation (`mballoc.reqs: 15234`)
- 숫자 자동 추출 (단위 `kB`, `ms`, `%` 제거)
- `- ` 접두사 제거
- 괄호 내 값 추출 (`GC calls: 234 (BG: 189)` → `GC_calls: 234`, `GC_calls_BG: 189`)
- `#`, `=`, `[...]` 주석/헤더 줄 자동 무시

**파싱 예시:**
```
입력 (cat /proc/meminfo):
  MemTotal:       16384532 kB
  MemFree:         2345678 kB
  Dirty:              1234 kB

결과 JSON:
  {"MemTotal": 16384532, "MemFree": 2345678, "Dirty": 1234}
```

```
입력 (cat /proc/fs/ext4/sda1/mb_stats):
  mballoc:
      reqs: 15234
      success: 15120
      cr_p2_aligned_stats:
          hits: 8234

결과 JSON:
  {"mballoc.reqs": 15234, "mballoc.success": 15120, "mballoc.cr_p2_aligned_stats.hits": 8234}
```

### raw

```
/proc/fs/ext4/sda1/mb_groups
/proc/fs/f2fs/sda1/segment_info
/proc/fs/f2fs/sda1/victim_bits
```

출력 전체를 텍스트 그대로 JSON 문자열로 저장. 테이블/비트맵 등 파싱이 어려운 출력용. Tree View에서 확인.

---

## 백엔드 구조

### 패키지 구성

```
com.samsung.move.metadata/
├── config/
│   └── MetadataMonitorProperties    — yaml 설정값 바인딩
├── controller/
│   ├── MetadataController           — 모니터링 데이터 조회, 슬롯 ON/OFF, 주기/타입 설정
│   └── MetadataAdminController      — 타입/명령어/매핑 CRUD (Admin)
├── service/
│   ├── MetadataMonitorService       — 핵심: 상태 감지 + 모니터링 스케줄링
│   ├── MetadataCommandExecutor      — SSH/ADB 명령 실행 (4가지 command_type)
│   └── MetadataTypeService          — Admin CRUD
├── entity/
│   ├── UfsMetadataType
│   ├── UfsMetadataCommand
│   └── UfsProductMetadata
└── repository/
```

---

## MetadataMonitorService (핵심)

### 상태 감지 (5초마다)

`@Scheduled(fixedDelay = 5000ms)`로 모든 슬롯의 `testState` 변화를 감지합니다.

| 이전 상태 | 현재 상태 | 동작 |
|-----------|-----------|------|
| 비실행 | **Running** | `startMonitoring()` — 모니터링 시작 |
| **Running** | 비실행 | `stopMonitoring()` — 모니터링 중지 + 최종 저장 |
| **Running** (TC A) | **Running** (TC B) | TC 전환 — A 중지 → B 시작 (time=0 리셋) |

### 슬롯별 설정 (3가지)

| 설정 | 기본값 | 설명 |
|------|--------|------|
| **모니터링 ON/OFF** | OFF | MetadataDialog에서 토글 |
| **모니터링 주기** | 전역 기본값 (초) | 슬롯별 개별 설정 가능, 최소 10초 |
| **제외 타입** | 없음 (전부 ON) | 타입별 ON/OFF 토글 |

### 모니터링 시작 (startMonitoring)

```
1. enabledSlots 체크 → 비활성이면 return
2. DB: UfsProductMetadata 조회 → 이 제품이 지원하는 타입 목록
3. excludedTypes 필터링 → 사용자가 OFF한 타입 제외
4. DB: UfsMetadataCommand 조회 → 각 타입별 명령어
5. SlotMonitorContext 생성
6. Debug tool push (adb push, 중복 방지)
7. ScheduledExecutorService에 주기적 모니터링 등록 (N초 간격)
```

### 모니터링 실행 (monitorOnce, N초마다)

```
1. ReentrantLock으로 동시 실행 방지
2. 각 명령어에 대해 command_type 분기:
   ├── "tool"     → executeCommand() → JSON 직접
   ├── "sysfs"    → executeSysfsRead() → 정규식 파싱
   ├── "keyvalue" → executeKeyValue() → key:value 자동 파싱
   └── "raw"      → executeRaw() → 텍스트 그대로
3. "time" 필드 추가 (경과 초)
4. CopyOnWriteArrayList에 누적 (인메모리)
5. VM에 JSON 파일 저장 (SFTP)
```

### 모니터링 중지 (stopMonitoring)

```
1. ScheduledFuture.cancel(true) — 실행 중이면 인터럽트
2. monitorOnce() 최종 1회 실행 (마지막 데이터)
3. activeMonitors에서 제거
```

---

## 스레드 모델

| 스레드 풀 | 역할 |
|-----------|------|
| **Spring Scheduled** | `checkSlotStateChanges()` — 5초마다 상태 감지 |
| **metadata-monitor** (8개 데몬) | 슬롯별 `monitorOnce()` 주기 실행 |
| **metadata-cmd-timeout** (캐시) | SSH 명령어 timeout 감시 (60초/120초) |

### Thread Safety

| 데이터 | 동시성 전략 |
|--------|------------|
| activeMonitors | `ConcurrentHashMap` |
| enabledSlots | `ConcurrentHashMap.newKeySet()` |
| excludedTypes | `ConcurrentHashMap<String, Set>` |
| slotIntervalSeconds | `ConcurrentHashMap<String, Integer>` |
| monitoredData | `CopyOnWriteArrayList` |
| 경과 시간 | `AtomicInteger` (초 단위) |
| monitorOnce() | `ReentrantLock.tryLock()` |

---

## 프론트엔드

### MetadataDialog

슬롯 카드 Context Menu 또는 TC 테이블의 Meta 버튼에서 열립니다.

**멀티 슬롯 지원:**
- 단일 슬롯: 탭 없이 직접 표시
- 여러 슬롯 선택: 상단 슬롯 탭으로 전환, 각 탭별 독립 데이터

**모니터링 컨트롤 바:**
```
모니터링: [ON]    주기: [60] 초  [적용]  (기본: 300초)  ● 4m 55s
```
- ON/OFF 토글: 슬롯별 모니터링 시작/중지
- 주기 입력: 초 단위 (최소 10초), 슬롯별 개별 설정
- 기본값 대비 차이 표시

**타입 선택기:**
```
[Health ON●] [Write Booster ON●] [EC Count OFF] [f2fs Status ON●]
```
- 타입별 ON/OFF 토글 — OFF하면 다음 모니터링부터 해당 타입 제외
- 초록색 점: 현재 모니터링 중

**4가지 차트:**

| 데이터 타입 | 차트 종류 | 설명 |
|-------------|-----------|------|
| 숫자 (number) | 라인/산점도 | X축=time, Y축=값, 키별 선택/해제 |
| 숫자 배열 (number[]) | **히트맵** | X축=time, Y축=index, 색상=값 |
| 문자열 (string) | Table만 | Table/Tree View에서 표시 |
| 중첩 객체 | Tree View | 원본 JSON 트리 |

**데이터 처리 파이프라인:**

```
JSON 배열
  → flattenObject()     중첩 JSON → dot notation ("gc.nMinEc")
  → classifyKeys()      number / string / object / array 분류
  → applyDelta()        선택된 키만 이전 값과의 차이로 변환
  → Chart (라인/히트맵) / Table / Tree View
```

**Delta 모드:**
- 숫자: `[100, 105, 112]` → `[0, 5, 7]`
- 배열: 원소별 delta 적용

**Export:**
- **Excel**: 선택된 키 + delta 적용 상태로 `.xlsx` 다운로드
- **JSON**: 원본 JSON 그대로 `.json` 다운로드

**데이터 소스 분기:**

| 상태 | 데이터 소스 |
|------|------------|
| TC Running + 모니터링 중 | 인메모리 (30초 자동 새로고침) |
| TC 완료 + logPath 있음 | VM의 JSON 파일 (SFTP) |
| Slots 페이지 + 완료 | VM의 `/home/octo/tentacle/slot{N}/log/debug_{typeKey}.json` |

### Admin — Metadata 관리

3개 섹션:
- **Types**: 메타데이터 종류 CRUD
- **Commands**: 타입별 명령어 CRUD (4가지 command_type 선택, 타입별 도움말)
- **Product Mappings**: 제품-타입 매핑 (체크박스 다중 선택, 그룹 테이블 표시)

---

## API

### 모니터링 데이터

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/metadata/types/for-product` | 제품별 지원 타입 조회 |
| GET | `/api/metadata/slot/{t}/{s}/status` | 슬롯 모니터링 상태 |
| GET | `/api/metadata/slot/{t}/{s}/data` | 슬롯 모니터링 데이터 |
| GET | `/api/metadata/file` | 저장된 JSON 파일 조회 |

### 슬롯 설정

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET/PUT | `/api/metadata/slot/{t}/{s}/enabled` | 모니터링 ON/OFF |
| GET/PUT | `/api/metadata/slot/{t}/{s}/interval` | 모니터링 주기 (초) |
| GET/PUT | `/api/metadata/slot/{t}/{s}/excluded-types` | 제외 타입 목록 |

### 전역 설정

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET/PUT | `/api/metadata/config` | 전역 모니터링 설정 |

---

## 설정

```yaml
metadata:
  monitor:
    enabled: true              # 모니터링 활성화
    poll-interval-ms: 5000     # 상태 체크 간격 (5초)
    collection-interval-min: 5 # 기본 모니터링 간격 (5분 = 300초)
```

- 전역 기본값: yaml에서 설정 (분 단위, 내부에서 ×60 변환)
- 슬롯별 개별 설정: API로 초 단위 설정 (최소 10초)
- 슬롯별 설정이 있으면 전역보다 우선

---

## Lifecycle

### 서버 시작
- `MetadataMonitorService` 빈 생성
- Monitor 스레드 풀 (8개) 생성
- 5초 간격 상태 체크 자동 시작

### 모니터링 중
- `activeMonitors`에 슬롯별 `SlotMonitorContext` 유지
- 각 Context: ScheduledFuture + 모니터링 결과 리스트 + 경과 시간 (초)
- VM에 주기적으로 JSON 파일 덮어쓰기

### 서버 종료
- `@PreDestroy`: 모든 모니터링 취소 + 스레드 풀 shutdown
- 슬롯별 설정 (ON/OFF, 주기, 제외 타입)은 인메모리 → 서버 재시작 시 초기화
