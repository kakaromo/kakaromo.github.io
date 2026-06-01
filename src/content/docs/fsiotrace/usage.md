---
title: fsiotrace 사용법
description: 빌드된 fsiotrace 바이너리로 IO 를 추적·분석하는 방법 — 옵션, 출력 해석, host 후처리
---

이 문서는 빌드된 `fsiotrace` 바이너리로 실제 IO를 추적하고
분석하는 방법을 설명한다. 빌드는 [빌드 가이드](/fsiotrace/build/), 설계는 [설계 문서](/fsiotrace/design/).

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
| `--wb-inode` | writeback inode 매핑 활성 (실험적) |
| `--rb-size=MB` | ringbuf 크기 (기본 8MB). 고부하·QD 클 때 `diag[9]` drop 보이면 ↑ |
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

이벤트 한 줄 예 (가독성을 위한 의사표현. 실제 출력은 TAB 구분 17컬럼 TSV → [TSV 출력 형식](/fsiotrace/output-format/)):

```
ts=12345678  L=VFS  pid=4521  tid=4521  cpu=3  comm=mysqld
syscall=vfs_write  fs=ext4  dev=259:12  ino=983241
size=16384  off=0  sec=0  name=ibdata1
io=0x0000010000000102  [WRITE|O_SYNC|DATA|SAW_VFS]
```

`name` 은 **항상 dentry 마지막 컴포넌트**(`ibdata1`)뿐이다. 풀패스(`bpf_d_path()` /
manual d_parent walk)는 이 device verifier 가 거부해 제거됐다. 풀패스가 필요하면
호스트에서 `ino` 로 `debugfs -R "ncheck <ino>"` 또는 `/proc/<pid>/fd` 로 복원.

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
| `name` | dentry 마지막 컴포넌트 (VFS/FS) |
| `io` | u64 비트마스크. 자세한 의미는 §5 |
| `ufs={lun=… tag=… op=…}` | UFS row 의 추가 식별자 (extra 컬럼) |

### Layer hop marker (SAW_*) 활용

같은 IO 의 layer hop 을 따라가려면 SAW_* 비트를 보면 된다:
- `SAW_VFS` 켜진 BLK row → 같은 task 가 VFS 단계에서 직접 발행 (동기 IO 경로)
- `SAW_VFS` 꺼진 BLK row → writeback kworker (원본 process 정보 없음)

## 5. io_flags 비트 (참고용)

전체 정의는 `src/fsiotrace.h` 및 [설계 문서 §3 Record 구조](/fsiotrace/design/#3-record-구조). 자주 쓰는 것만:

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

출력은 **TAB 구분 17컬럼 TSV** (컬럼 정의는 [TSV 출력 형식](/fsiotrace/output-format/)). awk 는 `-F'\t'` 로
컬럼 위치 기반 파싱한다. 주요 컬럼: `$2`=layer, `$3`=pid, `$6`=comm, `$7`=syscall,
`$8`=action, `$12`=ino, `$15`=name, `$16`=io_flags, `$17`=extra.

### 6.1 특정 파일에 대한 모든 활동

```sh
awk -F'\t' '$15=="ibdata1"' run.events
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

본격 분석은 별도 Rust 분석기([Trace Analysis](/guide/trace-analysis/))에서.

## 7. 종료 / 시그널

- SIGINT (Ctrl-C), SIGTERM 으로 깔끔 종료. 종료 시 이벤트 카운트를 stderr 로 보고.
- `-d SEC` / `-m N` 으로 자동 종료 가능.

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
원본 process 와 연결이 끊긴다. 자세한 건 [설계 문서 §4 cross-layer 전파](/fsiotrace/design/#4-cross-layer-정보-전파-전파-map-구조), [§5 verifier 제약](/fsiotrace/design/).
원본 task 와 매칭하려면:
- 동기 IO(O_SYNC/O_DIRECT/fsync) 로 테스트해서 SAW_VFS 가 켜진 BLK row 확인
- 또는 `--wb-inode` 로 inode 매핑 fallback (실험적)

## 9. 알려진 한계 (현재 버전)

- UFS `tag→request` pairing: `tracepoint:scsi:scsi_dispatch_cmd_start` 가 주 경로,
  vendor 가 SCSI 미들레이어를 우회하는 경우 `kprobe:ufshcd_send_command` fallback.
  둘 다 attach 실패하면 UFS row 의 pid/filename 이 빈다.
- f2fs segment 세분 비트 (48-55): v6.6/6.12 는 `f2fs_submit_page_write`,
  v6.13+ upstream 은 `f2fs_submit_folio_write` 로 채움. 양쪽 SEC 동시 빌드, attach 자동
  선택. 두 이름 모두 없는 vendor 커널은 비트 미충전.
- 풀패스 불가: 이 device verifier 가 `bpf_d_path()` 와 manual d_parent walk 를 모두
  거부 → `name` 은 항상 dentry 마지막 컴포넌트(≤64B, FNAME_LEN). 풀패스는 호스트에서
  `ino` → `debugfs -R "ncheck <ino>"` 또는 `/proc/<pid>/fd` 로 후처리.
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
