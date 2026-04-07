---
title: 변경 이력
description: Samsung Portal의 주요 변경 사항 및 기능 추가 이력
---

## 2026-04-07

### T32 Dump UX 종합 개선

**실행 전 확인 다이얼로그**: "Dump 시작" 클릭 시 ConfirmDialog에서 슬롯, FW 폴더(Windows 경로), JTAG/T32 PC 정보를 확인 후 실행. 잘못된 브랜치 선택 시 5분 낭비 방지.

**Stepper 경과 시간**: 각 Step 옆에 타이머 아이콘 + 경과 시간 표시 (`3s`, `1:47` 등). Step 3(최대 5분)의 진행 상황 가늠 가능.

**브랜치 선택 리스트 개선**:
- Bitbucket 브랜치 드롭다운으로 교체 (Browse 파일 탐색기 → 직접 찾기 fallback으로 이동)
- DOWNLOADED 브랜치: 클릭 즉시 선택, hover 시 초록 배경
- DETECTED 브랜치: 인라인 "다운로드" 버튼, SSE 진행률, 완료 후 자동 선택
- 브랜치 검색: 실시간 필터링
- 긴 브랜치명 자동 줄바꿈, filePath는 Windows 경로로 변환 표시

**Step별 실패 힌트**: 실패 시 Step 번호 + 구체적 해결 안내 (JTAG 케이블/Hang 명령어/fail 키워드 등)

**Step 3 fail 키워드 감지**: exitCode=0이어도 stdout에 "fail"(대소문자 무시) 포함 시 실패 처리.

**전체 다운로드 버튼**: Dump 완료 시 결과 폴더 ZIP 다운로드 동작 연결.

### T32 Dump result path 형식 변경

결과 폴더명을 `{date}_{setLocation}_{testToolName}_{testTrName}` 형식으로 변경. 예: `20260407_T10-1_randwrite_Savona_V8_TLC_512Gb_512GB_P00RC28`. DumpRequest에 `setLocation`, `testToolName`, `testTrName` 파라미터 추가.

### T32 Admin 명령어 필드 textarea 변경

JTAG Command, T32 Port Check Command, Dump Command 입력 필드를 `input` → `textarea`로 변경. 긴 명령어 전체 확인 가능, 세로 리사이즈 지원.

### ConfirmDialog children snippet 지원

ConfirmDialog에 `children` snippet prop 추가. title/description 외에 커스텀 콘텐츠를 삽입할 수 있음 (T32 Dump 실행 확인에서 활용).

### Bitbucket Admin targetPath 표시 개선

- 테이블: truncate + tooltip (TruncateCell)
- Add/Edit 다이얼로그: `col-span-2`로 전체 너비 표시

### Bitbucket 파일 삭제 Samba 마운트 대응

Java `Files.walk` + `Files.delete` → OS `rm -rf` (ProcessBuilder)로 교체. Samba 마운트에서 `directory not empty` 오류 방지. 3회 리트라이 (2초 간격).

### Bitbucket 브랜치 커밋 날짜 표시

브랜치 이력에 Bitbucket 커밋 날짜(`commitDate`)를 표시합니다. Bitbucket API의 `authorTimestamp`를 DB에 저장하며, metadata에 없으면 `/commits?until={branchId}&limit=1`로 fallback 조회합니다.

### Bitbucket 다운로드 플로팅 카드

DETECTED 브랜치의 개별 다운로드 시 우하단에 플로팅 카드로 실시간 진행률을 표시합니다. 여러 브랜치 동시 다운로드를 지원하며, 삭제 시 `downloadedAt`을 초기화합니다.

### Bitbucket SSE 다운로드 + 파일/DB 분리 삭제

- `POST /branches/{branchId}/download` (SSE): 진행률 스트리밍 (1MB마다 이벤트)
- `POST /branches/{branchId}/delete-files`: 파일만 삭제 (DB 유지, 상태→DETECTED)
- `DELETE /branches/{branchId}`: DB 기록 삭제

### Bitbucket autoDownload 토글

저장소별 `autoDownload` 옵션 추가. OFF 시 새 브랜치를 `DETECTED` 상태로만 기록하고 자동 다운로드하지 않습니다. 디스크 용량 관리 시 유용합니다.

### Bitbucket 저장 전 연결 테스트

`POST /test-connection` 엔드포인트 추가. 저장소를 DB에 저장하기 전에 연결을 테스트하고 브랜치 목록 + 커밋 ID + 타임스탬프를 미리 확인합니다.

---

## 2026-04-05

### T32 FW Code 경로 매핑

T32Config에 4개 경로 매핑 필드 추가:
- `fwCodeLinuxBase` / `fwCodeWindowsBase`: FW 코드 Linux↔Windows 경로 변환
- `resultBasePath` / `resultWindowsBasePath`: 결과 저장 경로 Linux↔Windows 변환

Dump 명령어에 `{result_path}`, `{branch_path}` 플레이스홀더가 추가되어 자동으로 Windows 경로로 변환됩니다.

### T32 Dump 브랜치 폴더 선택

Dump Dialog에서 Bitbucket 다운로드된 브랜치 폴더를 선택할 수 있습니다. 선택한 폴더 경로가 `{branch_path}`로 dump 명령어에 전달됩니다.

### T32 결과 파일 미리보기 + 다운로드

Dump 완료 후 결과 파일 목록을 미리 보고 개별 다운로드할 수 있습니다.

---

## 2026-04-04

### 성능 TC/History DataTable Excel Export

성능/호환성 TC 테이블과 History DataTable에 Excel 내보내기 버튼 추가. FW Name, TC Name, Parser Name 등 추가 필드 포함. 시트 이름에 Excel 예약어(History) 충돌 방지 처리.

### perf-content 개별 Excel 제거 + testdb/ufsinfo 전체 Excel Export

perf-content 컴포넌트의 개별 Excel 내보내기를 제거하고, testdb/ufsinfo 단위의 전체 Excel export로 통합했습니다.

---

## 2026-04-03

### 성능 결과 재파싱 (Reparse) 기능

완료된 성능 테스트의 원본 로그에서 결과 JSON을 다시 생성하는 기능입니다. 파싱 오류 발생 시나 파서 업데이트 후 재파싱이 필요할 때 사용합니다.

**백엔드:**
- `PerformanceReparseService`: SSH로 원격 tentacle 서버 접속, `parsingcontroller` 백그라운드 실행 (ExecutorService 4스레드)
- NAS(UFS) 경로: 압축 해제 → 파싱 → 압축/JSON 외 파일 삭제
- 일반 경로: `*_testcase.log` → LOGFILE 추출 → 파일 존재 검증 → JSON 삭제 → 파싱
- `parsingMethod`는 DB의 parser name 사용 (TC → parserId → parser.name)
- `latencyType` 비트마스크: ioType의 R→bit0, W→bit1, U→bit2
- `ReparseController`: REST API + SSE 실시간 진행 스트리밍 (1초 push)

**프론트엔드:**
- `reparseStore`: 글로벌 Svelte 5 store (SSE + localStorage 영속)
- `ReparseFloatingCard`: 모든 페이지 우하단 플로팅 진행 카드 (파일명, 진행률, 경과시간)
- History 상세, TR 상세, Slots 페이지 3곳에 Reparse 버튼 배치
- 브라우저 닫아도 서버에서 계속 진행, 재접속 시 자동 복원

### headType 시스템

HEAD 연결의 타입 구분을 이름 기반에서 정수 타입 기반으로 변경했습니다.

- 기존: `name` 문자열에서 용도를 유추
- 변경: `headType` 정수 필드로 명시적 구분 (0=호환성 테스트, 1=성능 테스트)
- `portal_head_connections` 테이블에 `headType` 컬럼 추가
- 프론트엔드 Slots 페이지가 headType 기반으로 탭 필터링

### SessionLockManager (VM 배타적 접근)

원격 VM 접속 시 세션 잠금을 통해 동시 접근을 방지합니다.

- `SessionLockManager`: 서버별 세션 잠금 관리 (사용자 ID + 타임스탬프)
- 이미 다른 사용자가 접속 중인 VM은 잠금 상태로 표시
- 잠금 자동 해제: 접속 종료 또는 타임아웃 시 자동 릴리스
- Remote 페이지에서 잠금 상태 시각적 표시 (잠금 아이콘 + 사용자 정보)

### Slot 페이지 성능 비교 기능

슬롯의 Assigned TC 테이블에 VS 버튼(`CompareOpenCell`) 추가. 클릭 시 CompareSheet가 직접 열리며, 내장 picker로 다른 FW의 동일 TC 결과를 추가하여 비교할 수 있습니다.

- `CompareOpenCell`: TC 행에 VS 아이콘 버튼 렌더링
- CompareSheet: 슬롯 페이지 내에서 Sheet 형태로 열림 (페이지 이동 불필요)
- 내장 Picker로 TR 선택 → 동일 TC History 목록 → 비교 대상 추가

---

## 2026-03-20

### 토스 UX 철학 기반 종합 UI 개선

토스 UX 6대 원칙에 맞춰 전반적인 인터랙션 품질을 개선했습니다.

**마이크로 인터랙션:**
- **버튼 press 피드백**: `active:scale-[0.97]` 눌림 효과 추가 (모든 Button 컴포넌트)
- **페이지 전환 모션**: View Transitions API 활용 150ms fade 전환. 미지원 브라우저는 기존처럼 즉시 전환
- **네비 탭 터치 타겟 확대**: `text-[11px] px-2 py-1` → `text-xs px-2.5 py-1.5`, 아이콘 `size-2.5` → `size-3`

**ConfirmDialog 컴포넌트:**
- 네이티브 `confirm()` 전면 교체. 커스텀 다이얼로그로 통일 (경고 아이콘, 실행 중 스피너)
- Admin 탭 삭제, MakeSet 그룹 삭제, Memo 미저장 변경사항 경고 등 6곳 적용

**토스트 알림 통합:**
- 앱 전체 `alert()` → `toast.success()`/`toast.error()` 교체 (0개 alert 잔존)
- Compatibility, Performance, UFS Info, Slot Info, Admin 탭, MakeSet, DLM 등 모든 CRUD 작업에 성공/실패 토스트 추가
- 토스트 위치: `top-right`

**스켈레톤 로딩:**
- `TableSkeleton` 재사용 컴포넌트 신규 생성 (columns, rows props)
- 10개 페이지의 dsy-loading 스피너를 스켈레톤으로 교체: ufsinfo, slot-infomations, sets, compatibility, performance, performance/[trId], performance/compare, performance/history/[hisId], remote
- Dashboard는 기존 Skeleton 유지

**인라인 밸리데이션:**
- TR/TC 폼에 `submitted` 상태 기반 필수 필드 에러 표시 (빨간 테두리 + "필수 항목입니다" 메시지)
- Compatibility TR: Product, Controller / Compatibility TC: Name
- Performance TR: Controller / Performance TC: Name
- UFS Info: Name
- Save 버튼의 disabled 조건을 제거하고 Save 클릭 시 밸리데이션+시각 피드백으로 전환

**비동기 버튼 로딩 상태:**
- Admin 탭(Debug, Sets, UfsInfo, Slots)의 Save 버튼에 `saving` 상태 + Loader 스피너 추가

**Empty State 개선:**
- DataTable, DataTableShell의 "No results." → SearchX 아이콘 + "검색 결과가 없습니다" + 안내 문구

**TR 수정 제한:**
- History가 연결된 TR은 수정 불가 (Compatibility, Performance 양쪽). 409 응답 + toast.error 표시

### UI/UX 기본 개선

- **svelte-sonner 통합 토스트**: `<Toaster>` 컴포넌트를 전역 레이아웃에 추가하여 모든 페이지에서 일관된 알림 UX 제공
- **AppSidebar 데드코드 정리**: 미사용 `AppSidebar.svelte` 및 `ui/sidebar/` 디렉토리(26개 파일) 삭제
- **아이콘 버튼 aria-label 추가**: DataTableShell, DataTablePagination의 페이지네이션 버튼에 접근성 라벨 추가
- **차트 테마 반응성 개선**: `renderChartToImage()`가 현재 다크모드 상태를 감지하여 적절한 테마와 배경색 자동 적용

### PerfChart 로딩 인디케이터

PerfChart에 ECharts `finished` 이벤트 기반 로딩 오버레이 추가. 차트 렌더링 완료 전까지 반투명 배경 + 스피너를 표시하여 대용량 데이터의 progressive 렌더링 중에도 로딩 상태를 명확히 전달. 15개 perf-content 컴포넌트에 자동 적용.

### GenPerf Min/Max 마커 개선

- Min/Max 마커의 `symbolSize`를 28 → 40, `fontSize`를 8 → 10으로 확대
- `formatter` 추가로 마커에 이름과 값을 줄바꿈으로 함께 표시
- 기본값을 비활성으로 변경 (툴바 "Min/Max" 버튼으로 토글)

---

## 2026-03-18

### DB 테이블 관리를 Admin 페이지로 이동

Sets, Slot Info, UFS Info, Perf Generator를 메인 메뉴에서 제거하고 Admin 대시보드 내 탭으로 통합했습니다. Admin 로그인 시에만 접근 가능합니다.

- **Sets 탭**: SetInfomation CRUD (인라인 편집)
- **Slots 탭**: SlotInfomation 조회/수정
- **UFS Info 탭**: 7개 UFS 코드 테이블 CRUD (서브탭)
- **Perf Gen 탭**: 성능 차트 컴포넌트 코드 생성기

백엔드 변경:
- `AdminDbTableController` 추가 (`/api/admin/db/sets`, `/api/admin/db/slots`, `/api/admin/db/ufsinfo/{table}`)
- `AdminMenuService` DEFAULT_MENUS에서 제거된 메뉴 항목 동기화

프론트엔드 변경:
- `PerfGenerator.svelte`를 `$lib/components/`로 분리 (admin, devtools 양쪽에서 공유)
- `AdminSetsTab`, `AdminSlotsTab`, `AdminUfsInfoTab` 컴포넌트 추가
- 메인 메뉴(`+layout.svelte`)에서 4개 항목 제거

---

## 2026-03-16

### Admin Slot Override

Admin 대시보드에 **Slot Override** 탭 추가. HeadSlotData의 `testState`, `connection`, `product` 등 필드를 관리자가 직접 덮어쓸 수 있습니다. 오버라이드 적용 시 슬롯이 Lock 상태가 되어 이후 Head TCP 업데이트가 해당 슬롯에 반영되지 않으며, Restore로 원래 값으로 복원합니다.

- `GET /api/admin/slot-overrides` — 현재 오버라이드 목록
- `PUT /api/admin/slot-override` — 오버라이드 적용 및 Lock
- `DELETE /api/admin/slot-override/{source}/{slotIndex}` — 오버라이드 삭제 및 복원

### HeadSlotData product 계산 필드

`HeadSlotData.getProduct()` 신규 추가. `Controller_NandType_CellType_NandSize_Density` 형식으로 조합된 계산 필드로, 슬롯 카드에 product 정보로 표시됩니다.

### 슬롯 카드 툴팁

슬롯 카드에 마우스를 올리면 전체 슬롯 상세 정보(modelName, battery, testState, setLocation, trName, runningState 등)를 보여주는 툴팁이 표시됩니다.

### 파일 업로드 한도 및 압축 필수 정책

- 파일 업로드 한도를 1GB에서 **1TB**로 상향 (`max-file-size`, `max-request-size`)
- **2GB 초과 파일은 압축 형식(`.zip`, `.gz`, `.tar`, `.7z`, `.rar` 등)만 업로드 허용** — 비압축 파일이 2GB를 초과하면 서버에서 거부

### HEAD 이미지 업로드 명령어 변경

HEAD 명령어 `imageupload` → **`makeset`** 으로 변경. 이미지 업로드 및 셋 구성 명령으로 통합.

---

## 2026-03-14

### 호환성/성능 히스토리 상세 검색 기능

JPA Specification 기반 동적 검색 API와 프론트엔드 접이식 상세 검색 패널 구현. Result, Slot, FW, TC, 날짜 범위 등 다양한 조건 조합 검색 지원. 호환성은 Set Model, Test Type 필터 추가.

### 대용량 차트 성능 최적화 및 글로벌 에러 처리

PerfChart에 대용량 데이터 자동 최적화 주입 (10K+ large mode, 50K+ LTTB 다운샘플링). `GlobalExceptionHandler`(`@RestControllerAdvice`) 추가로 컨트롤러별 try-catch 정리.

### admin, auth 패키지 구조 분리

admin, auth 패키지를 `controller/entity/repository/service` 하위 패키지로 분리.

### 슬롯 상태 색상/아이콘 중앙 집중화

SlotCard, ResultCell, 대시보드에 분산되어 있던 상태별 색상/아이콘 매핑을 `$lib/config/slotState.ts` 한 곳에서 관리하도록 통합. `WARNING_PASS` 상태를 warning(amber) 스타일로 변경. 새 상태 추가 시 `EXACT_STATE_COLORS`에 한 줄만 추가하면 전체 반영.

---

## 2026-03-13

### Guacamole 터미널 클립보드 지원

원격 터미널과 로컬 클립보드 간 복사/붙여넣기 지원. 원격→로컬 자동 동기화, 로컬→원격 paste/focus 이벤트 전송. RDP `normalize-clipboard=windows` 추가.

### Slots 페이지 Log Browser 연동

슬롯 카드 우클릭 메뉴 및 Selection Sheet에 Log Browser 추가. 슬롯 위치 기반 자동 경로 추출.

### Slots 페이지 DB 의존 제거

슬롯 정보를 Head SSE 메시지에서만 가져오도록 변경. DB polling 제거로 DB/실제 상태 불일치 문제 해결.

### Admin 메뉴 권한 수정

admin 사용자는 메뉴 visibility와 무관하게 모든 메뉴 표시. `/admin` 경로를 visibility 필터링에서 분리.

### 버그 수정

- 로그 검색 결과 500개 제한 제거
- `logPath` 디렉토리 경로 처리, LogBrowseCell logPath 기반 표시
- TC 카테고리 구분, Set TR/TC 시트 전체 화면, SetTR 목록 20개 제한 수정
- SlotCard 글자 크기 확대, DataTable OOM 해결

---

## 2026-03-12

### MinIO 다운로드 진행률 표시

`fetch` + `ReadableStream` 기반 다운로드 프로그레스 바, 취소 버튼, 다중 파일 큐 지원.

### Slots 페이지 버그 수정

`testTrName` 빈 값 NONE 처리, Running TC JSON 파일 경로 수정, JSON 파싱 에러 메시지 개선.

### Head 연결 관리를 DB로 이관

기존 `application.yaml`의 `head.connections` 하드코딩을 DB `portal_head_connections` 테이블로 이관했습니다. Admin 페이지에서 동적으로 연결 추가/수정/삭제/활성화 토글이 가능합니다.

- `HeadConnection` 엔티티 신규 (name, host, portSuffix, listenPortSuffix, enabled, testMode)
- `HeadConnectionManager` DB 기반 초기화, testMode 지원
- `HeadTcpClient` stop/reconnect 시 exit 메시지 강제 전송 (race condition 방지)
- Admin Head 탭 추가 (연결 CRUD, enable/disable 토글, testMode 체크박스)
- Slots 페이지 하드코딩 탭을 DB 기반 동적 탭으로 변경

### MinIO Bucket 가시성 관리

Admin이 버킷별 visible을 토글하여 일반 유저에게 노출할 버킷을 제어합니다.

- `BucketVisibility` 엔티티 + `portal_bucket_visibility` 테이블
- `GET /buckets` 응답을 역할별 분리 (Admin: 전체, User: visible만)
- `PUT /buckets/{name}/visibility` 추가

---

## 2026-03-11

### Dashboard 성능 최적화

메인 대시보드 로딩 속도를 개선했습니다. 7개 API 병렬 호출 + 프론트 집계 방식에서, `GET /api/dashboard/stats` 단일 API + DB `GROUP BY` 집계 쿼리 방식으로 변경했습니다.

### 서버(VM) 관리를 DB로 이관

기존 `application.yaml`의 `guacamole.vms` 하드코딩을 DB `portal_servers` 테이블로 이관했습니다. 서버 추가/변경 시 재시작 없이 웹에서 동적 관리 가능합니다.

- `PortalServer` 엔티티 (name, ip, username, password, sshPort, rdpPort, connectionType, visible)
- Admin Servers 탭 추가
- `connectionType`: 0=없음, 1=SSH, 2=RDP, 3=SSH+RDP

---

## 2026-03-10

### DB 스키마 SQL 파일 정비

3개 DB의 전체 테이블 생성 SQL을 `sql/` 디렉토리에 정리했습니다.

- `sql/schema-testdb.sql` (9개 테이블)
- `sql/schema-portal.sql` (4개 테이블 + 기본 admin 계정)
- `sql/schema-ufsinfo.sql` (7개 룩업 테이블)

### 사용자 관리 및 권한 체계

`portal_users` 테이블 추가. 관리자 ID/PW 관리, BCrypt 암호화, 세션 기반 인증, ADMIN/USER 역할 구분.

### Admin Dashboard UI 리디자인

Dashboard 페이지와 동일한 디자인 언어로 Admin UI 전면 개편. Health, Connections, Cache, App Info, Config, Menus 6개 탭.

### Admin Dashboard 신규 구현

관리자 대시보드 페이지(`/admin`). 11개 REST 엔드포인트, 9개 서비스 병렬 헬스체크, 16개 Redis 캐시 관리, JVM 메모리/GC/스레드 정보, 메뉴 가시성 관리.

### CompatibilityTestCase 엔티티 수정

`nullable` 속성을 DB 스키마와 일치하도록 수정. `TEST_TYPE` 컬럼 추가.

### 호환성 TR/TC 드롭다운 추가

- TR: Test Type 셀렉트 (Aging, function, POR-TC, NPO-TC, SPOR-OCTO, BootingRepeat)
- TC: TC Type (APK, Binary, Dummy), Belong To (OFK, STF, ETC)

### LogViewer 바이너리 파일 강제 열기

`force` 파라미터 추가. NUL 바이트 제거 후 텍스트로 반환.

### LogViewer 검색 결과 Ctrl+A 전체 복사

검색 결과가 있을 때 Ctrl+A로 모든 매칭 라인을 클립보드에 복사.

### LogBrowser non-UTF-8 인코딩 지원

SSH: `file --mime-encoding` 감지 + `iconv` 변환. Local: `Charset.forName()` 사용.

---

## 2026-03-09

### Parser Registry

parserId와 시각화 컴포넌트 매핑을 `parserRegistry.ts` 한 곳에서 관리하도록 리팩터링. 새 파서 추가 시 `register()` 한 줄만 추가하면 모든 페이지에 자동 반영.

### Performance Compare parserId 매핑 수정

`CompareSideBySide`와 History 상세 페이지의 parserId 매핑을 실제 DB 값에 맞게 전면 수정.

### MinIO 체크박스 선택 + 일괄 다운로드/삭제

호버 기반 다운로드를 체크박스 선택으로 교체. 전체 선택/해제, 액션 바 (Download/Delete/Clear).

### BinMapper BOOL8/BOOL32 타입 및 ASCII 힌트

`BOOL32` (4바이트) 타입 추가. 정수 필드의 각 바이트가 인쇄 가능한 ASCII면 자동 표시.

### BinMapper Predefined Struct UI

StructInput에 CRUD UI 추가. 구문 하이라이팅 코드 에디터, 헤더 파일(.h) 열기 지원.

---

## 2026-03-07

### BinMapper Hex View 개편

`HexStructView`를 ImHex 스타일 인터랙티브 그리드로 전면 개편. 필드별 색상 하이라이트, 양방향 호버 동기화.

### BinMapper 테스트 케이스

`CppStructLexerTest`, `CppStructParserTest`, `CppTypeTest`, `BinaryReaderServiceTest` 추가.

### Performance Compare 추가 비교

Compare 페이지에서 다른 TR의 성능 결과를 추가 비교하는 기능.

---

## 2026-03-06

### TC Group 기능

자주 사용하는 TC 조합을 그룹으로 저장/관리. Slots 페이지 SetTC Sheet에 그룹 Chip UI, 그룹 클릭으로 TC 일괄 선택/해제.

---

## 2026-03-05

### MinIO S3 스토리지 브라우저

MinIO를 S3 호환 오브젝트 스토리지로 활용하는 파일 관리 기능. 버킷 CRUD, 파일 업로드(프로그레스 바, 취소), 다중 파일 배치 업로드.

### OAuth2/OIDC 인증

Spring Security + OAuth2 Client로 Samsung AD (ADFS) 인증. `AUTH_DISABLED = true` 상태. CSRF 보호 (`XSRF-TOKEN`).

### BinMapper (Binary Struct Mapper)

C/C++ 구조체 정의와 바이너리 파일 매핑 도구. Lexer, Parser, BinaryReader. 12개 기본 타입 + 90개 alias. Table/Hex+Struct/JSON 3가지 뷰.

### Excel Export (gRPC)

Go Excel Service와 gRPC 통신으로 네이티브 Excel 차트 포함 `.xlsx` 내보내기.

---

## 2026-03-04

### Performance History 비교 기능

성능 테스트 결과 2개 이상을 Baseline 대비 비교. 3가지 뷰: Chart Overlay, Side-by-Side, Delta Table. Chip 방식 선택, 서버사이드 페이지네이션에서 페이지 전환 시에도 선택 유지.

---

## 2026-02-25

### Log Viewer

SSH 원격 서버 로그 파일 조회/검색. 청크 로딩, "Last" 버튼, `rg` 패턴 검색, 바이너리 감지, 다크 터미널 UI.

### GenPerf 컴포넌트

`ReadWriteViewer`를 범용 성능 데이터 시각화 컴포넌트로 교체. Read/Write/FlushTime 탭, Line/Scatter 전환, Y축 단위 자동 판별.

### DataTable Excel-like 셀 선택

`enableCellCopy` prop. 마우스 드래그 셀 선택, `Ctrl+A` 전체 선택, `Ctrl+C` TSV 복사.

### Performance History 상세 페이지

`/testdb/performance/history/[hisId]` 단독 상세 페이지. GenPerf 차트 전체 화면, History 탭에서 행 더블클릭 진입.

### Slots 페이지 기능 강화

RUNNING 상태 TC 행 확장 시 슬롯 로그에서 실시간 성능 데이터 표시. 완료된 TC 행 확장 시 GenPerf 결과 차트 표시.
