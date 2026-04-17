---
title: I/O Test (호환성 평가)
description: syscall 레벨 멀티스레드 I/O 테스트 — 명령 시퀀스 빌더, 데이터 무결성 검증, 프리셋 관리
---

I/O Test는 Android 디바이스에서 **syscall 레벨의 I/O 테스트**를 수행합니다. fio/iozone과 달리 개별 open/read/write/close 시퀀스를 직접 정의하고, 멀티스레드로 동시 실행할 수 있습니다.

---

## 1. 기본 개념

### iotest 바이너리
Go로 작성된 크로스 컴파일 바이너리(`tools/iotest`)가 Android 디바이스에 adb push 되어 실행됩니다. JSON 설정을 입력받아 지정된 syscall 시퀀스를 실행하고, 결과를 JSON으로 출력합니다.

### 스레드(Thread)
하나의 iotest 실행에 여러 스레드를 정의할 수 있습니다. 각 스레드는 독립적인 명령 시퀀스를 가지며, **goroutine으로 동시 실행**됩니다.

### 명령(Command)
각 스레드 안에 순서대로 실행할 op(명령)을 나열합니다. loop, if 분기도 지원합니다.

---

## 2. 지원 Op (22개)

### I/O 작업

| Op | 설명 | 주요 파라미터 |
|----|------|-------------|
| `open` | 파일 열기 | path, flags, fd (이름) |
| `close` | 파일 닫기 | fd |
| `read` | offset 지정 읽기 (pread) | fd, offset, bs, count |
| `write` | offset 지정 쓰기 (pwrite) | fd, offset, bs, count, pattern |
| `verify` | write 패턴 검증 (read+비교) | fd, offset, bs, count, pattern |
| `fsync` | 디스크 동기화 | fd |
| `fdatasync` | 데이터만 동기화 | fd |

### 파일 관리

| Op | 설명 | 주요 파라미터 |
|----|------|-------------|
| `stat` | 파일 정보 조회 | path |
| `truncate` | 파일 크기 변경 | path, size |
| `unlink` | 파일 삭제 | path |
| `mkdir` | 디렉토리 생성 | path |
| `rename` | 파일 이름 변경 | path, new_path |
| `fallocate` | 공간 사전 할당 | fd/path, size |
| `create_files` | N개 파일 생성 | dir, prefix, count, bs, blocks |
| `delete_pattern` | 패턴 삭제 (홀수/짝수) | dir, prefix, rule, count |

### 디바이스 제어

| Op | 설명 | 주요 파라미터 |
|----|------|-------------|
| `sysfs_write` | sysfs 값 쓰기 | path, value |
| `sysfs_read` | sysfs 값 읽기 | path |
| `shell` | 임의 shell 명령 | cmd |

### 흐름 제어

| Op | 설명 | 주요 파라미터 |
|----|------|-------------|
| `sleep` | 대기 | ms |
| `loop` | 반복 (횟수 또는 시간) | loop_count / loop_duration, items |
| `if` | 조건 분기 | condition, then[], else[] |

---

## 3. 멀티 파일 핸들 (Multi-FD)

여러 파일을 동시에 열고 교차 R/W 가능:

```json
{"op": "open", "path": "/data/.../fileA", "fd": "A", "flags": "O_RDWR|O_CREATE"},
{"op": "open", "path": "/data/.../fileB", "fd": "B", "flags": "O_RDWR|O_CREATE"},
{"op": "write", "fd": "A", "offset": "0", "bs": "4k", "count": 100, "pattern": "byte:0xAA"},
{"op": "write", "fd": "B", "offset": "0", "bs": "4k", "count": 100, "pattern": "byte:0x55"},
{"op": "verify", "fd": "A", "offset": "0", "bs": "4k", "count": 100, "pattern": "byte:0xAA"},
{"op": "close", "fd": "A"},
{"op": "close", "fd": "B"}
```

`fd`를 생략하면 마지막으로 열린 파일이 사용됩니다 (단일 파일 사용 시).

---

## 4. 랜덤/순차 파라미터

offset, bs 필드에서 랜덤 또는 순차 선택이 가능합니다:

| 형식 | 예시 | 동작 |
|------|------|------|
| 고정값 | `4k`, `1m` | 항상 같은 값 |
| 템플릿 | `{{i*4096}}` | loop index로 계산 |
| 랜덤 리스트 | `random:4k,8k,16k,64k` | 매번 랜덤 선택 |
| 랜덤 범위 | `random:0-1m` | 범위 내 랜덤 값 |
| 순차 리스트 | `seq:4k,8k,16k` | loop index에 따라 round-robin |

결과에 `actual` 필드로 실제 선택된 값이 기록됩니다:
```json
{"op": "write", "offset": 674727, "bs": 8192, "actual": "offset=674727,bs=8k"}
```

---

## 5. Duration 기반 Loop

`loop_duration`으로 시간 기반 반복:

```json
{"op": "loop", "loop_duration": 60, "commands": [
  {"op": "write", "offset": "random:0-10m", "bs": "random:4k,8k,16k", "count": 1}
]}
```
→ 60초 동안 반복 실행. `loop_count`와 택일.

---

## 6. 데이터 무결성 검증 (Verify)

write한 패턴을 read해서 byte 비교:

```json
{"op": "write", "offset": "0", "bs": "4k", "count": 100, "pattern": "byte:0xAA"},
{"op": "fsync"},
{"op": "verify", "offset": "0", "bs": "4k", "count": 100, "pattern": "byte:0xAA"}
```

불일치 시 에러 메시지에 정확한 mismatch offset이 포함됩니다:
```
mismatch at offset 4096+12: expected 0xAA got 0x00
```

---

## 7. 프리셋

### 기본 프리셋 (15개, 6개 카테고리)

| 카테고리 | 프리셋 | 설명 |
|---------|--------|------|
| **Basic I/O** | Offset Write | offset 0부터 4k씩 순차 쓰기 |
| | Offset R/W | write 후 같은 offset read |
| | Misaligned R/W | 512B 단위 비정렬 I/O |
| | Conditional R/W | 짝수=write, 홀수=read |
| **Random/Stress** | Cache Miss | 대용량 순차 write → random read |
| | Duration Stress | 60초 동안 random R/W |
| **Data Integrity** | Multi-File R/W | 2파일 동시 open + verify |
| | Random BS + Verify | 랜덤 블록 크기 write → verify |
| **File Management** | Create+Delete Odd/Even | 파일 생성 → 홀수/짝수 삭제 |
| | Fragmentation | 100파일→홀수삭제→큰파일 재할당 |
| | Rename Stress | rename 반복 → verify |
| **Concurrent** | Mixed RWD | 3 threads: write+read+파일삭제 |
| | Concurrent Same File | 같은 파일에 동시 R/W |
| **Device Control** | Voltage Swing + I/O | sysfs 변경 + 동시 write |

### 사용자 프리셋
현재 설정을 이름과 카테고리를 지정하여 DB에 저장할 수 있습니다. 프리셋 목록에서 파란 배경으로 구분됩니다.

---

## 8. 시나리오 통합

I/O Test는 시나리오 캔버스에서 독립 step type으로 사용 가능합니다:
- 시나리오 캔버스 → 노드 팔레트에서 **I/O Test** 드래그
- Trace Start → I/O Test → Trace Stop 조합으로 I/O trace 수집과 함께 실행
- 다른 step (benchmark, shell, cleanup 등)과 자유 조합

---

## 9. 실시간 Progress

실행 중 각 thread의 각 step 진행 상황이 실시간으로 표시됩니다:
- 시나리오 캔버스: iotest 노드에 thread별 미니 progress bar
- 노드 클릭: thread별 step 상태 (완료/진행중/실패), loop iter/total, op duration
