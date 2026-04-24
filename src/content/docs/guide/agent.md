---
title: Agent (디바이스 평가)
description: Android 디바이스의 벤치마크, 시나리오 캔버스 빌더, 앱 매크로 자동화, I/O trace 분석까지 종합 가이드
---

Agent 페이지는 Android 디바이스에 연결된 Go gRPC 서버(DeviceAgent)를 통해 스토리지 성능 벤치마크, 시나리오 실행, 앱 매크로 자동화, I/O trace 수집/분석을 수행합니다.

---

## 1. 3-Panel 레이아웃

Agent 페이지는 세 개의 패널과 플로팅 카드로 구성됩니다.

| 영역 | 컴포넌트 | 역할 |
|------|----------|------|
| **좌측 패널** | `AgentContextPanel` | 서버 카드 선택, 디바이스 체크리스트, 스토리지 실시간 표시, Quick Actions |
| **센터 패널** | 모드별 전환 | Benchmark / Scenario / Trace / Results / Schedule / App Macro |
| **우측 시트** | on-demand | 서버 관리, 모니터링 차트, 결과 상세, Trace 분석, 디바이스 화면 |
| **플로팅 카드** | `AgentFloatingJobCard` | 실행 중인 Job 진행률 (하단 우측 고정) |

### 좌측 패널 상세

**서버 카드**: 등록된 Agent 서버가 카드 형태로 나열됩니다. 카드 클릭으로 서버를 선택하고, 설정 아이콘으로 서버 관리 시트를 엽니다.

**디바이스 체크리스트**: 선택한 서버에 연결된 디바이스가 자동 로드됩니다. 체크박스로 벤치마크/시나리오에 사용할 디바이스를 선택합니다. 디바이스별로 모니터링(📊), 화면 보기(📱) 아이콘을 제공합니다.

**스토리지 실시간 표시**: 선택된 디바이스의 `/data` 파티션 사용량을 프로그레스 바로 표시합니다.
- 90% 초과: 빨간 경고
- 70% 초과: 주황 경고
- 2초 간격 자동 갱신

**Quick Actions**: Benchmark, Scenario, Trace, Results, Schedule 모드를 빠르게 전환하는 버튼 그룹입니다.

---

## 2. 서버 관리

우측 시트(`AgentServerSheet`)에서 Agent 서버를 관리합니다.

### CRUD 작업

| 작업 | 설명 |
|------|------|
| **추가** | Name, Host, Port(기본 50051) 입력 → 접속 테스트 → 저장 |
| **접속 테스트** | `ListDevices` gRPC 호출로 연결 확인, 성공 시 디바이스 목록 반환 |
| **수정** | 기존 서버의 이름, 호스트, 포트 변경 |
| **삭제** | 서버 삭제 시 해당 gRPC 채널도 함께 정리 |

### 동적 gRPC 채널

서버별 host:port 조합으로 gRPC 채널이 동적으로 생성됩니다. `AgentConnectionManager`가 서버별 채널 라이프사이클을 관리하며, 서버 삭제 시 채널 자원을 자동 해제합니다.

**연결 상태**: 선택된 서버의 연결 dot이 표시됩니다.
- 🟢 초록: 연결됨
- 🔴 빨강: 끊김

:::tip
접속 테스트 버튼으로 서버 저장 전에 연결 상태를 미리 확인하세요. 서버가 실행 중이지 않으면 디바이스 목록이 비어 있다는 메시지가 반환됩니다.
:::

---

## 3. 디바이스 관리

좌측 패널에서 선택한 서버의 디바이스 목록이 자동 로드됩니다.

### 디바이스 정보

| 필드 | 설명 |
|------|------|
| `device_id` | USB 포트 경로 (예: `2-1.1.2`) 또는 TCP serial |
| `serial` | 표시용 시리얼 번호 |
| `manufacturer` | 제조사 |
| `board` / `platform` / `hardware` | 보드 정보 |
| `cpuAbi` | CPU 아키텍처 |
| `buildId` / `sdkVersion` | Android 빌드 정보 |

### 디바이스 상태

| 상태 | 색상 | 설명 |
|------|------|------|
| Online | 🟢 초록 | 사용 가능 |
| Busy | 🟡 노랑 | 작업 실행 중 |
| Offline | ⚪ 회색 | 연결 끊김 |

### ADB 연결/해제

gRPC를 통해 `ConnectDevice` / `DisconnectDevice` 명령을 전달합니다. 디바이스의 ADB 연결 상태를 원격으로 관리할 수 있습니다.

---

## 4. Benchmark 실행

센터 패널에서 "Benchmark" 모드를 선택하여 스토리지 성능 벤치마크를 실행합니다.

### 지원 도구

| 도구 | 설명 |
|------|------|
| **fio** | Flexible I/O Tester — 가장 널리 사용되는 스토리지 벤치마크 |
| **iozone** | 파일시스템 벤치마크 |
| **tiotest** | Threaded I/O 벤치마크 |
| **iotest** | syscall DSL 엔진 — thread × commands 트리 조립 + 실제 `open/pread/pwrite/fsync` 실행. 전용 에디터 + 18 내장 프리셋. 자세한 내용은 [iotest 가이드](/guide/iotest/) |

### fio 옵션

**기본 옵션** (8개):

| 옵션 | 설명 | 기본값 예시 |
|------|------|-------------|
| Block Size (`bs`) | I/O 블록 크기 | `4k` |
| I/O Pattern (`rw`) | read, write, randread, randwrite, randrw 등 | `randwrite` |
| I/O Size (`size`) | 파일 크기 | `1G` |
| Runtime (`runtime`) | 최대 실행 시간 (초) | `60` |
| Num Jobs (`numjobs`) | 동시 Job 수 | `1` |
| I/O Engine (`ioengine`) | libaio, sync, mmap 등 | `libaio` |
| Direct I/O (`direct`) | O_DIRECT 사용 여부 | `1` |
| I/O Depth (`iodepth`) | 큐 깊이 | `32` |

**고급 옵션** (접기/펼치기, 12개):

rwmixread, rwmixwrite, bssplit, bsrange, zonesize, zonerange, chunk, time_based, ramp_time, rate, verify, group_reporting 등

:::note
각 옵션 옆 (?) 아이콘을 클릭하면 해당 파라미터의 도움말이 표시됩니다. 고급 옵션에 없는 파라미터는 하단 textarea에 `key=value` 형식으로 직접 추가할 수 있습니다.
:::

### iozone / tiotest 옵션

각 도구의 실제 CLI usage에 맞는 옵션 폼이 제공됩니다.

- **iozone**: 기본 5개 + 고급 4개
- **tiotest**: 기본 5개 + 고급 4개

### iotest

iotest 는 일반 벤치마크 옵션 폼 대신 **전용 `IOTestEditor`** 를 사용합니다 (thread + commands 트리). 내장 18 프리셋이 있고, `IOTestPreset` 엔티티에 저장한 사용자 프리셋도 선택 가능. 자세한 사용법은 [iotest 가이드](/guide/iotest/).

### 프리셋

자주 사용하는 벤치마크 설정을 DB(`portal_benchmark_presets`)에 저장하고 빠르게 불러올 수 있습니다.

- **저장**: 현재 폼 상태를 이름과 함께 저장
- **불러오기**: 프리셋 선택 시 모든 폼 필드가 자동 채워짐
- **삭제**: 사용하지 않는 프리셋 제거

### 결과 단위

성능 결과는 사용자 친화적 단위로 자동 변환됩니다.

| 메트릭 | 원본 | 표시 |
|--------|------|------|
| IOPS | ops/s | **KIOPS** |
| Bandwidth | KB/s | **MiB/s** |
| Latency | ns | **ms** |
| IO 크기 | bytes | **MiB** |

---

## 5. 시나리오 캔버스 빌더

**@xyflow/svelte 기반 비주얼 캔버스**로 시나리오를 구성합니다. Airflow DAG처럼 노드와 엣지로 복잡한 워크플로우를 편집할 수 있습니다.

### 캔버스 기본 사용법

1. **좌측 팔레트**(`NodePalette`)에서 Step 타입을 캔버스로 **드래그 앤 드롭**
2. 노드 간 **핸들(●) 드래그**로 엣지 연결
3. 노드 hover → **편집(✏) / 삭제(🗑) / 위로(↑) / 아래로(↓)** 아이콘
4. **실행 순서** = Y좌표 기준 (위에 있는 노드가 먼저 실행)
5. 각 노드에 실행 순서 번호(①②③) 자동 표시
6. **MiniMap** + **Controls** (줌/핏) + **Background** 그리드 제공

### Step Types

| Type | 색상 | 설명 |
|------|------|------|
| `benchmark` | 🔵 파랑 | fio/iozone/tiotest/**iotest** 실행, 옵션 폼 편집 가능 |
| `shell` | ⚫ 회색 | 커스텀 쉘 명령 실행 |
| `cleanup` | 🟠 주황 | 파일 삭제 — 전체(`all`) / 특정 step 파일(`steps`) / 경로 직접(`path`) |
| `sleep` | 🟡 노랑 | 지정 초 대기 |
| `trace_start` | 🟢 에메랄드 | ftrace I/O 수집 시작 (UFS/Block/Both) |
| `trace_stop` | 🟢 에메랄드 | ftrace I/O 수집 중지, 이전 trace_start 자동 매칭 |
| `condition` | — | 조건 분기 (True/False 경로) |
| `app_macro` | 🟣 보라 | 등록된 앱 매크로 실행 |

### benchmark step 고급 기능

- **use_file_from_step**: 이전 step에서 생성된 파일을 재사용 (cleanup 없이 연속 테스트)
- **trace: "on"**: 해당 benchmark step 실행 시 자동으로 trace를 수집

### 조건 분기 (Condition)

`ConditionNode`로 런타임 결과에 따라 실행 경로를 분기합니다.

**조건 소스 유형**:

| 소스 | 설명 | 예시 |
|------|------|------|
| **벤치마크 메트릭** | 이전 step의 성능 결과값 비교 | `IOPS > 100000` |
| **Shell 명령** | 쉘 명령 실행 → 출력에서 값 추출 → 비교 | `df /data` → 사용률 추출 |

**연산자**:

```
>  <  >=  <=  ==  !=  contains  !contains
```

**복합 조건**: 여러 규칙을 `AND` / `OR` 로직으로 조합하여 복합 판단이 가능합니다.

```
규칙 1: IOPS > 100000
  AND
규칙 2: Latency < 5ms
```

**추출 테스트 도우미**: 조건 편집 시 샘플 출력을 붙여넣고 추출 패턴을 테스트할 수 있습니다. 실시간으로 추출 결과 + 조건 평가를 미리 확인합니다.

**엣지 연결**: Condition 노드는 **True 경로**와 **False 경로** 두 개의 출력 핸들을 가집니다.

### Loop (반복 그룹)

Loop 그룹으로 여러 step을 N회 반복합니다.

**사용 방법**:
1. 팔레트에서 **Loop** 드래그 → 프레임 생성 (`LoopGroup` 노드)
2. Loop 편집(✏) 아이콘 → 반복 횟수 + 포함할 Step 체크박스 선택
3. 포함된 Step들이 시각적 프레임 안에 표시됨

**동적 파라미터 변경** — 반복마다 파라미터를 자동으로 변경할 수 있습니다:

| 패턴 | 설명 | 예시 (3회 loop) |
|------|------|-----------------|
| 콤마 리스트 | 순서대로 적용 | `bs=4k,8k,16k` → 4k, 8k, 16k |
| 템플릿 변수 | `{loop}` 치환 | `size={loop}G` → 1G, 2G, 3G |
| 곱셈 | `{loop*N}` 계산 | `{loop*1024}M` → 1024M, 2048M, 3072M |

:::tip
Loop 내부의 benchmark step에 `trace: "on"`을 설정하면 반복마다 자동으로 trace가 수집되어, 반복 횟수별 I/O 패턴 변화를 분석할 수 있습니다.
:::

### 실행 추적 (Execution Tracking)

실행 중 캔버스에서 현재 진행 상황을 시각적으로 표시합니다.

| 상태 | 표시 | 배경색 |
|------|------|--------|
| 완료 | ✓ | 초록 |
| 실행 중 | 스피너 | 파랑 |
| 실패 | ✗ | 빨강 |
| 스킵됨 | — | 회색 |
| 대기 | — | 기본 |

각 노드 데이터에 `execStatus`, `execLoopCurrent`, `execLoopTotal`, `execProgress` 가 실시간 반영됩니다.

**미니 캔버스** (`ExecutionMiniCanvas`): 결과 상세 시트에서도 축소된 캔버스로 진행 상황을 확인할 수 있습니다 (접기/펼치기 가능).

### 템플릿

시나리오 구성을 DB(`portal_scenario_templates`)에 저장하고 재사용합니다.

- **저장**: step, loop, condition, edge 정보가 모두 보존
- **불러오기**: 템플릿 선택 시 캔버스가 저장된 상태로 복원 (`protoToCanvas` 역직렬화)
- **수정/삭제**: 기존 템플릿을 편집하거나 제거

---

## 6. 앱 매크로 자동화

`AgentMacroRecorder`에서 Android 디바이스의 UI 조작을 녹화하고 재생합니다.

### 매크로 위저드

매크로 생성은 5단계 위저드로 진행됩니다:

| 단계 | 화면 | 설명 |
|------|------|------|
| **list** | 매크로 목록 | 저장된 매크로 조회, 선택, 복제, 삭제 |
| **app** | 앱 선택 | 디바이스에 설치된 앱 목록에서 대상 앱 선택 |
| **record** | 녹화 | 실시간 화면을 보며 터치/스와이프 등 이벤트 녹화 |
| **events** | 이벤트 편집 | 녹화된 이벤트 목록 확인, 수정, 삭제 |
| **save** | 저장 | 이름, 설명 입력 후 DB 저장 |

### 지원 이벤트 유형

| 이벤트 | 설명 |
|--------|------|
| `tap` | 화면 터치 (x, y 좌표) |
| `swipe` | 스와이프 (시작 → 끝 좌표, duration) |
| `key` | 물리/소프트 키 입력 (Back, Home, Recent 등) |
| `wait` | 고정 시간 대기 (ms) |
| `wait_until` | OCR 텍스트 감지까지 대기 (타임아웃 지정) |
| `screenshot` | 현재 화면 캡처 |
| `scroll_capture` | 스크롤하며 연속 화면 캡처 |

### 녹화 방식

디바이스 화면이 H.264 WebSocket 스트리밍으로 실시간 표시됩니다. 화면 위에서:
- **클릭** → `tap` 이벤트 기록
- **드래그** → `swipe` 이벤트 기록 (시작/끝 좌표 + 소요 시간)
- **소프트 버튼** (Back/Home/Recent) → `key` 이벤트 기록

녹화 중 타임스탬프가 자동으로 기록되어, 이벤트 간 간격이 `wait`로 삽입됩니다.

### 좌표 스케일링

녹화 시 디바이스 해상도(`deviceWidth` x `deviceHeight`)가 함께 저장됩니다. 재생 시 대상 디바이스 해상도에 맞게 좌표가 자동 스케일링됩니다.

### OCR 연동 (Tesseract)

- **screenshot → 텍스트 추출**: 캡처한 화면에서 OCR로 텍스트를 인식
- **영역 지정**: 전체 화면 또는 특정 영역(region)만 OCR 수행 가능
- **wait_until**: 특정 텍스트가 화면에 나타날 때까지 대기 (로딩 완료 감지 등에 유용)

### 매크로 DB 관리

매크로는 `portal_app_macros` 테이블에 저장됩니다.

| 작업 | API | 설명 |
|------|-----|------|
| 목록 조회 | `fetchAppMacros` | 서버별 매크로 목록 |
| 생성 | `createAppMacro` | 새 매크로 저장 |
| 수정 | `updateAppMacro` | 이벤트/이름/설명 수정 |
| 삭제 | `deleteAppMacro` | 매크로 제거 |
| 복제 | `duplicateAppMacro` | 기존 매크로 복사 |

### 시나리오에서 매크로 사용

시나리오 캔버스에서 `app_macro` step 타입으로 등록된 매크로를 선택하여 실행할 수 있습니다. 벤치마크 전후에 앱 실행/종료를 자동화하거나, 특정 앱 시나리오를 반복 테스트하는 데 활용됩니다.

:::caution
매크로 재생은 디바이스 화면 해상도가 녹화 시와 크게 다르면 좌표 오차가 발생할 수 있습니다. 가능하면 동일 해상도의 디바이스에서 실행하세요.
:::

---

## 7. I/O Trace 분석

ftrace 기반으로 Android 디바이스의 UFS/Block I/O를 수집하고 분석합니다.

### Trace 시작/중지

센터 패널에서 "Trace" 모드 선택 후:

1. **Trace Type** 선택: UFS / Block / Both
2. 디바이스 **1개** 선택
3. **Start Trace** → (벤치마크 실행) → **Stop Trace**

:::note
시나리오 내에서 `trace_start` / `trace_stop` step을 사용하면 자동으로 trace를 수집합니다. `trace_stop` 시 디바이스 tracing off + adb kill은 즉시 수행되고, parquet 병합은 백그라운드로 처리하여 다음 step으로 즉시 진행됩니다.
:::

### Trace 분석 시트

`AgentTraceResultSheet`에서 전체화면 시트로 열립니다.

#### 5종 Scatter 차트

사이드바에서 표시할 차트를 선택합니다:

| 차트 | Y축 | 설명 |
|------|-----|------|
| **LBA** | Logical Block Address | 접근 블록 주소 분포 |
| **QD** | Queue Depth | 큐 깊이 변화 |
| **DtoC** | Dispatch to Complete | 디바이스 처리 지연 |
| **CtoD** | Complete to Dispatch | 완료 후 다음 디스패치까지 |
| **CtoC** | Complete to Complete | 연속 완료 간격 |

각 차트는 `TraceScatterChart` 컴포넌트로 렌더링되며, **cmd별 색상**이 자동 적용됩니다.

**cmd 색상 매핑**:
- SCSI opcode: `0x28` = Read (파랑), `0x2a` = Write (빨강) 등
- Block 문자열: 자동 매핑

#### Brush 드래그 필터

차트 위에서 **X + Y 양축 범위**를 드래그로 선택하면:
1. 선택 영역이 하이라이트 표시
2. 필터가 자동 반영
3. **Statistics 탭**이 선택 범위에 맞게 재조회

필터 해제: X 버튼 또는 빈 영역 클릭

#### Statistics 탭

| 탭 | 내용 |
|----|------|
| **Latency Stats** | min/max/avg/p50/p90/p95/p99 지연 통계 |
| **CMD Stats** | DtoC / CtoD / CtoC / QD 탭별 cmd 통계 |
| **Latency Histogram** | type별 탭으로 지연 분포 히스토그램 |
| **CMD+Size Count** | cmd별 탭으로 I/O 크기별 카운트 |

#### 복수 Job 병합

여러 trace job의 `job_ids`를 동시에 전달하여 합쳐진 결과를 분석할 수 있습니다. 시나리오 내 여러 trace 구간을 하나의 시트에서 비교 분석하는 데 유용합니다.

#### 차트 리사이즈

차트 영역은 **CSS native resize** 속성이 적용되어 있으며, `ResizeObserver`로 크기 변경을 감지하여 ECharts 인스턴스를 자동 리사이즈합니다.

#### Trace Reparse

Parquet 데이터가 손상되었거나 파서가 업데이트된 경우, 원본 `trace.log`에서 parquet를 재생성할 수 있습니다.

1. **Reparse 버튼** 클릭 (단일 job만 지원)
2. Go Agent에서 `./trace --parquet-only` 명령 실행
3. 2초 간격 폴링으로 완료 감지
4. 완료 시 데이터 자동 새로고침

:::caution
Reparse는 원본 trace.log가 서버에 보존되어 있어야 합니다. trace.log가 삭제된 경우 재생성이 불가능합니다.
:::

### Trace 목록 관리

- **Cycle 드롭다운**: repeat별 필터링
- **접기/펼치기**: 긴 목록 정리
- **개별 선택**: 특정 trace만 선택하여 분석
- **실시간 확인**: 시나리오 진행 중 `trace_stop` 완료 즉시 목록에 나타남

---

## 8. 실시간 모니터링

디바이스 옆 📊 아이콘을 클릭하면 우측 시트(`AgentMonitoringSheet`)에 실시간 차트가 표시됩니다.

### 모니터링 항목

| 차트 | 단위 | 설명 |
|------|------|------|
| **CPU Usage** | % | 디바이스 CPU 사용률 |
| **Memory Usage** | % | 메모리 사용률 |
| **Disk I/O** | ops | 디스크 I/O 오퍼레이션 수 |

### SSE 스트리밍

- `MonitorDevices` gRPC 스트리밍 → SSE로 변환
- **5초 간격** 데이터 갱신
- **페이지 레벨 연결 관리**: Agent 페이지가 열려 있는 동안 연결 유지
- **시트를 닫아도 연결 유지** — 다시 열면 쌓인 데이터가 그대로 차트에 표시됨
- **디바이스별 차트**: 여러 디바이스를 동시에 모니터링할 수 있음

---

## 9. Results (실행 이력)

센터 패널에서 "Results" 모드를 선택하면 모든 실행 이력을 조회할 수 있습니다.

### 실행 이력 관리

- **DB 저장**: 모든 Benchmark/Scenario/Trace 실행이 `portal_job_executions` 테이블에 기록
- **필터**: 서버, 타입(Benchmark/Scenario/Trace), 상태별 필터링
- **페이지네이션**: 30건 단위 페이징
- **통계**: 총 실행 수, 성공/실패 수, 성공률 표시

### 결과 상세 시트

`AgentResultDetailSheet`에서 실행 결과를 상세 분석합니다.

**벤치마크 결과** (`FioResultView`):
- Cycle별 IOPS/Bandwidth 차트
- Read/Write 탭 분리
- **Step 모드**: 개별 step 결과 확인
- **Merge 모드**: 여러 step을 한 차트에서 비교 (개별 선택 가능)

**시나리오 결과**:
- Loop 결과: **Cycle 탭**(repeat) + **Iteration**(loop 반복) X축
- **Step 비교**: 여러 Step을 한 차트에서 비교
- **미니 캔버스**로 실행 흐름 확인

**매크로 결과** (`MacroResultView`):
- 매크로 실행 로그 및 스크린샷 확인

### 다른 브라우저에서 확인

어떤 컴퓨터/브라우저에서든 Agent 페이지에 접속하면 DB에서 running 상태인 job을 자동 조회하고 SSE를 구독하여 **실시간 진행 상황을 확인**할 수 있습니다.

---

## 10. 플로팅 Job 카드

`AgentFloatingJobCard`는 화면 하단 우측에 고정되어 실행 중인 Job의 진행률을 표시합니다.

### 기능

| 기능 | 설명 |
|------|------|
| **진행률 표시** | 프로그레스 바 + 퍼센트 |
| **Job 정보** | 서버명, 도구, 디바이스 목록 |
| **상태 전환** | running → completed/failed 자동 갱신 |
| **자동 닫기** | 완료/실패 후 일정 시간 경과 시 자동 dismiss |
| **취소** | 실행 중인 Job을 취소 (cancel 버튼) |
| **다중 Job** | 여러 Job이 동시 실행될 때 카드가 쌓여서 표시 |

### SSE 진행률 구독

`SubscribeJobProgress` gRPC 스트리밍 → SSE로 변환되어 실시간 진행률이 갱신됩니다. 각 Job별로 `EventSource`를 관리하며, 페이지를 새로 열어도 running 상태 Job의 SSE를 자동 재구독합니다.

---

## 11. Job 스케줄링

센터 패널에서 "Schedule" 모드를 선택하면 정기 실행 관리 화면(`AgentScheduleView`)이 표시됩니다.

### Cron 기반 자동 실행

| 프리셋 | Cron 표현식 | 설명 |
|--------|-------------|------|
| 매시간 | `0 0 * * * ?` | 매시 정각 실행 |
| 매일 새벽 | `0 0 2 * * ?` | 매일 02:00 실행 |
| 매주 월요일 | `0 0 2 ? * MON` | 매주 월요일 02:00 |
| 직접 입력 | 사용자 정의 | 자유 Cron 표현식 |

### 스케줄 설정

| 항목 | 설명 |
|------|------|
| **타입** | 벤치마크 / 시나리오 선택 |
| **서버/디바이스** | 실행 대상 지정 |
| **파라미터** | 벤치마크 옵션 또는 시나리오 템플릿 |
| **재시도** (`retry`) | 실패 시 N회 자동 재시도, 간격 설정 가능 |
| **Busy Policy** (`busy_policy`) | 디바이스가 사용 중일 때 대기/스킵/강제 실행 |
| **알림** | Slack webhook으로 성공/실패 알림 |
| **활성/비활성** | 토글로 스케줄 on/off |
| **수동 트리거** | 즉시 실행 버튼 |

### DB 테이블

스케줄 정보는 `portal_scheduled_jobs` 테이블에 저장되며, camelCase 컬럼 컨벤션을 사용합니다.

---

## 12. 디바이스 화면 스트리밍

디바이스 옆 📱 아이콘을 클릭하면 우측 시트(`AgentScreenSheet`)에 Android 화면이 실시간으로 표시됩니다.

### 기술 스택

| 구성 요소 | 역할 |
|-----------|------|
| **scrcpy v2.4** | Android 화면을 H.264로 인코딩 |
| **WebSocket** | Go Agent → 브라우저 H.264 스트림 전달 |
| **JMuxer** | 브라우저에서 H.264 → 비디오 디코딩 |

### 입력 지원

| 입력 | 동작 |
|------|------|
| 마우스 클릭 | 터치 |
| 마우스 드래그 | 스와이프 |
| 마우스 스크롤 | 스크롤 |
| 소프트 버튼 | Back / Home / Recent |

### 연결 관리

- **시트 닫기**: JMuxer 인스턴스만 해제, **WebSocket 연결은 유지**
- **시트 재오픈**: 서버에서 **SPS/PPS + 키프레임**을 재전송하여 즉시 화면 표시 (재연결 지연 없음)
- **디바이스 전환**: 다른 디바이스를 선택하면 기존 연결 해제 후 새 연결 생성
- `connectedDeviceKey`로 동일 디바이스 재연결 방지

:::tip
화면 스트리밍은 매크로 녹화(`AgentMacroRecorder`)에서도 동일한 WebSocket + JMuxer 스택을 사용합니다. 매크로 녹화 시 화면 위의 터치/드래그가 이벤트로 기록됩니다.
:::

---

## 백엔드 아키텍처 요약

### 패키지 구조

```
com.samsung.move.agent
├── controller/     # AgentController (/api/agent/*)
├── entity/         # JPA 엔티티
├── repository/     # Spring Data JPA
├── service/        # 비즈니스 로직
└── grpc/           # AgentGrpcClient, AgentConnectionManager
```

### DB 테이블

| 테이블 | 용도 |
|--------|------|
| `portal_agent_servers` | Agent 서버 목록 |
| `portal_scenario_templates` | 시나리오 템플릿 |
| `portal_benchmark_presets` | 벤치마크 프리셋 |
| `portal_app_macros` | 앱 매크로 |
| `portal_job_executions` | Job 실행 이력 |
| `portal_scheduled_jobs` | 스케줄 설정 |

### gRPC 통신

Proto 파일: `src/main/proto/device_agent.proto`

주요 RPC:

| 카테고리 | RPC | 설명 |
|----------|-----|------|
| 디바이스 | `ListDevices` | 연결된 디바이스 목록 |
| 디바이스 | `ConnectDevice` / `DisconnectDevice` | ADB 연결/해제 |
| 벤치마크 | `RunBenchmark` | 벤치마크 실행 |
| 벤치마크 | `GetJobStatus` | Job 상태 조회 |
| 벤치마크 | `SubscribeJobProgress` | SSE 스트리밍 진행률 |
| 벤치마크 | `GetBenchmarkResult` | 벤치마크 결과 |
| 벤치마크 | `DeleteJob` | Job 삭제 |
| 시나리오 | `RunScenario` | 시나리오 실행 |
| 모니터링 | `MonitorDevices` | SSE 스트리밍 모니터링 |
| Trace | `StartTrace` / `StopTrace` | Trace 시작/중지 |
| Trace | `GetTraceResult` | Trace 통계 |
| Trace | `GetTraceRawData` | Trace 원시 데이터 (repeated job_ids) |

:::note
Go Agent 서버(`~/project/agent`)와 Portal의 proto 파일은 동기화가 필요합니다. proto를 수정하면 양쪽 모두 재생성해야 합니다.
:::
