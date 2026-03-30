---
title: Agent (디바이스 평가)
description: Android 디바이스의 벤치마크, 시나리오, I/O trace 실행 및 결과 분석
---

Agent 페이지는 Android 디바이스에 연결된 Go gRPC 서버(DeviceAgent)를 통해 스토리지 성능 벤치마크, 시나리오 실행, I/O trace 수집/분석을 수행합니다.

## 페이지 구조

3패널 레이아웃으로 구성됩니다.

| 영역 | 역할 |
|------|------|
| **좌측 패널** | 서버 카드 선택, 디바이스 체크리스트, 스토리지 실시간 표시, Quick Actions |
| **센터 패널** | Benchmark / Scenario / Trace / Results / Schedule 모드 전환 |
| **우측 시트** | 서버 관리, 모니터링 차트, 결과 상세, Trace 분석 (on-demand) |
| **플로팅 카드** | 실행 중인 Job 진행률 (하단 우측 고정) |

## 서버 관리

좌측 패널에서 서버가 **카드 형태**로 표시됩니다. 카드 클릭으로 서버 선택, 설정 아이콘으로 서버 관리 시트를 엽니다.

- **추가**: Name, Host, Port(기본 50051) 입력 → 접속 테스트 → 저장
- **접속 테스트**: `ListDevices` gRPC 호출로 연결 확인
- **수정/삭제**: 기존 서버 편집, 삭제 시 gRPC 채널도 정리
- **연결 상태**: 선택된 서버의 연결 dot 표시 (초록=연결, 빨강=끊김)

## 디바이스

좌측 패널에서 선택한 서버의 디바이스 목록이 자동 로드됩니다.

- **device_id**: USB 포트 경로(예: `2-1.1.2`) 또는 TCP serial
- **serial**: 표시용 시리얼 번호
- **상태**: Online(초록), Busy(노랑), Offline(회색)
- **추가 정보**: manufacturer, board, platform, hardware, cpuAbi, buildId, sdkVersion
- **모니터링**: 📊 아이콘 → 실시간 CPU/Memory/Disk 차트
- **화면 보기**: 📱 아이콘 → 실시간 Android 화면 스트리밍

### 스토리지 실시간 표시

디바이스를 선택하면 좌측 패널에 **/data 파티션 사용량**이 실시간으로 표시됩니다.

- 디바이스별 프로그레스 바 + 사용량/전체 용량
- 90% 초과: 빨간 경고, 70% 초과: 주황 경고
- 2초 간격 자동 갱신
- 여러 디바이스 선택 시 각각 표시

## Benchmark

센터 패널에서 "Benchmark" 선택 후 실행합니다.

### 파라미터 입력

주요 옵션은 select/input 폼 필드로 제공됩니다. 각 옵션 옆 (?) 아이콘에 도움말이 표시됩니다.

**fio 기본 옵션**: Block Size, I/O Pattern, I/O Size, Runtime, Num Jobs, I/O Engine, Direct I/O, I/O Depth

**fio 고급 옵션** (접기/펼치기): Read/Write Mix %, BS Split, BS Range, Zone/Chunk, Time Based, Ramp Time, Rate Limit, Verify, Group Reporting 등

**iozone/tiotest**: 각 도구의 실제 usage에 맞는 옵션 제공

고급 옵션에 없는 파라미터는 하단 textarea에 `key=value` 형식으로 추가 입력합니다.

### 프리셋

자주 사용하는 벤치마크 설정을 DB에 저장하고 빠르게 불러올 수 있습니다.

### 결과 단위

성능 결과는 사용자 친화적 단위로 자동 변환됩니다.

| 메트릭 | 원본 | 표시 |
|--------|------|------|
| IOPS | ops/s | **KIOPS** |
| Bandwidth | KB/s | **MiB/s** |
| Latency | ns | **ms** |
| IO 크기 | bytes | **MiB** |

## Scenario (캔버스 빌더)

**@xyflow/svelte 기반 비주얼 캔버스**로 시나리오를 구성합니다. Airflow DAG처럼 노드와 엣지로 워크플로우를 편집합니다.

### 캔버스 사용법

1. **좌측 팔레트**에서 Step 타입을 캔버스로 **드래그 앤 드롭**
2. 노드 간 **핸들(●) 드래그**로 엣지 연결
3. 노드 hover → **편집(✏)/삭제(🗑)** 아이콘
4. **실행 순서** = Y좌표 기준 (위에 있는 노드가 먼저 실행)
5. 각 노드에 실행 순서 번호(①②③) 자동 표시

### Step Types

| Type | 설명 |
|------|------|
| `benchmark` | fio/iozone/tiotest 실행 |
| `shell` | 커스텀 쉘 명령 실행 |
| `cleanup` | 파일 삭제 (전체 / 특정 step / 경로 직접) |
| `sleep` | 대기 |
| `trace_start` | ftrace I/O 수집 시작 |
| `trace_stop` | ftrace I/O 수집 중지 |
| `condition` | 조건 분기 (True/False 경로) |
| `loop` | 반복 그룹 (시각적 프레임) |

### 조건 분기 (Condition)

Condition 노드로 런타임 결과에 따라 경로를 분기합니다.

**벤치마크 메트릭 조건**: IOPS > 100 KIOPS 이면 True 경로
**Shell 명령 조건**: `df /data` 결과에서 사용률 추출 → 90% 초과면 중단
**복합 조건 (AND/OR)**: 여러 규칙을 조합하여 복합 판단

연산자: `>`, `<`, `>=`, `<=`, `==`, `!=`, `contains`, `!contains`

조건 편집 시 **추출 테스트 도우미**로 샘플 출력을 붙여넣고 실시간으로 추출 결과 + 조건 평가를 미리 확인할 수 있습니다.

### Loop

Loop 그룹으로 여러 step을 N회 반복합니다.

1. 팔레트에서 **Loop** 드래그 → 프레임 생성
2. Loop 편집(✏) 아이콘 → 반복 횟수 + 포함할 Step 체크박스 선택
3. 반복마다 파라미터를 동적으로 변경 가능:
   - **콤마 리스트**: `bs=4k,8k,16k,32k` → loop마다 순서대로 적용
   - **템플릿 변수**: `size={loop}G` → loop 1: 1G, loop 2: 2G...
   - **곱셈**: `{loop*1024}M` → 1024M, 2048M, 3072M...

### 실행 추적

실행 중 캔버스에서 현재 진행 상황을 시각적으로 표시합니다.

| 상태 | 표시 |
|------|------|
| 완료 | 초록 배경 + ✓ |
| 실행 중 | 파란 배경 + 스피너 |
| 실패 | 빨간 배경 + ✗ |
| 대기 | 기본 |

결과 상세 시트에서도 **미니 캔버스**로 진행 상황을 확인할 수 있습니다 (접기/펼치기).

### 템플릿

시나리오 구성을 DB에 저장하고 재사용합니다. 저장 시 step, loop, condition 정보가 모두 보존됩니다.

## I/O Trace

ftrace 기반으로 Android 디바이스의 UFS/Block I/O를 수집하고 분석합니다.

### Trace 시작/중지

센터 패널에서 "Trace" 선택:
1. Trace Type 선택 (UFS / Block / Both)
2. 디바이스 1개 선택
3. Start Trace → 벤치마크 실행 → Stop Trace

**시나리오 내 trace**: trace_stop 시 디바이스 tracing off + adb kill은 즉시 수행되고, parquet 병합은 백그라운드로 처리하여 다음 step으로 즉시 진행됩니다.

**실시간 trace 확인**: 시나리오 진행 중 trace_stop이 완료되면 즉시 Trace 목록에 나타나고, "분석" 버튼으로 바로 결과 확인이 가능합니다.

### Trace 분석 시트

전체화면 시트로 열립니다.

**Raw Data 차트** (사이드바로 선택):
- LBA 분포, Queue Depth, DtoC/CtoD/CtoC Latency
- cmd별 색상 구분, 드래그 영역 선택 → 필터 자동 반영

**Statistics**: Latency Stats, CMD Stats, Latency Histogram, CMD+Size Count

**Trace 목록 관리**: Cycle 드롭다운으로 repeat별 필터, 접기/펼치기, 개별 선택

## Results (실행 이력)

모든 Benchmark/Scenario/Trace 실행 이력이 **DB에 저장**되어 표시됩니다.

- **필터**: 서버, 타입(Benchmark/Scenario/Trace), 상태별 필터
- **페이지네이션**: 30건 단위 페이징 (100건 제한 없음)
- **통계**: 총 실행 수, 성공/실패 수, 성공률
- **결과 상세**:
  - Loop 결과: **Cycle 탭**(repeat) + **Iteration**(loop 반복) X축
  - **Step 비교**: 여러 Step을 한 차트에서 비교 (개별 선택 가능)

### 다른 브라우저에서 확인

어떤 컴퓨터/브라우저에서든 Agent 페이지에 접속하면, DB에서 running 상태인 job을 자동으로 조회하고 SSE를 구독하여 **실시간 진행 상황을 확인**할 수 있습니다.

## Schedule (스케줄링)

센터 패널에서 "Schedule" 선택 시 정기 실행 관리 화면이 표시됩니다.

- **Cron 기반 자동 실행**: 매시간, 매일 새벽, 매주 등 프리셋 또는 직접 입력
- **벤치마크/시나리오**: 타입별 설정 + 서버/디바이스/파라미터 지정
- **재시도**: 실패 시 N회 자동 재시도 (간격 설정 가능)
- **알림**: Slack webhook으로 성공/실패 알림
- **수동 트리거**: 즉시 실행 버튼
- **활성/비활성** 토글

## 디바이스 화면 스트리밍

디바이스 옆 📱 아이콘을 클릭하면 우측 시트에 Android 화면이 실시간으로 표시됩니다.

- **H.264 비디오**: scrcpy v2.4 기반, WebSocket을 통해 JMuxer로 디코딩
- **입력 지원**: 마우스 클릭(터치), 드래그(스와이프), 스크롤, Back/Home/Recent 소프트 버튼
- **시트를 닫아도 연결 유지** — 다시 열면 서버에서 SPS/PPS + 키프레임을 재전송하여 즉시 화면 표시

## 실시간 모니터링

디바이스 옆 📊 아이콘을 클릭하면 우측 시트에 실시간 차트가 표시됩니다.

- CPU Usage (%), Memory Usage (%), Disk I/O (ops)
- 5초 간격 SSE 스트리밍
- **시트를 닫아도 연결 유지** — 다시 열면 쌓인 데이터 그대로 표시
