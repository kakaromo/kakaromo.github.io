---
title: Agent (디바이스 평가)
description: Android 디바이스의 벤치마크, 시나리오, I/O trace 실행 및 결과 분석
---

Agent 페이지는 Android 디바이스에 연결된 Go gRPC 서버(DeviceAgent)를 통해 스토리지 성능 벤치마크, 시나리오 실행, I/O trace 수집/분석을 수행합니다.

## 페이지 구조

3패널 레이아웃으로 구성됩니다.

| 영역 | 역할 |
|------|------|
| **좌측 패널** | 서버 선택, 디바이스 체크리스트, Quick Actions |
| **센터 패널** | Benchmark / Scenario / Trace / Results 모드 전환 |
| **우측 시트** | 서버 관리, 모니터링 차트, 결과 상세, Trace 분석 (on-demand) |
| **플로팅 카드** | 실행 중인 Job 진행률 (하단 우측 고정) |

## 서버 관리

좌측 패널의 ⚙ 아이콘을 클릭하면 Agent 서버 관리 시트가 열립니다.

- **추가**: Name, Host, Port(기본 50051) 입력 → 접속 테스트 → 저장
- **접속 테스트**: `ListDevices` gRPC 호출로 연결 확인
- **수정/삭제**: 기존 서버 편집, 삭제 시 gRPC 채널도 정리

## 디바이스

좌측 패널에서 선택한 서버의 디바이스 목록이 자동 로드됩니다.

- **device_id**: USB 포트 경로(예: `2-1.1.2`) 또는 TCP serial(예: `192.168.65.1:5555`)
- **serial**: 표시용 시리얼 번호
- **상태**: Online(초록), Busy(노랑), Offline(회색)
- **모니터링**: 디바이스 옆 📊 아이콘 → 실시간 CPU/Memory/Disk 차트 (시트 닫아도 연결 유지)

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

- **저장**: "프리셋 저장" 버튼 → 이름 입력 → tool + 현재 params 저장
- **로드**: 상단 드롭다운에서 프리셋 선택 → 자동 채움
- **삭제**: 드롭다운 옆 🗑 아이콘

### 결과

실행 완료 후 플로팅 카드의 "결과 보기" 또는 Results 탭에서 접근합니다.

- **Performance 차트**: Cycle별 IOPS/Bandwidth (Read/Write 탭)
- **Statistics 테이블**: 카테고리별 탭 (IOPS, BW, Latency, CPU 등) → Read/Write 서브탭 → Cycle × metric 피벗 테이블
- **Step 선택**: 여러 step일 때 개별 또는 Merge 보기
- **Trace 분석**: trace가 포함된 경우 "Trace" 버튼으로 trace 시트 연결

## Scenario

여러 step을 조합하여 복잡한 테스트를 구성합니다.

### Step Types

| Type | 설명 |
|------|------|
| `benchmark` | fio/iozone/tiotest 실행 |
| `shell` | 커스텀 쉘 명령 실행 |
| `cleanup` | 파일 삭제 (전체 / 특정 step / 경로 직접) |
| `sleep` | 대기 |
| `trace_start` | ftrace I/O 수집 시작 |
| `trace_stop` | ftrace I/O 수집 중지 |

### 파일 재사용

benchmark step에서 "파일 재사용" 드롭다운으로 이전 step이 생성한 파일을 재사용할 수 있습니다. (예: step 0에서 write → step 1에서 같은 파일 randread)

### Cleanup 옵션

- **전체 삭제**: `/data/local/tmp/test` 폴더 삭제
- **특정 step 파일 삭제**: 체크박스로 step 선택 → `delete_files_from_steps`
- **경로 직접 입력**: 특정 경로 삭제

### 자동 Trace

benchmark/shell step에서 "Trace" 토글을 켜면 해당 step 실행 전후에 자동으로 trace_start/stop이 수행됩니다. trace_type(ufs/block/both) 선택 가능.

### Loop & Repeat

- **Loop**: 시작/끝 step 범위를 N회 반복
- **Repeat**: 전체 시나리오를 N회 반복

### 템플릿

시나리오 구성을 DB에 저장하고 재사용합니다.

- **저장**: "템플릿 저장" → 이름 입력 → steps/loops/repeat 저장
- **로드**: 드롭다운에서 템플릿 선택 → 자동 채움
- **복제/삭제**: 드롭다운 옆 아이콘

## I/O Trace

ftrace 기반으로 Android 디바이스의 UFS/Block I/O를 수집하고 분석합니다.

### Trace 시작/중지

센터 패널에서 "Trace" 선택:
1. Trace Type 선택 (UFS / Block / Both)
2. 디바이스 1개 선택
3. Start Trace → 벤치마크 실행 → Stop Trace

### Trace 분석 시트

전체화면 시트로 열립니다.

**Raw Data 차트** (사이드바로 선택):
- LBA 분포 (time → lba)
- Queue Depth (time → qd)
- DtoC Latency (time → dtoc)
- CtoD Latency (time → ctod)
- CtoC Latency (time → ctoc)

특징:
- cmd별 색상 구분 (Read=파랑, Write=주황, Flush=초록, Discard=보라, SCSI opcode 자동 매핑)
- 사이드바에서 차트 멀티 선택
- 드래그 영역 선택 → X+Y 축 필터 자동 반영 → 통계 재조회

**Filter**:
- Time range, LBA range, DtoC/CtoD/CtoC min/max, QD min/max
- Custom latency histogram 구간 (기본: 0.1, 0.5, 1, 5, 10, 50, 100, 500, 1000 ms)

**Statistics**:
- Overview: Total Events, Duration, Continuous/Aligned Ratio
- Latency Statistics: DtoC/CtoD/CtoC/QD 탭 → min~p99.9999 테이블
- CMD Statistics: Overview + DtoC/CtoD/CtoC/QD 탭 → cmd별 상세 latency
- Latency Histogram: type별 탭 (DtoC/CtoD/CtoC) → DataTable
- CMD+Size Count: cmd별 탭 → size별 count

**여러 Job 합치기**: 멀티 trace job을 합쳐서 조회 가능 (repeated job_ids)

## Results (히스토리)

모든 Benchmark/Scenario/Trace 실행 이력이 표시됩니다.

- localStorage에 최대 100건 영속 저장
- Job ID 복사, 상세 보기, 삭제 지원
- 수동 Job ID 입력 조회 가능

## 실시간 모니터링

디바이스 옆 📊 아이콘을 클릭하면 우측 시트에 실시간 차트가 표시됩니다.

- CPU Usage (%), Memory Usage (%), Disk I/O (ops)
- 5초 간격 SSE 스트리밍
- **시트를 닫아도 연결 유지** — 다시 열면 쌓인 데이터 그대로 표시
- 다른 디바이스 클릭 시 기존 연결 끊고 새로 시작
