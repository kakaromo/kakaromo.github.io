---
title: UFS Metadata 아키텍처
description: UFS 메타데이터 수집 시스템의 설계, 데이터 흐름, 스레드 모델 및 주요 컴포넌트
---

## 한줄 요약

**TC 평가 중** UFS 디바이스의 상태(SSR, Telemetry 등)를 **N분 간격으로 자동 수집**하여 JSON 파일로 저장하고, 시간별 변화를 차트/테이블로 보여주는 시스템입니다.

---

## 전체 흐름

```
[슬롯에서 TC 시작]
    ↓
MetadataMonitorService (5초마다 상태 체크)
    ↓ testState가 "Running"으로 변경 감지
    ↓
[수집 시작]
    ├── 1. 이 UFS 제품이 지원하는 메타데이터 타입 조회 (DB)
    ├── 2. 필요한 debug tool을 디바이스에 push (adb push)
    └── 3. N분 간격으로 수집 반복 (adb shell 명령 실행)
         ↓
    수집 결과를 JSON 배열로 누적
    ├── 인메모리 저장 (실시간 조회용)
    └── VM에 JSON 파일 저장 (나중에 History에서 조회용)
         ↓
[TC 종료 감지]
    └── 수집 중지 + 최종 결과 저장
```

---

## 데이터 모델

3개 테이블로 "어떤 제품에, 어떤 명령어로, 어떤 메타데이터를 수집할지"를 관리합니다.

### 테이블 관계

```
ufs_metadata_types (메타데이터 종류)
    │   예: SSR, Telemetry, Read10Debug
    │
    ├── ufs_metadata_commands (수집 명령어)
    │       각 타입별 adb 명령어 또는 sysfs 경로
    │       필요한 debug tool 바이너리 연결
    │
    └── ufs_product_metadata (제품별 지원 매핑)
            어떤 controller/nandType/cellType 조합이
            어떤 메타데이터 타입을 지원하는지
```

### 테이블 상세

#### ufs_metadata_types

| 컬럼 | 설명 | 예시 |
|------|------|------|
| name | 표시 이름 | SSR |
| type_key | URL/파일명용 키 | ssr |
| category | `common` (공통) 또는 `feature` | common |
| enabled | 활성화 여부 | true |

#### ufs_metadata_commands

| 컬럼 | 설명 | 예시 |
|------|------|------|
| metadata_type_id | 연결된 타입 | → SSR |
| command_type | 실행 방식 | `tool` 또는 `sysfs` |
| command_template | 실행할 명령어 | `/data/local/tmp/ufs-utils /dev/block/sda ssr --json` |
| debug_tool_id | 필요한 바이너리 | → ufs-utils (tool 모드만) |

**command_type 차이:**

| | tool (기본) | sysfs |
|---|---|---|
| 실행 방식 | `adb shell '명령어'` | `adb shell cat 경로` (여러 줄) |
| 출력 기대 | JSON 직접 출력 | plaintext → 정규식으로 JSON 변환 |
| debug tool | 필요 | 불필요 |
| 사용 예 | UFS 전용 tool | /sys/block/sda/stat, /proc/meminfo |

#### ufs_product_metadata

| 컬럼 | 설명 |
|------|------|
| controller | UFS 컨트롤러 (NULL = 모든 값 매칭) |
| cell_type | 셀 타입 (NULL = 모든 값 매칭) |
| nand_type | NAND 타입 (NULL = 모든 값 매칭) |
| metadata_type_id | 지원하는 메타데이터 타입 |

NULL 필드는 **와일드카드**입니다. controller만 지정하고 나머지를 비우면 해당 controller의 모든 제품에 매칭됩니다.

---

## 백엔드 구조

### 패키지 구성

```
com.samsung.move.metadata/
├── config/
│   └── MetadataCollectionProperties  — yaml 설정값 바인딩
├── controller/
│   ├── MetadataController            — 수집 데이터 조회, 슬롯 토글, 설정 변경
│   └── MetadataAdminController       — 타입/명령어/매핑 CRUD (Admin)
├── service/
│   ├── MetadataMonitorService        — 핵심: 상태 감지 + 수집 스케줄링
│   ├── MetadataCommandExecutor       — SSH/ADB 명령 실행 + timeout
│   └── MetadataTypeService           — Admin CRUD
├── entity/
│   ├── UfsMetadataType
│   ├── UfsMetadataCommand
│   └── UfsProductMetadata
└── repository/
    ├── UfsMetadataTypeRepository
    ├── UfsMetadataCommandRepository
    └── UfsProductMetadataRepository
```

---

## MetadataMonitorService (핵심)

### 상태 감지 (5초마다)

`@Scheduled(fixedDelay = 5000ms)`로 모든 슬롯의 `testState` 변화를 감지합니다.

| 이전 상태 | 현재 상태 | 동작 |
|-----------|-----------|------|
| 비실행 | **Running** | `startCollection()` — 수집 시작 |
| **Running** | 비실행 | `stopCollection()` — 수집 중지 + 최종 저장 |
| **Running** (TC A) | **Running** (TC B) | TC 전환 — A 중지 → B 시작 (time=0 리셋) |

### 슬롯 활성화 (기본값 OFF)

```java
Set<String> enabledSlots;  // key = "tentacleName:slotNumber"
```

- 사용자가 UI에서 명시적으로 ON 해야 수집 시작
- ON이어도 TC가 Running 상태가 되어야 실제 수집 시작
- OFF로 전환하면 진행 중인 수집도 즉시 중지

### 수집 시작 (startCollection)

```
1. enabledSlots 체크 → 비활성이면 return
2. DB: UfsProductMetadata 조회 → 이 제품이 지원하는 타입 목록
3. DB: UfsMetadataCommand 조회 → 각 타입별 수집 명령어
4. SlotCollectionContext 생성 (상태 추적용)
5. Debug tool push (adb push, 중복 방지)
6. ScheduledExecutorService에 주기적 수집 등록 (N분 간격)
```

### 수집 실행 (collectOnce, N분마다)

```
1. ReentrantLock으로 동시 실행 방지
2. 각 명령어에 대해:
   ├── command_type이 "tool"이면:
   │   └── adb shell '명령어' 실행 → JSON 응답
   └── command_type이 "sysfs"이면:
       └── 각 경로별 adb shell cat → 정규식 파싱 → JSON 조합
3. "time" 필드 추가 (경과 분)
4. CopyOnWriteArrayList에 누적 (인메모리)
5. VM에 JSON 파일 저장 (SFTP)
```

### 수집 중지 (stopCollection)

```
1. ScheduledFuture.cancel(true) — 실행 중이면 인터럽트
2. collectOnce() 최종 1회 실행 (마지막 데이터 수집)
3. activeCollections에서 제거
```

---

## 스레드 모델

3개의 스레드 풀이 역할을 분담합니다.

### 1. Spring Scheduled Thread

`checkSlotStateChanges()` — 5초마다 실행. 슬롯 상태 변화를 감지하고 수집을 시작/중지합니다.

### 2. Metadata Collector Pool (8개 데몬 스레드)

`ScheduledExecutorService` — 슬롯별로 N분 간격의 `collectOnce()` 태스크를 실행합니다.
여러 슬롯이 동시에 수집할 수 있으므로 8개 스레드를 사용합니다.

### 3. Command Timeout Pool (캐시 스레드)

`ExecutorService` (cached) — SSH 명령어 실행 시 timeout 감시용.
`Future.get(60초)`로 대기하고, timeout 발생 시 명령어를 강제 종료합니다.

### Thread Safety

| 데이터 | 동시성 전략 |
|--------|------------|
| activeCollections | `ConcurrentHashMap` |
| enabledSlots | `ConcurrentHashMap.newKeySet()` |
| previousStates | `ConcurrentHashMap` |
| 수집 결과 리스트 | `CopyOnWriteArrayList` |
| 경과 시간 | `AtomicInteger` |
| collectOnce() | `ReentrantLock.tryLock()` (동시 실행 방지) |

---

## Timeout 처리

디바이스 상태 이상으로 adb 명령이 hang될 수 있습니다.

```
MetadataCommandExecutor.execCommandWithTimeout()
  1. SSH ChannelExec 열기
  2. 별도 스레드에서 출력 읽기 시작
  3. Future.get(timeoutSeconds) 으로 대기
     ├── 정상 완료 → 결과 반환
     └── TimeoutException → future.cancel(true) + channel 강제 종료
  4. channel.disconnect()
```

| 명령 유형 | Timeout |
|-----------|---------|
| adb shell (메타데이터 수집) | 60초 |
| adb push (tool 전송) | 120초 |

---

## Sysfs 명령어 포맷

Tool 모드는 직관적이지만, Sysfs 모드는 특별한 포맷을 사용합니다.

### 기본 구조

```
경로 | regex:패턴 | keys:키1,키2
```

### 예시

```
/sys/block/sda/size
/sys/block/sda/stat | regex:(\d+)\s+\d+\s+(\d+) | keys:read_ios,read_sectors
/proc/meminfo | regex:MemTotal:\s+(\d+) | keys:mem_total_kb
```

### 동작 설명

| 줄 | 실행 | 결과 |
|----|------|------|
| `/sys/block/sda/size` | `cat /sys/block/sda/size` → `125034840` | `{"size": "125034840"}` |
| `/sys/block/sda/stat \| regex:... \| keys:...` | `cat /sys/block/sda/stat` → 정규식 캡처 | `{"read_ios": "1234", "read_sectors": "56789"}` |
| `/proc/meminfo \| regex:... \| keys:...` | `cat /proc/meminfo` → 정규식 캡처 | `{"mem_total_kb": "3891204"}` |

- **경로만**: 전체 출력을 값으로, 경로 마지막 세그먼트를 key로 사용
- **regex 추가**: 정규식 캡처 그룹으로 원하는 값만 추출
- **keys 추가**: 캡처 그룹에 대응하는 JSON key (생략 시 경로 마지막 세그먼트)

---

## 프론트엔드

### MetadataDialog 컴포넌트

슬롯이나 History에서 "Meta" 버튼을 클릭하면 열립니다.

**3개 탭:**

| 탭 | 표시 내용 | 대상 |
|----|-----------|------|
| **Chart** | 숫자 필드를 시간축 라인/산점도 차트로 | number 타입 필드 |
| **Table** | 전체 필드를 DataTable로 | 모든 필드 |
| **Tree View** | 원본 JSON을 트리로 펼쳐보기 | 중첩 object 포함 |

**데이터 처리 파이프라인:**

```
수집된 JSON 배열
  → flattenObject()     중첩 JSON을 "gc.nMinEc" 같은 dot notation으로 평탄화
  → classifyKeys()      number/string/object 키 분류
  → applyDelta()        선택된 키만 이전 값과의 차이로 변환
  → Chart / Table        최종 표시
```

**Delta 모드**: 누적 값을 변화량으로 변환합니다.
- 원본: `100, 105, 112, 120`
- Delta: `0, 5, 7, 8`

**데이터 소스 분기:**

| 상태 | 데이터 소스 |
|------|------------|
| TC Running 중 + 수집 중 | 인메모리 (30초 자동 새로고침) |
| TC 완료 + logPath 있음 | VM의 JSON 파일 (SFTP) |
| Slots 페이지 + 수집 완료 | VM의 `/home/octo/tentacle/slot{N}/log/debug_{typeKey}.json` |

### AdminMetadataTab

Admin 페이지의 Metadata 탭에서 3개 섹션을 관리합니다:
- **Types**: 메타데이터 종류 CRUD
- **Commands**: 타입별 수집 명령어 CRUD (command_type 선택)
- **Product Mappings**: 제품-타입 매핑 CRUD

---

## 설정

```yaml
metadata:
  monitor:
    enabled: true              # 모니터링 활성화
    poll-interval-ms: 5000     # 상태 체크 간격 (5초)
    collection-interval-min: 5 # 수집 간격 (5분)
```

런타임 변경 API:
```
PUT /api/metadata/config
{"collectionIntervalMin": 1}
```

변경 시 **새로 시작되는 수집**부터 적용됩니다. 진행 중인 수집은 기존 간격을 유지합니다.

---

## Lifecycle

### 서버 시작
- `MetadataMonitorService` 빈 생성
- Collector 스레드 풀 (8개) 생성
- 5초 간격 상태 체크 자동 시작

### 수집 중
- `activeCollections`에 슬롯별 Context 유지
- 각 Context: ScheduledFuture + 수집 결과 리스트 + 경과 시간
- VM에 주기적으로 JSON 파일 덮어쓰기

### 서버 종료
- `@PreDestroy`: 모든 수집 취소 + 스레드 풀 shutdown
