---
title: Trace Analysis (I/O trace 분석)
description: UFS / Block / UFSCUSTOM trace 로그 업로드, 차트 + 상세 통계 분석, Filter/Brush/Zoom 조작 — 전체 사용자 가이드
---

Trace 페이지는 수집된 **UFS / Block / UFSCUSTOM I/O trace 로그**를 업로드하고 차트·통계로 분석하는 도구입니다. 1M~수천만 이벤트 규모의 로그를 다룰 수 있도록 설계됐습니다.

---

## 1. 접속 · 레이아웃

상단 네비게이션에서 **Trace** 메뉴 클릭 (아이콘: activity).

레이아웃은 3단계로 변화합니다:

| 상태 | 구성 |
|---|---|
| **진입 직후** | 좌측 Jobs 리스트 (20%) + 우측 안내 (업로드/선택 유도) |
| **Job 선택 시** | 좌측 자동 접힘 (얇은 사이드바) + 우측 분석 뷰 전체 |
| **완전 숨김** | 상단 툴바의 패널 아이콘으로 좌측 완전 제거 |

좌측 패널은 **드래그로 크기 조절** 가능하며, 조절한 크기는 자동 저장됩니다 (브라우저 localStorage).

---

## 2. 업로드

상단 툴바의 **[+ 업로드]** 버튼 → 다이얼로그에서 로그 파일 선택 또는 드래그 앤 드롭.

**지원 포맷**:
- `.log` `.txt` — ftrace, blktrace CSV, UFSCUSTOM 원본
- `.zip` `.gz` `.zst` `.tar` `.xz` `.7z` — 압축 자동 해제

**업로드 단계 표시 (다이얼로그)**:

| 단계 | 라벨 | 설명 |
|---|---|---|
| `probing` | "업로드 경로 확인 중" | MinIO 직접 PUT 경로가 닿는지 3초 안에 확인 |
| `init` | "업로드 준비 중" | 서버가 multipart 시작 + presigned URL 발급 |
| `uploading` | "MinIO 업로드 중 · 용량 / %" | 64MB 단위 4개 동시 PUT (가장 빠름) |
| `finalizing` | "서버에서 마무리 중 (MinIO 합치는 중)" | 큰 파일은 수십 초 걸릴 수 있음 |
| `fallback` | "안전 모드 업로드 중 (Spring 경유 — 방화벽 우회)" | 직접 PUT 차단 환경에서 자동 전환 — 느리지만 끝까지 진행됨 |

**파싱 단계 (Job 리스트)**: 다이얼로그 닫혀도 백그라운드 진행
1. `다운로드` — Rust 서비스가 MinIO 에서 원본 가져옴
2. `파싱` — 라인별 이벤트 추출 (UFS / Block / UFSCUSTOM 자동 분류)
3. `변환` — latency/QD/continuous/aligned 계산 후 Arrow RecordBatch
4. `업로드` — parquet 으로 MinIO 저장
5. `완료` — 분석 가능 상태

진행률은 Job 리스트에 **단계명 + %** 로 실시간 표시됩니다.

:::tip
방화벽 환경에서 처음 업로드할 때 "안전 모드"로 전환되면 같은 세션 동안은 캐시되어 즉시 안전 모드로 진행됩니다 (재검사 없음). 다른 브라우저 세션에서는 다시 3초 probe 후 결정.
:::

:::tip
한 로그 파일에 UFS 와 Block 이벤트가 모두 포함돼 있으면 **두 개의 parquet (`ufs.parquet`, `block.parquet`) 이 동시에 생성**됩니다. 상단 툴바의 `UFS` / `BLOCK` 버튼으로 전환해 각각 분석하세요.
:::

---

## 3. Job 목록

`/trace` 페이지에 **모든 사용자의 Job** 이 최신 순으로 나열됩니다 (전체 공개).

| 상태 | 배지 색상 | 설명 |
|---|---|---|
| `UPLOADING` | 회색 | 업로드 중 |
| `UPLOADED` | 회색 | 업로드 완료, 파싱 대기 |
| `PARSING` | 주황 | 파싱 진행 중 |
| `PARSED` | 녹색 | 분석 가능 |
| `FAILED` | 빨강 | 파싱 실패 (에러 메시지 표시) |

**Owner 필터 (상단 툴바)**:
- **모든 사용자** (기본) — 전체 표시
- **내 Job** — 본인이 업로드한 Job 만
- **사용자별 dropdown** — 특정 사용자 선택

필터 적용 시 카운트는 `필터링된 / 전체` 형태로 표시.

**권한**:
- **차트 / 통계 조회** — 모든 사용자 가능
- **삭제 / 재파싱** — 업로드한 본인 + admin 만

**자동 갱신**: `PARSING` 또는 `UPLOADED` 상태 Job 이 있으면 2초마다 리스트 자동 새로고침. 완료되면 자동 정지.

**수동 새로고침**: 상단 툴바 [새로고침] 버튼.

Job 클릭 시 → `/trace/{jobId}` 분석 페이지로 이동. 분석 페이지에서는 **차트 + 통계가 동시에 시작** 되어, Statistics 탭으로 바로 전환해도 즉시 표시됩니다.

---

## 4. Parquet 타입 선택

상단 툴바에 업로드 결과로 생성된 parquet 종류가 버튼으로 나타납니다:

- **UFS** — ufshcd_command 이벤트 (send_req / complete_rsp)
- **BLOCK** — block_rq_issue / block_rq_complete 이벤트
- **UFSCUSTOM** — UFSCustom 포맷 (action 없음, start/end_time 사용)

버튼 클릭 → 해당 parquet 기준으로 차트/통계 로드.

---

## 5. Raw Chart 탭

### 5.1 차트 종류 (좌측 사이드바)

| 차트 | Y축 | 비고 |
|---|---|---|
| **LBA** | Logical Block Address | 공간 분포 |
| **Queue Depth** | QD | 동시 outstanding |
| **CPU** | CPU core | **2 모드** — CMD (Y=CPU 0~8, 색상=cmd) / LBA (Y=LBA, 색상=CPU 0~7) |
| **DtoC Latency** | Dispatch→Complete (ms) | complete 이벤트만 |
| **CtoD Latency** | Complete→Dispatch (ms) | send 이벤트만 |
| **CtoC Latency** | Complete→Complete (ms) | complete 이벤트만 |

**토글**: 사이드바 차트 이름 클릭으로 visible 전환. 최소 1개는 유지.

### 5.2 Action 탭 (UFS/Block 만)

- **Send** — `send_req` / `block_rq_issue` / `Q` 이벤트
- **Complete** — `complete_rsp` / `block_rq_complete` / `C` 이벤트 (기본)
- **All** — 모든 이벤트

UFSCUSTOM 은 이벤트가 모두 완료 상태라 Action 탭이 표시되지 않습니다.

### 5.3 cmd 별 색상

| 그룹 | 색 |
|---|---|
| **Read** (0x28 / RA / R …) | 파랑 계열 |
| **Write** (0x2a / WA / W …) | 주황·빨강 계열 |
| **Flush** (0x35 / FF …) | 초록 계열 |
| **Discard** (0x42 / D …) | 보라 계열 |
| **Other** | 회색 계열 |

우측 **Legend** 에서 cmd 클릭 → 해당 cmd 숨김/표시 (모든 차트 동기화).

### 5.4 CPU 차트 2 모드

카드 헤더의 `CMD` / `LBA` 탭으로 전환:

- **CMD** (기본): X=time, Y=CPU(0~8 고정), 색상=cmd → "어느 CPU 가 어떤 I/O 를 했나"
- **LBA**: X=time, Y=LBA, 색상=CPU 0~7 팔레트 → "어느 LBA 요청이 어느 CPU 에서 처리됐나"

### 5.5 차트 크기 조절

차트 카드의 **우측 하단 모서리 드래그** → 높이 조절. 한 차트 조절 시 모든 차트 동시에 같은 높이.

### 5.6 Zoom · Pan

- **마우스 휠** — X축 zoom in/out (모든 차트 X 동기화)
- **드래그** — pan
- zoom 종료 후 250ms 디바운스 → 해당 시간 범위로 **서버 재요청** (더 촘촘한 샘플)
- Meta bar 의 **[전체 범위로]** 링크 → zoom 초기화

### 5.7 영역 선택 (Brush)

차트 위 **우클릭** → **영역 선택 (X+Y)** 메뉴 → 드래그로 사각형 → 드래그 종료 시:
- X 범위 → Filter 의 Time min/max
- Y 범위 → 해당 차트에 맞는 필드 (LBA / QD / DtoC / CtoD / CtoC)
- Filter 자동 펼침 + Chart + Statistics 동시 재조회

---

## 6. Raw Data 탭

1억 row 까지 다룰 수 있는 raw 이벤트 표 (ReadParquet RPC + DataTable 무한 스크롤) + 클릭 row 의 원본 .log 라인 컨텍스트 (GetRawLogLines RPC).

### 6.1 무한 스크롤 페이지네이션

- 첫 페이지 진입 시 5,000 row 까지 fetch (default page size)
- 스크롤이 마지막 row 근처 도달 시 다음 페이지 자동 fetch — keyset cursor (`cursor_time` + `cursor_line_number`) 로 OFFSET 누적 스캔 회피
- "더 없음" 신호는 응답의 `next_cursor_*` 가 모두 미설정일 때
- loadMore race 가드 — fetch 중복 방지 (`92443db`)

### 6.2 컬럼

UFS/Block/UFSCustom 별로 다른 컬럼셋. 공통 핵심:
- **time** (s) — 시간순 정렬 보장 (parquet 은 `(action, opcode, time)` 저장이지만 응답 직전 time 재정렬)
- **line_number** — 1-based 원본 .log 라인 번호 (raw log 뷰어 키)
- **action / opcode / lba / size / dtoc / qd / cpu / process** 등

### 6.3 원본 .log 라인 뷰어 (Dialog)

table 의 row 클릭 → 원본 .log 의 ± 컨텍스트 라인 (default ±10) Dialog 표시. 이전엔 Sheet 였다가 화면 차지가 커서 Dialog 로 전환 (`f70ff59`).

- **target line 강조** — Dialog 안에서 클릭한 row 의 line 이 highlight
- **컨텍스트 ±50 까지** — 서버 클램프
- **lazy single-pass** — Rust 가 `BufReader::next_line()` 한 패스로 멀티 GB .log 도 OOM 없이 처리

---

## 7. Logs 탭 — logcat / kmsg 첨부 + 시간범위 검색

trace job 1 개에 보조 로그 (logcat / kmsg / custom) 를 첨부하고, 같은 페이지의 chart zoom 시간범위로 즉시 검색 (`e35170d`).

### 7.1 업로드

- **kind**: `logcat` / `kmsg` / `custom`
- **format**: `auto` / `logcat-short` (MM-DD) / `logcat-long` (YYYY-MM-DD) / `kmsg` ([secs.ms] monotonic)
- **anchor** (kmsg 의 `bootEpochMs` 또는 logcat short 의 `referenceYear`) — wall-clock 환산용
- **업로드 경로**: presigned multipart — 브라우저가 직접 MinIO 에 PUT (JVM 우회). 100MB+ 파일도 4-way 병렬 업로드

업로드된 로그는 panel 목록에 라디오로 표시. 선택 후 검색.

### 7.2 시간범위 검색

- chart 의 zoom 영역이 자동으로 시작/종료 시간으로 들어옴 (수동 입력도 가능)
- 검색 클릭 → MinIO range-GET 이진탐색으로 startOffset 결정 (~30회 GET) → end-time 까지 line-by-line 매칭
- **1초 이내 첫 응답** (100GB 파일도) — Rust trace 서비스 거치지 않고 Java 단에서 직접 처리
- limit default 5,000 / max 50,000 (`truncated` flag 표시)

### 7.3 운영 함정

- 업로드 시 `format: auto` 가 잘못 추정하면 시간 변환이 빗나감 → 결과 0건. format 직접 지정 권장
- bucket whitelist — `log-search` 가 포털이 발급한 traceLogId 만 받음 (`edab1f3` objectKey hijack 방어). 직접 bucket/key 지정 모드는 `/log-search` 단독 페이지에서만 가능

---

## 8. Statistics 탭

**탭 진입 시 자동 조회** (이미 결과가 있으면 재사용). 다음 순서로 표시:

### 6.1 Overview (4 카드)

- Total Events / Send count
- Duration (초)
- Continuous ratio (%) — 이전 complete 의 `lba+size == lba`
- Aligned ratio (%) — `lba % 8 == 0 && size % 8 == 0`

### 6.2 I/O Amount (3 카드)

Read / Write / Discard 총 바이트 (사람 읽기 쉬운 단위).

### 6.3 Latency Statistics

DtoC / CtoD / CtoC / QD 4 탭. 각 탭에 min / max / avg / stddev / median / p99 / p999 / p9999 / p99999 / p999999 / count.

### 6.4 CMD Statistics

5 탭 (Overview / DtoC / CtoD / CtoC / QD). 각 cmd 별 집계 테이블:
- Overview: count / send / ratio / total size / continuous / 4 latency avg
- Latency 탭: cmd × (min/max/avg/stddev/median/p99/p999/p9999) 테이블

**검색**: CMD 컬럼 기준 필터 박스 지원.

### 6.5 Latency Histogram

cmd × latency_type (DtoC/CtoD/CtoC) 별 버킷 카운트. Filter 의 "Latency Ranges" 텍스트 박스에서 버킷 경계를 조정할 수 있습니다 (기본 `0.1, 0.5, 1, 5, 10, 50, 100, 500, 1000`).

### 6.6 CMD + Size Count

cmd 별로 I/O 크기 분포 테이블.

---

## 9. Filter

우측 상단 `Filter` 바를 펼치면 12개 필드:

- **Time** min / max (ms)
- **LBA** min / max
- **QD** min / max
- **DtoC** min / max (ms)
- **CtoD** min / max (ms)
- **CtoC** min / max (ms)

그리고 **Latency Ranges** (histogram 버킷 경계, comma-separated ms).

버튼:
- **조회** — Chart + Statistics 모두 재조회 (현재 탭 유지)
- **초기화** — 모든 필드 리셋 + timeRange 리셋

활성 필터가 있으면 Filter 버튼 옆에 **파란 점** 표시.

:::tip
**Brush 드래그**가 가장 빠른 필터 방법입니다. 차트에서 관심 영역을 우클릭→영역 선택→드래그하면 Time + Y 축 모두 자동 입력돼요.
:::

---

## 10. Samples (다운샘플링)

상단 툴바의 **Samples** 드롭다운으로 서버에서 받아올 최대 포인트 수 조정:

| 값 | 용도 |
|---|---|
| 10k | 빠른 확인 |
| 50k | 일반 분석 (ECharts 기본값) |
| 100k | 상세 분석 |
| 200k | 밀도 보고 싶을 때 |
| 500k | **Deck.gl 렌더러 권장** |
| 1M | 최대 — 5GB 이상 파일 전용 |

값을 바꾸면 즉시 차트 재조회. ECharts 는 200k 이상에서 렌더 지연이 체감되므로 필요하면 Deck.gl 렌더러로 전환 (섹션 12 참조).

:::caution
실제 반환 포인트 수는 데이터 시간 분포에 따라 설정값보다 적을 수 있습니다. 예를 들어 대부분 이벤트가 특정 시간대에 몰려있으면 Sample 1M 설정이어도 수천~수만 수준만 나올 수 있어요. 이 경우 zoom 으로 해당 시간대만 좁혀서 재조회하세요.
:::

---

## 11. Job 관리

### 재파싱 (Reparse)

툴바 **[재파싱]** 버튼 → 확인 다이얼로그 → 원본 로그 기준으로 다시 파싱.

언제 사용:
- 파서 버그 수정된 Rust 버전 배포 후
- DB 에 파싱 에러로 남은 Job 복구 시도

파싱 중/업로드 중 상태일 땐 버튼이 비활성화됩니다.

### 삭제

툴바 **[삭제]** 버튼 → 확인 → 아래 3가지 동시 삭제:
- MinIO 원본 (`trace-uploads/...`)
- MinIO parquet (`trace-parquet/...`)
- DB `portal_trace_jobs` + `portal_trace_parquets` row

**복구 불가**하므로 주의.

---

## 12. ExportXlsx — Excel 분할 다운로드

Trace 결과의 raw row 를 필터 적용 후 Excel 파일로 다운로드. 1억 row 까지 처리 (`a1d4aa3` + `5aaff18`).

### 12.1 다운로드 트리거

`/trace/[jobId]` 페이지 헤더의 **Excel** 버튼 → 현재 적용된 Filter / Time range / Parquet 타입 그대로 ExportXlsx RPC 호출.

### 12.2 진행 단계 (SSE 스트림)

| % | message | 의미 |
|---|---|---|
| 5–20 | Downloading | parquet 다운로드 |
| **22** | **Sorting rows by time (DuckDB merge)** | **글로벌 시간순 머지·정렬** — chunk 경계에서도 시간 단조증가 |
| 25–48 | Reading / Filtering | parquet 읽기 + filter 적용 |
| 55–80 | Writing xlsx | 1M row/file 분할 쓰기 (`constant_memory`) |
| 85–90 | Compressing | **분할된 경우에만** ZIP STORED |
| 93 | Uploading | MinIO 업로드 |
| 98 | URL | presigned 다운로드 URL 발급 |
| 100 | Completed | 자동 다운로드 시작 |

stage 라벨에 정렬 단계가 명시되도록 개선됨 (이전엔 "읽기/필터" 만 있었음).

### 12.3 파일명 규약

- 파일 1개일 때: `{trace_type}_{startTime}_{endTime}.xlsx` (소수점 3자리 ms)
- 분할되어 ZIP 으로 묶일 때: ZIP 안에 `{trace_type}_{startTime}_{endTime}.xlsx` 가 N 개
- chunk 1 = 가장 이른 시간, chunk N = 가장 나중 시간 (22% 단계 글로벌 정렬 덕분)

### 12.4 운영 자동 정리

`exports/` prefix 의 zip 은 매일 04:00 KST 에 24h 이상 지난 파일이 자동 삭제됩니다 (`TraceExportCleanupTask` + `0d0edc7` — `(userId, jobId)/exports/` prefix 단위로 좁혀 다른 사용자 영향 없음).

---

## 13. 에러 메시지

시스템은 상세 원본 에러를 사용자 친화 메시지로 변환합니다:

| 상황 | 표시 |
|---|---|
| gRPC payload 크기 초과 | "차트 데이터가 너무 커요 — Samples 값을 줄이거나 시간 범위를 좁혀서 다시 시도해 주세요" |
| 서버 연결 실패 | "서버와 연결할 수 없어요 — 잠시 후 다시 시도하거나 Portal 서버 상태를 확인해 주세요" |
| 인증 만료 | "접속이 만료됐어요 — 로그인 페이지로 이동해 다시 로그인해 주세요" |
| Rust 서비스 다운 | "분석 서버(Rust)에 연결할 수 없어요 — trace 서비스가 꺼져 있거나 재시작 중일 수 있어요" |
| parquet 없음 | "분석 파일을 찾을 수 없어요 — Job 이 삭제됐거나 아직 파싱 중일 수 있어요" |

**[다시 시도]** 버튼이 있으면 클릭해서 재시도. **[자세히]** 링크로 원본 에러를 펼쳐볼 수 있습니다.

---

## 14. 키보드 · 마우스 단축

| 입력 | 동작 |
|---|---|
| 마우스 휠 (차트 위) | X축 zoom in/out |
| 드래그 (차트 위) | Pan |
| 더블 클릭 (차트 위) | Zoom in |
| 우클릭 (차트 위) | 영역 선택 메뉴 |
| 드래그 (차트 카드 우측 하단 모서리) | 높이 조절 |
| 드래그 (좌측/센터 경계) | 패널 너비 조절 |
| Legend 클릭 | cmd 숨김/표시 |

---

## 15. 고급: Deck.gl 렌더러 (선택)

**Deck.gl WebGL 렌더러** 를 사용하면 500k~1M+ 포인트도 60fps 로 부드럽게 렌더됩니다. 여러 시간대 trace 를 밀도있게 비교할 때 유용합니다.

### 활성화

프론트 빌드 환경변수:

```bash
# .env.local
VITE_TRACE_RENDERER=deckgl
```

빌드 후 Portal 재기동하면 `/trace` 페이지에서 **모든 scatter 차트가 Deck.gl 로 렌더**됩니다. 설정 안 하면 기본 ECharts 경로 사용.

### 기본 기능 동등

Action 탭 / 차트 토글 / cmd 색상 / Brush / Legend 동기화 / 차트 리사이즈 — 모두 ECharts 경로와 동일하게 동작합니다. UI 상 차이는 렌더 성능 뿐.

---

## 16. 트러블슈팅

### 업로드가 0% 에서 멈춰요 / "안전 모드" 라벨이 떠요

`/minio-upload/` 경로가 사용자 네트워크의 방화벽/프록시에서 차단된 경우입니다. 시스템이 자동으로 **Spring 경유 streaming (`/upload-stream`)** 으로 전환해서 끝까지 업로드를 완료해줍니다. 다음을 확인:

- "안전 모드 업로드 중" 라벨이 떠 있다면 **그대로 기다리세요** — 속도는 느리지만 정상적으로 완료됩니다 (1GB ~ 수 분 소요).
- 진행 % 가 전혀 안 올라가고 fail 이 뜨면 → 운영팀에 문의 (Spring 자체 또는 MinIO 단절 가능성)

운영팀 시점에서는 `portal/docs/nginx-trace-upload.md` 가이드 참고.

### "차트 데이터가 너무 커요"

Samples 값을 줄이거나 시간 범위를 Brush 로 좁혀서 재조회. 기본 256MB gRPC 응답 한도에 걸렸을 때.

### Job 이 PARSING 에서 멈춤

1. Rust trace 서비스 (`:50053`) 상태 확인
2. MinIO 접근 가능한지 확인
3. 마지막 수단 — **[재파싱]** 버튼

### UFS MCQ 환경에서 QD 가 예상보다 큼

현재 processor 는 단일 전역 카운터 (send +1 / complete -1). MCQ 에서 hwq_id 별로 QD 를 보려면 Rust `processors/ufs.rs` 수정 필요. 필요 시 운영 담당자에게 문의.

### Block trace 에서 LBA 가 18 quintillion

Flush (FF) 이벤트는 sector=u64::MAX(-1) 로 기록됩니다. 최신 파서는 이를 `sector=0` 으로 정규화합니다. 이전 parquet 이라면 **[재파싱]** 으로 갱신.

### Samples 값 보다 적게 반환되는 것 같아요

최신 stratified 다운샘플은 `n > targetPoints` 일 때 **정확히 targetPoints row** 를 반환합니다. 만약 그보다 적다면 `n ≤ targetPoints` (필터 후 데이터 자체가 적음) — 이 경우는 원본 그대로 패스스루 입니다. Brush 로 좁히면 그 구간 row 수만큼만 보여집니다.

---

## 17. 관련 문서

- **[Trace Analysis 아키텍처](/architecture/trace-analysis/)** — 시스템 설계, Arrow IPC wire format, parquet async streaming reader, Deck.gl 렌더러 내부 구조
- **[Agent 아키텍처](/architecture/agent/)** — Agent 페이지에서 실행되는 I/O trace 수집 파이프라인 (trace 수집 → 업로드 → 본 페이지에서 분석)
- **[MinIO 스토리지](/architecture/minio/)** — MinIO 일반 사용 (Spring Boot SDK / 버킷 관리 / 파일 업로드). trace 의 `trace-uploads` / `trace-parquet` 버킷 구조는 [Trace Analysis 아키텍처](/architecture/trace-analysis/) 참조
