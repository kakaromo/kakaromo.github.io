---
title: 프론트엔드 아키텍처
description: SvelteKit 5 기반 SPA 프론트엔드의 기술 스택, 페이지 구성, 데이터 테이블, 성능 시각화, 빌드 및 배포를 설명합니다.
---

## 기술 스택

| 기술 | 버전 | 용도 |
|------|------|------|
| SvelteKit | 5 | 프레임워크 (SPA 모드) |
| TypeScript | - | 타입 안전성 |
| TailwindCSS | v4 | 유틸리티 CSS |
| DaisyUI | v5 | UI 테마 (라이트/다크) |
| bits-ui (shadcn-svelte) | - | UI 컴포넌트 |
| TanStack Table | v8 | 데이터 테이블 |
| ECharts | - | 성능 차트 |
| ExcelJS | - | Excel Export (dynamic import) |
| Lucide | - | 아이콘 |
| xterm.js | - | SSH 터미널 (XtermClient) |
| guacamole-common-js | - | RDP 원격 접속 (GuacamoleClient) |

:::tip
Svelte 5 Runes(`$state`, `$derived`, `$effect`)를 사용하여 명시적 반응성으로 복잡한 상태 관리를 단순화합니다. SPA 모드로 빌드하여 SSR은 사용하지 않습니다.
:::

## 디렉토리 구조

```
frontend/
├── src/
│   ├── lib/
│   │   ├── api/                        # API 클라이언트 계층
│   │   │   ├── client.ts              # 기본 fetch 래퍼 (XSRF, 401 처리)
│   │   │   ├── testdb.ts             # TestDB CRUD + Head 명령 + TC Group
│   │   │   ├── ufsinfo.ts            # UFSInfo 조회
│   │   │   ├── guacamole.ts          # Guacamole API
│   │   │   ├── minio.ts              # MinIO API (XHR 업로드 포함)
│   │   │   ├── binMapper.ts          # BinMapper API
│   │   │   └── types.ts              # 공유 TypeScript 인터페이스
│   │   ├── stores/
│   │   │   ├── headSlotStore.svelte.ts # Head SSE 스토어 (Svelte 5 runes)
│   │   │   └── auth.svelte.ts         # 인증 상태 스토어
│   │   ├── config/
│   │   │   └── slotState.ts           # 슬롯 상태 색상/아이콘 중앙 관리
│   │   ├── utils/
│   │   │   └── excel-export.ts        # Excel Export 유틸리티
│   │   └── components/
│   │       ├── ui/                    # shadcn-svelte 기본 UI 컴포넌트
│   │       ├── data-table/           # 데이터 테이블 시스템
│   │       ├── perf-chart/           # ECharts 래퍼
│   │       ├── perf-content/         # 성능 데이터 시각화 (15개)
│   │       ├── perf-compare/         # 성능 비교 뷰
│   │       ├── bin-mapper/           # BinMapper 뷰
│   │       └── PerfGenerator.svelte # 성능 코드 생성기 (공유 컴포넌트)
│   └── routes/                       # 페이지 라우트
└── package.json
```

## 페이지 구성

### `/` -- 대시보드

통계 차트를 표시하는 메인 페이지. `GET /api/dashboard/stats` 단일 API로 DB 집계 통계를 조회합니다.
- 호환성/성능 카운트 카드 (TR수, History수, Pass Rate)
- DonutChart: 결과 분포 (PASS/FAIL/OTHER)
- BarChart: FW별 통계 (상위 10개)
- 최근 10건 History
- 실시간 슬롯 요약 타일 (SSE)

### `/testdb/compatibility` -- 호환성 테스트

TanStack Table 기반 데이터 테이블로 TestRequest, TestCase, History를 탭으로 관리합니다.

### `/testdb/performance` -- 성능 테스트

두 개의 메인 탭:
- **Test Requests**: TR 목록. 더블클릭 시 TR 상세 페이지로 이동
- **History**: 전체 History 목록 (서버사이드 페이지네이션). CompareToggleCell로 비교 목록 추가 가능

### `/testdb/performance/[trId]` -- TR 상세

특정 TR의 History를 TC 그룹별로 표시. 행 확장 시 GenPerf 차트 렌더링.

### `/testdb/performance/compare?ids=42,58` -- History 비교

3가지 뷰 모드: Chart Overlay, Side-by-Side, Delta Table

### `/testdb/performance/history/[hisId]` -- History 상세

단일 History 결과를 전체 화면으로 표시. parserId별 적절한 컴포넌트 + 로그 브라우저.

### `/testdb/slots` -- 실시간 슬롯 모니터링

Head 서버에서 SSE로 수신한 슬롯 상태를 카드 UI로 표시합니다. 카드 선택(클릭, Ctrl+클릭, Shift+클릭, 드래그), Context Menu, TC Group Chip 등을 지원합니다.

### `/remote` -- 원격 접속

xterm.js SSH 터미널 기반 원격 접속. 다중 터미널 탭, Broadcast 명령어, 브라우저 네이티브 복사/붙여넣기 지원.

### `/storage` -- MinIO 파일 브라우저

S3 호환 스토리지의 파일 관리. 좌측 버킷 목록, 우측 파일/폴더 탐색, 드래그앤드롭 업로드 + 프로그레스 바.

### `/devtools/bin-mapper` -- Binary Struct Mapper

C/C++ 구조체 정의로 바이너리 데이터를 매핑. 3가지 뷰: Table(트리), Hex+Struct(색상 동기화), JSON.

### `/admin` -- 관리자 대시보드

Admin 로그인 시에만 접근 가능. 시스템 Health, Connections, Cache, App Info, Config, Menus, Users, Servers, Head Connections, VM Status, Sets, Slots, UFS Info, Perf Gen 탭으로 구성.

:::note
Sets, Slot Info, UFS Info, Perf Generator는 메인 메뉴에서 제거되고 Admin 페이지 내 탭으로 통합되었습니다.
:::

---

## API 클라이언트 계층

### client.ts -- 기본 fetch 래퍼

모든 API 호출의 기반. XSRF 토큰 자동 추가, 401 자동 리다이렉트를 처리합니다.

```typescript
async function request<T>(path: string, init?: RequestInit): Promise<T>
```

### headSlotStore.svelte.ts -- SSE 스토어

Svelte 5 `$state` 기반. `EventSource`로 Head SSE 스트림을 구독하여 실시간 슬롯 상태를 관리합니다.

```typescript
const compatStore = createHeadSlotStore('compatibility');
const perfStore = createHeadSlotStore('performance');
```

### auth.svelte.ts -- 인증 스토어

인증 상태, 로그인/로그아웃 관리. `getCsrfToken()` 함수를 export합니다.

---

## 데이터 테이블 시스템

`DataTable.svelte`는 TanStack Table v8 기반의 재사용 가능한 테이블 컴포넌트입니다.

**주요 기능:**
- 정렬 / 필터링 / 페이지네이션 (클라이언트 & 서버사이드)
- 컬럼 표시/숨김 토글
- 행 선택 (단일/다중) 및 행 그룹핑
- 행 확장 (아코디언), 행 드래그 재정렬
- 행 더블클릭 네비게이션
- Excel-like 셀 범위 선택/복사 (`enableCellCopy`)
- 고정 높이 스크롤 + sticky 헤더 (`scrollHeight`)

### 커스텀 셀 렌더러

| 컴포넌트 | 용도 |
|----------|------|
| `BookmarkCell` | 북마크 토글 아이콘 |
| `CompareToggleCell` | 비교 목록 +/check 토글 |
| `DateCell` | 날짜 포맷팅 (ko-KR) |
| `LogBrowseCell` | 로그 브라우저 열기 버튼 |
| `ResultCell` | 테스트 결과 색상 뱃지 (16가지) |
| `SelectCell` | 행 선택 체크박스 |
| `StatusCell` | 상태 dot + text |

---

## 성능 시각화 컴포넌트

### parserId -> 컴포넌트 매핑

| parserId | 컴포넌트 | 차트 타입 |
|----------|----------|-----------|
| 2, 3, 16 | GenPerf | Line/Scatter + 멀티탭 (Read/Write/FlushTime) |
| 17 | KernelLatency | 테이블 전용 (Stats + Distribution) |
| 27 | VluLatency | 차트 + 복합 테이블 (3시트) |
| 28 | LongTermTC | 복수 차트 스택 (Write + Read) |
| 29 | WearLeveling | 단일 차트 + 테이블 |
| 30 | DirtyCase4Write | 단일 차트 + 테이블 |
| 31 | FragmentWrite | 단일 차트 + 테이블 |
| 32 | PerfByChunk | 멀티시트 (Write/Read) |
| 33 | VluDirtyCase4 | 멀티탭 |
| 34 | VluRandReadPerThread | 단일 차트 + 테이블 |
| 35 | UnmapThroughput | 테이블 전용 |
| 36 | IntervaledReadLatency | 단일 차트 + 테이블 |
| 37 | WideRandRead | 단일 차트 + 테이블 |
| 38 | WbFlushThroughput | 단일 차트 + 테이블 |
| 39 | WriteAndDelete | 단일 차트 + 테이블 |

모든 컴포넌트는 `exportExcel()` 함수를 제공하여 차트 이미지 + 데이터를 Excel로 내보냅니다.

### 공통 유틸리티

| 파일 | 용도 |
|------|------|
| `perfStyles.ts` | 버튼 스타일 상수 (`btnBase`, `btnActive`, `btnInactive`, `groupClass`) |
| `perfChartUtils.ts` | `baseChartOption()` 차트 공통 설정, `captureChartImage()` 이미지 캡처 |

---

## 빌드 및 배포

### 개발 모드

```bash
cd frontend
npm run dev
```

### 프로덕션 빌드

```bash
cd frontend
npm run build
```

빌드 결과물은 Maven 빌드 시 `src/main/resources/static/`에 복사되어 Spring Boot에서 서빙됩니다.

### SPA 라우팅

`SpaForwardingController`가 API, 정적 리소스 이외의 요청을 `index.html`로 포워딩하여 SvelteKit 클라이언트 사이드 라우팅을 지원합니다.

### 번들 최적화

- **ExcelJS**: `dynamic import()`로 사용자가 Excel 버튼 클릭 시에만 로드
- **ECharts**: 오프스크린 렌더링 시에도 dynamic import
- **프론트엔드 빌드 스킵**: `./mvnw ... -Dskip.frontend=true`
