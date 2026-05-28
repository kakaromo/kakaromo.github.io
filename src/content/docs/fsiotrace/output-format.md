---
title: fsiotrace TSV 출력 형식
description: 17컬럼 TSV 명세 — BLK/UFS row 파싱, UPIU extra, io_flags 비트, Rust 분석기 연동
---

Rust 분석기([Trace Analysis](/guide/trace-analysis/))가 fsiotrace 로그에서 **BLK row** 와
**UFS Cmd row** 두 종류만 파싱하는 경우의 형식 명세.

## 1. 공통 사항

- **TAB 구분, 헤더 없음, 한 줄 = 한 이벤트**
- **모든 row 가 정확히 17 컬럼**. 빈 값은 빈 string (`""`) 또는 `0` 또는 `-`.
- 줄 끝은 `\n` (LF).
- 인코딩: ASCII (filename / comm 에서 non-ASCII 가능하지만 sanitize 로
  TAB / newline 만 `_` 치환. 그 외 UTF-8 그대로 흘러갈 수 있음).
- 모든 row 는 `setvbuf(_IOLBF)` 로 라인 단위 flush. partial line 없음.

## 2. 컬럼 정의 (17개 고정)

| # | 이름 | 타입 | 예 | 비고 |
|---|---|---|---|---|
| 1 | `ts` | f64 | `12345.678901` | `bpf_ktime_get_ns()` 의 sec.us. 항상 6자리 소수. |
| 2 | `layer` | string | `VFS`/`FS`/`BLK`/`UFS` | 4종. **BLK 또는 UFS 만 필터링 대상** |
| 3 | `pid` | u32 | `4521` | `0` 가능 (idle/swapper) |
| 4 | `tid` | u32 | `4521` | |
| 5 | `cpu` | u32 | `3` | |
| 6 | `comm` | string | `mysqld` | TASK_COMM_LEN=16. TAB 은 `_` 로 sanitize |
| 7 | `syscall` | string | `vfs_write` 또는 `-` | VFS 안 거친 row 는 `-` |
| 8 | `action` | string | `block_rq_issue` / `ufshcd_command:send_req` | row 종류 식별 |
| 9 | `fs` | string | `ext4` / `f2fs` / `tmpfs` / `""` (빈) | BLK row 는 보통 비어 있음 |
| 10 | `dev_major` | u32 | `8` | |
| 11 | `dev_minor` | u32 | `32` | |
| 12 | `ino` | u64 | `983241` | 없으면 `0` |
| 13 | `size` | u64 | `16384` | bytes |
| 14 | `sec` | u64 | `8192000` | BLK 는 sector(512B), UFS 는 LBA |
| 15 | `name` | string | `ibdata1` 또는 `ino:42` 또는 빈 | filename. TAB 은 `_` 로 sanitize |
| 16 | `io_flags` | hex u64 | `0x0000010040002102` | 항상 `0x` + 16자리 hex |
| 17 | `extra` | string | `lun=0 tag=7 ...` (UFS) / `""` (BLK 은 현재 빈 값) | row 종류별 추가 key=value packed |

### 컬럼 split 예 (awk)

```awk
NR == 1 { FS = "\t" }
{
    ts        = $1
    layer     = $2
    pid       = $3
    ...
    extra     = $17    # 마지막 컬럼
}
```

## 3. BLK row 명세

### 식별

```
$2 == "BLK"   # 또는
$8 == "block_rq_issue" || $8 == "block_rq_complete"
```

### action 값

| action | 의미 | extra 패턴 |
|---|---|---|
| `block_rq_issue` | request 디스크 발급 | `""` (빈 값) |
| `block_rq_complete` | request 완료 | `""` (빈 값) |

> **BLK row 의 `extra`(col 17)는 현재 비어 있다.** block layer 의 rwbs 문자열
> 구성을 시도했으나 이 device verifier 가 거부(`R3 bitwise |= on pointer`)해
> revert 됐다 (DESIGN.md §5). READ/WRITE/DISCARD/FLUSH 구분은 `io_flags` 비트로
> 한다 (§5). request command flag(rwbs)가 필요하면 향후 별도 경로로 재시도해야 함.

### 핵심 컬럼

- `ts` — 발생 시각
- `pid/comm` — task_ctx 흡수 → 보통 VFS 발행자 (writeback 시 kworker)
- `syscall` — `vfs_read` / `vfs_write` / `vfs_fsync_range` / `-`
- `dev_major/dev_minor` — block device. `rq->part->bd_dev` 기준이라 **파티션 단위**
  (예: `8:32` = sda 의 32번 파티션). `rq->part` 없으면 `8:0` (disk base) fallback
- `ino` — VFS 흡수된 inode. writeback 시 fs hook 이 채울 수도
- `size` — request bytes (`rq->__data_len`)
- `sec` — 512B sector. flush 등 sector 무의미한 경우 `0` 으로 정규화 (BPF 측은 `u64=-1`)
- `name` — VFS 흡수된 filename (마지막 component) 또는 `ino:N`
- `io_flags` — 비트마스크 (§5)
- `extra` — **빈 값** (위 참조)

### 예시

```
12345.678920	BLK	4521	4521	3	mysqld	vfs_write	block_rq_issue	ext4	8	32	983241	16384	8192000	ibdata1	0x0000010000000102	
12345.679230	BLK	4521	4521	1	mysqld	vfs_write	block_rq_complete	ext4	8	32	983241	16384	8192000	ibdata1	0x0000010000000102	
```

(마지막 컬럼 `extra` 는 빈 문자열 — 줄은 여전히 17컬럼, col17 뒤에 값 없음.)

같은 IO 의 Q/C 매칭: `(dev_major, dev_minor, sec, size)` + 시간 인접도. 또는
`io_flags` 의 SAW_BLK 비트와 `pid`.

## 4. UFS Cmd row 명세

### 식별

```
$2 == "UFS" && ( $8 == "ufshcd_command:send_req" || $8 == "ufshcd_command:complete_rsp" )
```

> `ufshcd_command:` 로 시작하는 두 action 만 데이터 IO. 다른 UFS row
> (`ufshcd_upiu:*`, `ufshcd_uic_command`, `ufshcd_exception_event`) 는 별도 종류.

### action 값

| action | 의미 |
|---|---|
| `ufshcd_command:send_req` | SCSI 명령을 UFS 로 issue |
| `ufshcd_command:complete_rsp` | UFS 응답 수신 |

### 핵심 컬럼

- `ts` — 발생 시각
- `pid/comm` — ufs_tag_ctx 흡수 → BLK 단계의 task. miss 시 swapper/kworker
- `syscall` — VFS 거친 row 면 `vfs_read`/`vfs_write` 등, 아니면 `-`
- `dev_major/dev_minor` — BLK 단계에서 흡수. miss 시 `0:0`
- `ino/name` — BLK 흡수. miss 시 `0`/`""`
- `size` — `transfer_len` (UFS spec). bytes
- `sec` — **LBA** (UFS logical block. block size 보통 4KB)
- `io_flags` — 비트마스크. UFS opcode 별 비트 추가
- **`extra`** — UFS-specific 정보 (다음 절)

### extra 구조 (UFS Cmd)

key=value 공백 구분. 9개 키 (UPIU header 흡수 시):

```
lun=<u8>  tag=<u8>  hwq=<i32>  ufs_op=0x<u8>  grp=0x<u8>  txn=0x<u8>  flags=0x<u8>  func=0x<u8>  attr=<Simple|Ordered|HoQ|ACA>  cp=<0|1>
```

| key | 출처 | 의미 |
|---|---|---|
| `lun` | tracepoint `lun` | UFS logical unit (0-7) |
| `tag` | tracepoint `tag` | UPIU task tag (0-31 보통) |
| `hwq` | tracepoint `hwq_id` | hardware queue id. -1 가능 (signed) |
| `ufs_op` | tracepoint `opcode` | SCSI opcode. **이게 핵심**: `0x28`=READ_10, `0x2A`=WRITE_10, `0x42`=UNMAP(discard), `0x35`=SYNC_CACHE_10, `0x88`=READ_16, `0x8A`=WRITE_16 |
| `grp` | tracepoint `group_id` | UFS group ID (WriteBooster / multi-stream) |
| `txn` | UPIU hdr[0] | Transaction code. send 시 `0x01` (Cmd), complete 시 `0x21` (Data-In) 또는 `0x26` (Response) |
| `flags` | UPIU hdr[1] | UPIU flags. bit 5=R, bit 6=W, bit 2=CP (spec base) |
| `func` | UPIU hdr[5] | tm_function 또는 query_function (Cmd UPIU 에선 보통 0) |
| `attr` | flags bit 0-1 | `Simple` / `Ordered` / `HoQ` / `ACA` |
| `cp` | flags bit 2 | command priority flag, `0`/`1` |

> **주의 1**: UPIU header 흡수 못 한 경우 (드물게) `txn/flags/func/attr/cp`
> 키가 빠집니다. 그럼 extra 는 `lun=... grp=0x...` 까지만.
>
> **주의 2**: complete 시점의 `txn` 은 `0x01` 이 아니라 `0x21` (Data-In) 또는
> `0x26` (Response UPIU). 이건 응답 UPIU 의 transaction code.

### 예시

```
# send_req (Cmd UPIU 흡수됨)
12345.678935	UFS	4521	4521	3	mysqld	vfs_write	ufshcd_command:send_req	ext4	8	32	983241	16384	1024000	ibdata1	0x0000080040002102	lun=0 tag=7 hwq=0 ufs_op=0x2a grp=0x0 txn=0x01 flags=0x42 func=0x00 attr=Simple cp=0

# complete_rsp
12345.679210	UFS	4521	4521	1	mysqld	vfs_write	ufshcd_command:complete_rsp	ext4	8	32	983241	16384	1024000	ibdata1	0x0000080040002102	lun=0 tag=7 hwq=0 ufs_op=0x2a grp=0x0 txn=0x26 flags=0x00 func=0x00 attr=Simple cp=0

# UFS_TAG_CTX miss (cross-layer 정보 없음) — comm/ino/name 비어있음
12399.123456	UFS	0	0	0	swapper/0	-	ufshcd_command:send_req		8	0	0	4096	2048000		0x0000080000000001	lun=0 tag=12 hwq=0 ufs_op=0x28 grp=0x0
```

### send ↔ complete pairing

UFS 사양상 같은 IO 의 send 와 complete 는 **같은 `tag`**. (lun 도 같음.)

```awk
$2 == "UFS" && $8 ~ /ufshcd_command:/ {
    match($17, /tag=([0-9]+)/, m); tag = m[1]
    if ($8 == "ufshcd_command:send_req")    send_ts[tag] = $1
    if ($8 == "ufshcd_command:complete_rsp" && tag in send_ts) {
        latency_us = ($1 - send_ts[tag]) * 1000000
        print tag, latency_us
        delete send_ts[tag]
    }
}
```

## 5. io_flags 비트 (참고)

`io_flags` 는 u64 hex. 정의는 `src/fsiotrace.h`. 가장 자주 쓰일 비트만:

| bit | 0x | 이름 | 의미 |
|---|---|---|---|
| 0 | 0x1 | READ | |
| 1 | 0x2 | WRITE | |
| 2 | 0x4 | DISCARD | |
| 3 | 0x8 | FLUSH | |
| 8 | 0x100 | O_SYNC | |
| 9 | 0x200 | O_DIRECT | |
| 12 | 0x1000 | SYNC_PATH | fsync 트리거된 후속 IO |
| 13 | 0x2000 | REQ_SYNC | block 'S' |
| 16 | 0x10000 | DATA | |
| 17 | 0x20000 | METADATA | |
| 22 | 0x400000 | JOURNAL | |
| 23 | 0x800000 | CHECKPOINT | |
| 24 | 0x1000000 | GC | |
| 35 | 0x800000000 | WRITEBACK_KWORKER | |
| 40 | 0x10000000000 | SAW_VFS | |
| 42 | 0x40000000000 | SAW_BLK | |
| 43 | 0x80000000000 | SAW_UFS | |

`trace/` 분석기에서 BLK / UFS row 만 본다면 주요 사용 비트:
- `IO_READ` / `IO_WRITE` / `IO_DISCARD` / `IO_FLUSH` — IO 종류 분류
- `IO_SAW_VFS` — task 가 VFS 거쳤는가 (writeback 구분)
- `IO_REQ_SYNC` — sync flag

## 6. trace/ Rust 분석기 구현 가이드

### Struct (`src/models/fsiotrace_block.rs` 등 신규)

```rust
#[derive(Debug, Clone)]
pub struct FsioBlock {
    pub time: f64,
    pub cpu: u32,
    pub pid: u32,
    pub comm: String,
    pub action: String,       // "block_rq_issue" or "block_rq_complete"
    pub dev_major: u32,
    pub dev_minor: u32,
    pub ino: u64,
    pub size: u64,
    pub sector: u64,
    pub name: String,
    pub io_flags: u64,
    // 현재 BLK extra 는 빈 값이라 항상 "" (DESIGN §5 rwbs revert). io 종류는 io_flags 로.
    pub rwbs: String,
}

#[derive(Debug, Clone)]
pub struct FsioUfsCmd {
    pub time: f64,
    pub cpu: u32,
    pub pid: u32,
    pub comm: String,
    pub action: String,       // "ufshcd_command:send_req" or ":complete_rsp"
    pub dev_major: u32,
    pub dev_minor: u32,
    pub ino: u64,
    pub size: u64,
    pub lba: u64,
    pub name: String,
    pub io_flags: u64,
    pub lun: u8,
    pub tag: u32,
    pub hwq: i32,
    pub ufs_op: u8,
    pub grp: u8,
    pub txn: Option<u8>,      // UPIU 흡수 시 Some
    pub upiu_flags: Option<u8>,
    pub upiu_func: Option<u8>,
    pub upiu_attr: Option<String>,
    pub upiu_cp: Option<u8>,
}
```

### Parser 의사 코드

```rust
fn parse_line(line: &str) -> Option<FsioRow> {
    let cols: Vec<&str> = line.split('\t').collect();
    if cols.len() < 17 { return None; }    // 항상 17

    let layer = cols[1];
    let action = cols[7];

    match layer {
        "BLK" => Some(FsioRow::Block(parse_block(&cols))),
        "UFS" if action.starts_with("ufshcd_command:") =>
            Some(FsioRow::UfsCmd(parse_ufs_cmd(&cols))),
        _ => None,    // VFS / FS / UFS UPIU/UIC/Exception 무시
    }
}
```

### extra 파싱 (key=value 공백 구분)

```rust
fn parse_extra(extra: &str) -> HashMap<&str, &str> {
    extra.split(' ')
         .filter_map(|tok| tok.split_once('='))
         .collect()
}
```

`ufs_op` 같은 hex 값: `u8::from_str_radix(v.trim_start_matches("0x"), 16)`.

### 빠른 prefilter

`trace/` 가 한 줄 한 줄 처리하기 전에 첫 컬럼 (`layer`) 만 보고 무시 가능:

```rust
let first_tab = line.find('\t')?;
let second_tab = line[first_tab+1..].find('\t')? + first_tab + 1;
let layer = &line[first_tab+1..second_tab];
if layer != "BLK" && layer != "UFS" { return None; }
```

100% TAB-positional split 이라 빠릅니다.

## 7. 알려진 변동 사항

- **`-x`(--decode) 켜면 18번째 컬럼 추가**: io_flags 비트를 사람이 읽는
  `[WRITE|O_SYNC|DATA|...]` 로 푼 컬럼이 17컬럼 **뒤에** 붙는다. 분석기는 `cols.len() >= 17`
  로 검사하고 17번째까지만 읽으면 영향 없다(18번째는 무시). 기본(`-x` 없음)은 17컬럼.
- `comm` 길이 ≤ 16, TAB 없음 (sanitize 보장)
- `name` 길이 ≤ 64 (FNAME_LEN). TAB 없음
- `extra` 길이 ≤ 256
- `dev_major` `dev_minor` 가 모두 0 인 row 가 BLK 에 있다면 BPF 가 dev 못 읽은 것 (드뭄)
- `sec` = 0 이면 flush/discard 또는 BLK 가 sector 의미 없는 경우 (BPF 가
  `u64=-1` → userspace 가 0 으로 정규화)
- ufs_tag_ctx miss 시 UFS row 의 cross-layer 정보 (`ino`/`name`/syscall/fs/dev)
  가 모두 비거나 0
