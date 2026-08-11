---
title: fsiotrace 사용법
description: 빌드된 fsiotrace 바이너리로 IO 를 추적·분석하는 방법 — 옵션, 출력 해석, host 후처리
---
이 문서는 빌드된 `fsiotrace` 바이너리로 실제 IO를 추적하고
분석하는 방법을 설명한다. 빌드는 [`BUILD.md`](/fsiotrace/build/), 설계는 [`DESIGN.md`](/fsiotrace/design/).

## 1. 한눈에

```sh
# 가장 흔한 사용 — 10초 동안 전체 layer trace, 비트 풀이 ON
adb shell '/data/local/tmp/fsiotrace --duration 10 -x -o /data/local/tmp/run'
adb pull /data/local/tmp/run.events .
less run.events
```

## 2. 옵션 한눈에

| 옵션 | 의미 |
|---|---|
| `-o PREFIX` | `PREFIX.events` 로 출력 (생략 시 stdout) |
| `-d SEC` | SEC 초 후 자동 종료 |
| `-m N` | N개 이벤트 모이면 자동 종료 |
| `-p PID` | 특정 PID 만 |
| `-D MAJ:MIN` | 특정 block device 만 (`/proc/partitions` 참고) |
| `-I HEX` | `io_flags & MASK` 인 이벤트만 emit |
| `-x` | 줄 끝에 비트 이름 풀이 `[WRITE\|O_SYNC\|DATA]` 를 18번째 컬럼으로 추가 (17컬럼 뒤라 파서 호환) |
| `--no-vfs` / `--no-fs` / `--no-blk` / `--no-ufs` | layer 단위 off |
| `--bio` | **merge 전** bio 추적(`block_bio_queue`). merge 로 가려진 파일을 보려면 필요. ⚠ 이벤트량 급증 — drop 나면 `--rb-size` ↑ |
| `--rb-size=MB` | ringbuf 크기 (**기본 256MB**). `diag[9]` drop 보이면 ↑. 커널이 미리 할당하므로 메모리/RLIMIT_MEMLOCK 부족으로 load 실패하면 ↓ (`--rb-size 32`) |
| `--poll-ms=MS` | ring_buffer poll 주기 (기본 50ms). 짧을수록 burst 흡수 ↑ |
| `-v` | libbpf verbose |

## 3. 시나리오별 명령

### 3.1 특정 프로세스의 모든 IO

```sh
adb shell pidof com.example.app
# 12345

adb shell '/data/local/tmp/fsiotrace -p 12345 -x -o /data/local/tmp/app'
```

### 3.2 discard / UNMAP 만

```sh
# IO_DISCARD = bit2 = 0x4
adb shell '/data/local/tmp/fsiotrace -I 0x4 -x -o /data/local/tmp/discard'
```

### 3.3 동기 쓰기(O_SYNC/O_DIRECT/fsync 트리거)만

```sh
# O_SYNC(8) | O_DIRECT(9) | O_DSYNC(11) | SYNC_PATH(12) | REQ_SYNC(13)
# = 0x100 | 0x200 | 0x800 | 0x1000 | 0x2000 = 0x3B00
adb shell '/data/local/tmp/fsiotrace -I 0x3B00 -x -o /data/local/tmp/sync'
```

### 3.4 f2fs GC 만

```sh
# IO_GC = bit24 = 0x1000000
adb shell '/data/local/tmp/fsiotrace -I 0x1000000 -x -o /data/local/tmp/gc'
```

### 3.5 journal commit 만

```sh
# IO_JOURNAL = bit22 = 0x400000
adb shell '/data/local/tmp/fsiotrace -I 0x400000 -x -o /data/local/tmp/jrnl'
```

### 3.6 UFS layer 만 (block 이하 noise 줄이기)

```sh
adb shell '/data/local/tmp/fsiotrace --no-vfs --no-fs --no-blk -x -o /data/local/tmp/ufs'
```

### 3.7 특정 파티션 만 (예: /data → dm-84)

```sh
adb shell 'ls -l /dev/block/by-name/userdata'
# /dev/block/sda40 같은 것
adb shell 'cat /sys/class/block/sda40/dev'
# 8:40  ← major:minor
adb shell '/data/local/tmp/fsiotrace -D 8:40 -x -o /data/local/tmp/userdata'
```

### 3.8 IO 부하 줘서 검증

```sh
# 한 터미널에서 tracer
adb shell '/data/local/tmp/fsiotrace -d 20 -x -o /data/local/tmp/run' &

# 다른 작업 일으키기
adb shell '
  dd if=/dev/zero of=/data/local/tmp/sync.dat bs=4k count=200 oflag=sync
  dd if=/dev/zero of=/data/local/tmp/dio.dat  bs=4k count=200 oflag=direct
  cat /data/local/tmp/sync.dat > /dev/null     # cache hit (VFS 만)
  sync
  rm /data/local/tmp/*.dat
  fstrim /data 2>/dev/null                      # discard 유발
'
```

## 4. 출력 라인 읽기

이벤트 한 줄 예 (가독성을 위한 의사표현. 실제 출력은 TAB 구분 17컬럼 TSV → `OUTPUT_FORMAT.md`):

```
ts=12345678  L=VFS  pid=4521  tid=4521  cpu=3  comm=mysqld
syscall=vfs_write  fs=ext4  dev=259:12  ino=983241
size=16384  off=0  sec=0  name=/data/ibdata1
io=0x0000010000000102  [WRITE|O_SYNC|DATA|SAW_VFS]
```

`name` 은 **풀패스**다(`/data/user/0/pkg/files/ibdata1`). `bpf_d_path()` 는 여전히
막혀 있지만 dentry 를 직접 walk 하고, 파일시스템 경계는 userspace 가
`/proc/self/mountinfo` 로 붙여 완성한다 — 자세한 건 [`DESIGN.md §5.1`](/fsiotrace/design/).
UFS row 는 IRQ 경로라 BPF 가 풀패스를 안 붙이고 userspace 가 inode 로 후보정한다.

### 필드 의미

| 필드 | 의미 |
|---|---|
| `ts` | `bpf_ktime_get_ns()` (단조 시계, ns) |
| `L`  | 이벤트가 잡힌 layer (VFS/FS/BLK/UFS) |
| `pid`, `tid`, `comm` | 진입한 task. **writeback 은 kworker** |
| `syscall` | hook 이름 그대로 (vfs_write, block_rq_issue, …) |
| `fs` | filesystem 이름 (ext4, f2fs, tmpfs, …) |
| `dev` | major:minor (`/sys/class/block/*/dev` 참고) |
| `ino` | inode 번호 |
| `size` | bytes (VFS = vfs_* 의 count/retval, BLK = `__data_len`, UFS = `transfer_len`) |
| `off` | file offset (VFS only) |
| `sec` | BLK 는 512B sector, UFS 는 LBA, VFS/FS 는 0 |
| `name` | **풀패스** (VFS/FS/BLK/UFS 전부) |
| `io` | u64 비트마스크. 자세한 의미는 §5 |
| `ufs={lun=… tag=… op=…}` | UFS row 의 추가 식별자 (extra 컬럼) |

### Layer hop marker (SAW_*) 활용

같은 IO 의 layer hop 을 따라가려면 SAW_* 비트를 보면 된다:
- `SAW_VFS` 켜진 BLK row → 같은 task 가 VFS 단계에서 직접 발행 (동기 IO 경로)
- `SAW_VFS` 꺼진 BLK row → writeback kworker (원본 process 정보 없음)

## 5. io_flags 비트 (참고용)

전체 정의는 `src/fsiotrace.h` 및 [`DESIGN.md §3`](/fsiotrace/design/#3-record-구조). 자주 쓰는 것만:

| bit | 16진 | 이름 | 언제 켜지나 |
|---|---|---|---|
| 0 | 0x1 | READ | 읽기 |
| 1 | 0x2 | WRITE | 쓰기 |
| 2 | 0x4 | DISCARD | UNMAP / TRIM |
| 3 | 0x8 | FLUSH | preflush/FUA/SYNCHRONIZE_CACHE |
| 8 | 0x100 | O_SYNC | file O_SYNC |
| 9 | 0x200 | O_DIRECT | DIO |
| 12 | 0x1000 | SYNC_PATH | fsync 가 트리거한 후속 IO |
| 16 | 0x10000 | DATA | 사용자 데이터 |
| 17 | 0x20000 | METADATA | fs 메타 |
| 22 | 0x400000 | JOURNAL | jbd2/log |
| 23 | 0x800000 | CHECKPOINT | f2fs checkpoint |
| 24 | 0x1000000 | GC | f2fs GC |
| 35 | 0x800000000 | WRITEBACK_KWORKER | kworker writeback |
| 40 | 0x10000000000 | SAW_VFS | VFS hook 거침 |
| 42 | 0x40000000000 | SAW_BLK | block hook 거침 |
| 43 | 0x80000000000 | SAW_UFS | UFS hook 거침 |

bit 풀이는 `-x` 플래그를 켜면 줄 끝에 `[WRITE|O_SYNC|...]` 가 18번째 컬럼으로 찍힌다.

## 6. 분석 (host 에서)

출력은 **TAB 구분 17컬럼 TSV** (컬럼 정의는 `OUTPUT_FORMAT.md`). awk 는 `-F'\t'` 로
컬럼 위치 기반 파싱한다. 주요 컬럼: `$2`=layer, `$3`=pid, `$6`=comm, `$7`=syscall,
`$8`=action, `$12`=ino, `$15`=name, `$16`=io_flags, `$17`=extra.

### 6.1 특정 파일에 대한 모든 활동

```sh
awk -F'\t' '$15 ~ /ibdata1$/' run.events   # 풀패스라 끝부분 매칭
```

### 6.2 layer 별 카운트

```sh
awk -F'\t' '{print $2}' run.events | sort | uniq -c
```

### 6.3 discard 만

```sh
# IO_DISCARD = bit 2 (0x4). io_flags 는 $16 (0x... hex)
awk -F'\t' 'strtonum($16) % 8 >= 4' run.events     # crude (bit2 검사)
```

### 6.4 VFS write → BLK Q → UFS send 흐름 따라가기

같은 task 의 시간 인접 이벤트를 묶어 보기:

```sh
# pid($3) 로 필터 + 시간($1) 순
awk -F'\t' '$3==4521' run.events | sort -k1 -g
```

writeback 의 경우 같은 inode 의 BLK/UFS row 와 매핑하려면:

```sh
# VFS row 에서 ino($12) 추출
awk -F'\t' '$2=="VFS"{print $12}' run.events | sort -u
```

그런 다음 inode 번호로 BLK row 의 sector 와 매핑은 host 에서
`debugfs -R "ncheck <ino>" /dev/block/dm-XX` (오프라인 분석).

### 6.5 latency (Q → C 페어링)

tracer 자체는 raw 만 출력하므로 pairing 은 후처리. BLK Q/C 는 같은
`(dev_major,dev_minor,sector,size)` = `($10,$11,$14,$13)` 로 매칭:

```sh
awk -F'\t' '
$8=="block_rq_issue"    { k=$10":"$11":"$14":"$13; q[k]=$1 }
$8=="block_rq_complete" { k=$10":"$11":"$14":"$13; if(k in q){print $1-q[k]; delete q[k]} }
' run.events
```

본격 분석은 별도 도구(예: 옆 디렉토리 `/Users/songhyun/project/trace/`)에서.

## 7. 종료 / 시그널

- **SIGINT(Ctrl-C) / SIGTERM / SIGHUP / SIGQUIT** 모두 깔끔 종료.
  종료 시 이벤트 카운트를 stderr 로 보고한다.
  장시간 수집에서 `SIGHUP`(adb/ssh 세션 끊김)을 안 받으면 버퍼가 통째로 날아가므로
  넓게 받는다.
- 종료 시 **detach 후 ringbuf 잔여 이벤트를 배수**한다 — 마지막 몇 건이 잘리지 않는다.
- `-d SEC` / `-m N` 으로 자동 종료 가능.

### `adb shell … > trace.log` 에서 Ctrl+C 가 안 먹을 때

**증상**: Windows 에서 `adb shell fsiotrace > trace.log` 로 돌리고 Ctrl+C 를 눌러도
device 쪽 프로세스가 안 죽거나, 죽어도 로그 끝이 잘린다.

**원인**: 리다이렉트하면 adb 가 PTY 를 할당하지 않는다(실측: 게스트에서 `tty -s` 실패).
PTY 가 없으면 Ctrl+C 가 만드는 터미널 시그널이 device 쪽 프로세스 그룹에 전달될 경로가
없다. 즉 SIGINT 를 아무리 잘 처리해도 **애초에 오지 않는다.**

**대응**(이미 구현됨): adb 가 죽으면 stdout 파이프가 닫히므로 write 가 `EPIPE` 를 낸다.
이걸 종료 신호로 삼아 정상 종료 경로로 빠진다. `SIGPIPE` 는 기본 동작이 즉사(버퍼 유실)라
`SIG_IGN` 으로 막고 `errno` 로 감지한다. 부모(adb shell)가 죽어 고아가 되는 것도 함께 감시한다.

→ 그래도 확실히 하려면 `-d SEC` 로 수집 시간을 못박는 쪽을 권장.

## 7.5 기기 없이 검증 (QEMU 테스트베드)

실기기 없이 **verifier 통과 · attach · 4개 레이어 이벤트 · ftrace 대조 · 풀패스 출력 ·
f2fs 세분 플래그**까지 확인할 수 있다. 실제로 이 환경에서 verifier 거부 2건과
동작 버그 여러 건을 잡았다.

```sh
bash scripts/qemu/run_qemu.sh      # VM 안에서 sh /bin/<test>.sh
```

- LU0 = ext4(`/data`), LU1 = f2fs(`/f2fs`) — f2fs 전용 경로도 검증 가능.
- 셋업 절차·준비물은 [`scripts/qemu/README.md`](../scripts/qemu/README.md).
- **재현 안 되는 것**: 삼성 vendor hook(`android_vh_ufs_*`), MCQ(QEMU 가 legacy SDB 로
  fallback), 실물 UFS 타이밍.

> **컴파일만 통과한 걸 "동작한다" 고 판단하지 말 것.** verifier 거부와 런타임
> 오동작은 컴파일로 안 잡힌다.

### ftrace 를 정답지로 쓰기

"커널이 안 찍음" 과 "우리가 못 받음" 은 같은 tracepoint 를 BPF 와 ftrace 로 동시에
받아 대조하면 즉시 갈린다:

```sh
echo 1 > /sys/kernel/tracing/events/ufs/ufshcd_command/enable
```

측정할 때 반드시 지킬 것:

- **ftrace 버퍼를 키운다.** 기본 7KB/cpu 라 고부하에선 ftrace 쪽이 먼저 넘쳐
  "손실이 음수" 로 나온다. `echo 65536 > /sys/kernel/tracing/buffer_size_kb` 후
  `per_cpu/*/stats` 의 `overrun` 이 0 인지 확인.
- **측정 창을 맞춘다.** fsiotrace attach 완료 후 ftrace 를 켜고, ftrace 를 먼저 끈 다음
  fsiotrace 를 종료한다. 안 맞추면 ±수 건이 흔들리는데 이건 유실이 아니라 창 오차다
  (부호가 음수로 뒤집히면 확실).
- **IO 를 끝내고 몇 초 쉰 뒤 종료한다.** 트레이서가 IO 한복판에서 끝나면 in-flight
  만큼 send 가 많게 나오는 게 정상이다.

## 8. 트러블슈팅

### 8.1 "warn: failed to attach …"

특정 hook 이 device 커널에 없거나 이름이 다른 경우. 다음을 확인:

```sh
adb shell 'grep -wE "T <symbol>$" /proc/kallsyms'
adb shell 'ls /sys/kernel/tracing/events/<subsys>/<event>/'
```

- `ufshcd_send_command` 가 없으면 vendor 가 다른 함수명 사용. `available_filter_functions` 에서 검색:
  ```sh
  adb shell 'grep -i ufshcd /sys/kernel/tracing/available_filter_functions | head'
  ```
  그 뒤 `src/fsiotrace.bpf.c` 의 `SEC("kprobe/ufshcd_send_command")` 를 수정.
- `vfs_read/write` 가 inline 처리되어 없으면 `ksys_read`/`ksys_write` 또는 `__arm64_sys_read` 대체.

### 8.2 BPF verifier 거부 (`dmesg | grep bpf`)

전형적인 원인:
- BTF 가 device 와 안 맞음 → `pull_btf.sh` 다시 받아 `make VMLINUX_BTF=…` 재빌드
- CO-RE relocation 실패 → struct field 가 다른 커널 버전. 해당 부분의 `BPF_CORE_READ` 단순화 또는 stub 추가
- instruction limit 초과 → `--no-fs` 등으로 hook 줄여 빌드/실행

### 8.3 SELinux 차단

```sh
adb shell 'getenforce'
adb shell 'setenforce 0'    # userdebug 에서만
```

production user build 면 sepolicy 작업이 별도로 필요. 본 프로젝트 범위 밖.

### 8.4 attach 는 되는데 이벤트가 안 찍힘

- `-p` 필터가 잘못 잡혔거나 `-I` mask 가 너무 빡빡
- IO 가 정말 page cache hit 라 disk 까지 안 내려감 (정상) — `-x` 켜고 `SAW_BLK` 켜진 row 가 있는지 확인
- ring buffer 가 작아 drop. `events` map size 를 늘리거나 `-p` 로 범위 좁히기

### 8.5 writeback 의 pid 가 다 `kworker`

정상. dirty page 의 실제 디스크 IO 는 30초쯤 뒤 kworker 가 수행하므로
원본 process 와 연결이 끊긴다. 자세한 건 [`DESIGN.md §4-4.4`](/fsiotrace/design/), [`DESIGN.md §5`](/fsiotrace/design/).
원본 task 와 매칭하려면:
- 동기 IO(O_SYNC/O_DIRECT/fsync) 로 테스트해서 SAW_VFS 가 켜진 BLK row 확인
- inode 매핑 fallback 은 **항상 켜져 있다** (`--wb-inode` 토글은 제거됨).
  `vfs_write` 가 `inode_ctx` 에 mirror 해 둔 걸 FS hook 이 lookup 해 보강한다

## 9. 알려진 한계 (현재 버전)

- UFS `tag→request` pairing: `tracepoint:scsi:scsi_dispatch_cmd_start` 가 주 경로,
  vendor 가 SCSI 미들레이어를 우회하는 경우 `kprobe:ufshcd_send_command` fallback.
  둘 다 attach 실패하면 UFS row 의 pid/filename 이 빈다.
- f2fs segment 세분 비트 (48-55): v6.6/6.12 는 `f2fs_submit_page_write`,
  v6.13+ upstream 은 `f2fs_submit_folio_write` 로 채움. 양쪽 SEC 동시 빌드, attach 자동
  선택. 두 이름 모두 없는 vendor 커널은 비트 미충전.
- 풀패스는 **동작한다**. `bpf_d_path()` 는 여전히 막혀 있지만(allowlist + trampoline 부재)
  dentry 를 직접 walk 하고, 파일시스템 경계에서 멈추는 부분은 userspace 가
  `/proc/self/mountinfo` 의 `dev → mountpoint` 를 앞에 붙여 완성한다
  (`/data/user/0/pkg/files/x.db`). 한계: 트레이싱 중 마운트가 바뀌면 어긋나고,
  마운트포인트를 못 찾으면 접두어 없이 마운트 기준 상대경로로 나온다.
- pairing(Q↔C, send↔complete) 은 tracer 가 하지 않음 → 외부 후처리

## 10. 결과 공유 시

문제 보고 시 아래를 함께 보내면 디버깅이 빠르다:

```sh
adb shell 'uname -r' > info.txt
adb shell 'getprop ro.build.fingerprint' >> info.txt
adb shell 'dmesg' > dmesg.txt
bash scripts/android_check.sh > check.log 2>&1
# events 파일 일부도 함께
head -200 run.events > run.head.txt
```
