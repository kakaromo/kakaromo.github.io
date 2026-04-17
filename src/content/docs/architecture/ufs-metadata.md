---
title: UFS Metadata 아키텍처
description: UFS 메타데이터 모니터링 시스템의 설계, 데이터 흐름, Command Type, 멀티 슬롯 지원
---

## 한줄 요약

**TC 평가 중** UFS 디바이스/파일시스템의 상태(SSR, Telemetry, ext4/f2fs stats, meminfo 등)를 **N초 간격으로 자동 모니터링**하여 JSON 파일로 저장하고, 시간별 변화를 차트/히트맵/테이블로 보여주는 시스템입니다.

---

## 전체 흐름

```mermaid
sequenceDiagram
    participant U as 사용자
    participant D as MetadataDialog
    participant M as MonitorService
    participant DB as Portal DB
    participant VM as Tentacle VM
    participant DEV as UFS Device

    U->>D: 모니터링 ON
    D->>M: enableSlot(T1:0)

    Note over M: 5초마다 상태 체크
    M->>M: testState → Running 감지

    M->>DB: ProductMetadata 조회
    DB-->>M: 지원 타입 목록
    M->>M: excludedTypes 필터링

    M->>VM: adb push (debug tool)
    Note over M: N초 간격 모니터링 시작

    loop 매 N초
        M->>VM: SSH → adb shell (command_type별)
        VM->>DEV: cat/shell 실행
        DEV-->>VM: JSON/텍스트 응답
        VM-->>M: 결과 반환
        M->>M: 인메모리 누적 + time 추가
        M->>VM: SFTP → JSON 파일 저장
    end

    Note over M: testState → 비실행 감지
    M->>M: 최종 1회 모니터링
    M->>M: activeMonitors에서 제거
```

---

## 데이터 모델

```mermaid
erDiagram
    ufs_metadata_types {
        BIGINT id PK
        VARCHAR name "SSR, f2fs Status"
        VARCHAR type_key "ssr, f2fs_status"
        VARCHAR category "common / feature"
        BOOLEAN enabled
    }

    ufs_metadata_commands {
        BIGINT id PK
        BIGINT metadata_type_id FK
        VARCHAR command_type "tool / sysfs / keyvalue / raw"
        VARCHAR command_template
        BIGINT debug_tool_id FK
    }

    ufs_product_metadata {
        BIGINT id PK
        VARCHAR controller "NULL = 와일드카드"
        VARCHAR cell_type "NULL = 와일드카드"
        VARCHAR nand_type "NULL = 와일드카드"
        VARCHAR oem "NULL = 와일드카드"
        BIGINT metadata_type_id FK
    }

    debug_tools {
        BIGINT id PK
        VARCHAR tool_name
        VARCHAR tool_path
    }

    ufs_metadata_types ||--o{ ufs_metadata_commands : has
    ufs_metadata_types ||--o{ ufs_product_metadata : has
    debug_tools ||--o{ ufs_metadata_commands : uses
```

- NULL 필드 = **모든 값에 매칭** (와일드카드)
- Admin UI에서 체크박스로 여러 타입 한번에 등록, UFS Info DB에서 select
- 같은 product 조건의 매핑은 그룹으로 표시

---

## Command Type (4가지)

```mermaid
flowchart LR
    CMD[command_type] --> TOOL[tool]
    CMD --> SYSFS[sysfs]
    CMD --> KV[keyvalue]
    CMD --> RAW[raw]

    TOOL -->|"adb shell '명령어'"| JSON1[JSON 직접 출력]
    SYSFS -->|"adb shell cat + regex"| JSON2[정규식 → JSON]
    KV -->|"adb shell cat"| JSON3["key:value 자동 파싱"]
    RAW -->|"adb shell cat"| TXT[텍스트 그대로 저장]
```

| Command Type | 용도 | Debug Tool |
|---|---|---|
| **tool** | UFS 전용 tool (JSON 출력) | 필요 |
| **sysfs** | sysfs/proc 경로 + 정규식 | 불필요 |
| **keyvalue** | meminfo, ext4/f2fs stats 등 key:value 형태 | 불필요 |
| **raw** | 테이블/비트맵 등 파싱 어려운 출력 | 불필요 |

### tool

```
/data/local/tmp/ufs-utils /dev/block/sda ssr --json
```

### sysfs

```
/sys/block/sda/size
/sys/block/sda/stat | regex:(\d+)\s+\d+\s+(\d+) | keys:read_ios,read_sectors
```

### keyvalue

```
/proc/meminfo
/proc/fs/f2fs/sda1/status
```

자동 파싱: 들여쓰기 → dot notation, 숫자 추출, 단위 제거, 괄호 내 값 추출

**예시:**
```
MemTotal: 16384532 kB   →  {"MemTotal": 16384532}
mballoc:
    reqs: 15234         →  {"mballoc.reqs": 15234}
GC calls: 234 (BG: 189) →  {"GC_calls": 234, "GC_calls_BG": 189}
```

### raw

```
/proc/fs/ext4/sda1/mb_groups
/proc/fs/f2fs/sda1/segment_info
```

텍스트 그대로 저장, Tree View에서 확인.

---

## 백엔드 구조

```mermaid
flowchart TD
    subgraph Controller
        MC[MetadataController<br/>데이터 조회 · 슬롯 설정]
        MAC[MetadataAdminController<br/>타입 · 명령어 · 매핑 CRUD]
    end

    subgraph Service
        MMS[MetadataMonitorService<br/>상태 감지 · 스케줄링]
        MCE[MetadataCommandExecutor<br/>SSH/ADB 명령 실행]
        MTS[MetadataTypeService<br/>Admin CRUD]
    end

    subgraph Config
        MMP[MetadataMonitorProperties<br/>yaml 설정]
    end

    Controller --> Service
    Service --> Config
    MMS --> MCE
```

---

## 모니터링 라이프사이클

```mermaid
stateDiagram-v2
    [*] --> Disabled: 서버 시작 (기본 OFF)
    Disabled --> Enabled: 사용자가 ON 토글
    Enabled --> Disabled: 사용자가 OFF 토글

    Enabled --> Monitoring: TC Running 감지
    Monitoring --> Enabled: TC 종료 감지
    Monitoring --> Monitoring: TC 전환 (A→B)

    state Monitoring {
        [*] --> StartMonitoring
        StartMonitoring --> MonitorOnce: 즉시 실행
        MonitorOnce --> Wait: N초 대기
        Wait --> MonitorOnce: 주기 도래
        MonitorOnce --> StopMonitoring: TC 종료 감지
        StopMonitoring --> [*]
    }
```

### 슬롯별 설정 (3가지)

| 설정 | 기본값 | 설명 |
|------|--------|------|
| **모니터링 ON/OFF** | OFF | MetadataDialog에서 토글 |
| **모니터링 주기** | 전역 기본값 (초) | 슬롯별 개별 설정 가능, 최소 10초 |
| **제외 타입** | 없음 (전부 ON) | 타입별 ON/OFF 토글 |

---

## 모니터링 실행 흐름 (monitorOnce)

```mermaid
sequenceDiagram
    participant S as Scheduler
    participant M as monitorOnce()
    participant E as CommandExecutor
    participant VM as Tentacle VM
    participant DEV as Device (adb)

    S->>M: 주기 도래
    M->>M: ReentrantLock.tryLock()
    alt 이미 실행 중
        M-->>S: skip
    end

    loop 각 UfsMetadataCommand
        alt command_type = tool
            M->>E: executeCommand()
            E->>VM: SSH → adb shell '명령어'
            VM->>DEV: 실행
            DEV-->>VM: JSON 출력
            VM-->>E: 결과
        else command_type = sysfs
            M->>E: executeSysfsRead()
            E->>VM: SSH → adb shell cat + regex 파싱
            VM-->>E: JSON 변환 결과
        else command_type = keyvalue
            M->>E: executeKeyValue()
            E->>VM: SSH → adb shell cat
            VM-->>E: key:value 자동 파싱 결과
        else command_type = raw
            M->>E: executeRaw()
            E->>VM: SSH → adb shell cat
            VM-->>E: 텍스트 그대로
        end
        E-->>M: JSON 문자열
        M->>M: time 필드 추가 + 인메모리 누적
    end

    M->>VM: SFTP → debug_{typeKey}.json 저장
    M->>M: elapsedSeconds += interval
    M->>M: unlock()
```

---

## 스레드 모델

```mermaid
flowchart TD
    subgraph SpringScheduled["Spring @Scheduled"]
        CHECK[checkSlotStateChanges<br/>5초 간격]
    end

    subgraph MonitorPool["metadata-monitor (8 threads)"]
        S1[Slot A: monitorOnce]
        S2[Slot B: monitorOnce]
        S3[...]
    end

    subgraph TimeoutPool["metadata-cmd-timeout (cached)"]
        T1["SSH exec + Future.get(60s)"]
        T2["timeout → cancel + disconnect"]
    end

    CHECK -->|start/stop| MonitorPool
    MonitorPool --> TimeoutPool
```

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

## 프론트엔드 데이터 파이프라인

```mermaid
flowchart LR
    A[JSON 배열] --> B[flattenObject<br/>중첩 → dot notation]
    B --> C[classifyKeys<br/>number · string · array · object]
    C --> D[applyDelta<br/>선택 키만 차분 변환]
    D --> E1[Line/Scatter Chart<br/>숫자 키]
    D --> E2[Heatmap<br/>배열 키]
    D --> E3[DataTable<br/>전체 키]
    D --> E4[Tree View<br/>원본 JSON]
```

### MetadataDialog

슬롯 카드 Context Menu 또는 TC 테이블의 Meta 버튼에서 열립니다.

**멀티 슬롯 지원:**
- 단일 슬롯: 탭 없이 직접 표시
- 여러 슬롯 선택: 상단 슬롯 탭으로 전환, 각 탭별 독립 데이터

**모니터링 컨트롤 바:**
```
모니터링: [ON]    주기: [60] 초  [적용]  (기본: 300초)  ● 4m 55s
```

**타입 선택기:**
```
[Health ON●] [Write Booster ON●] [EC Count OFF] [f2fs Status ON●]
```

**Export:**
- **Excel**: 선택된 키 + delta 적용 상태로 `.xlsx` 다운로드
- **JSON**: 원본 JSON 그대로 `.json` 다운로드

**데이터 소스 분기:**

```mermaid
flowchart TD
    OPEN[MetadataDialog 열림] --> CHECK{TC 상태?}
    CHECK -->|Running + 모니터링 중| LIVE[인메모리 데이터<br/>30초 자동 새로고침]
    CHECK -->|완료 + logPath| FILE1[VM JSON 파일<br/>logPath 기반]
    CHECK -->|완료 + logPath 없음| FILE2["VM JSON 파일<br/>/slot{N}/log/debug_{type}.json"]
```

### Admin — Metadata 관리

3개 섹션 (모두 DataTable):
- **Types**: 메타데이터 종류 CRUD
- **Commands**: 타입별 명령어 CRUD (4가지 command_type)
- **Product Mappings**: 제품-타입 매핑 (UFS Info DB에서 select, 체크박스 다중 선택, 그룹 표시, 수정/삭제)

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

```mermaid
sequenceDiagram
    participant APP as Spring Boot
    participant MMS as MonitorService
    participant POOL as Thread Pool (8)

    Note over APP: 서버 시작
    APP->>MMS: @PostConstruct
    MMS->>POOL: 8개 데몬 스레드 생성
    MMS->>MMS: @Scheduled 상태 체크 시작 (5초)

    Note over MMS: 모니터링 중
    MMS->>POOL: 슬롯별 monitorOnce 스케줄 등록
    POOL->>POOL: N초 간격 실행

    Note over APP: 서버 종료
    APP->>MMS: @PreDestroy
    MMS->>POOL: 모든 future.cancel(true)
    MMS->>MMS: activeMonitors 클리어
    MMS->>POOL: executor.shutdownNow()
```

- 슬롯별 설정 (ON/OFF, 주기, 제외 타입)은 인메모리 → 서버 재시작 시 초기화
