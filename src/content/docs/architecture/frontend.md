---
title: 프론트엔드 아키텍처
description: SvelteKit 5 기반 SPA 프론트엔드의 기술 스택, 페이지 구성, 글로벌 스토어, SSE 패턴, 데이터 테이블, 성능 시각화, 빌드 및 배포를 설명합니다.
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
| svelte-sonner | - | 토스트 알림 |
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
│   │   │   ├── auth.svelte.ts         # 인증 상태 스토어
│   │   │   ├── tentacle.svelte.ts     # Tentacle 경로 프리픽스 스토어
│   │   │   ├── menu.svelte.ts         # 메뉴 가시성 스토어
│   │   │   ├── reparse.svelte.ts      # Reparse Job SSE 스토어
│   │   │   └── headSlotStore.svelte.ts # Head SSE 스토어
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
│   │       ├── PerfGenerator.svelte  # 성능 코드 생성기
│   │       ├── PerfGenerator.types.ts # 공유 타입 (FieldNode, TabInfo 등)
│   │       ├── PerfPreview.svelte    # 실시간 차트/테이블 미리보기
│   │       ├── ReparseFloatingCard.svelte # Reparse 진행률 플로팅 카드
│   │       └── JsonTreeView.svelte   # JSON 트리 뷰 (읽기 전용)
│   └── routes/                       # 페이지 라우트
│       └── +layout.svelte            # 글로벌 레이아웃 (네비게이션 + 스토어 초기화)
└── package.json
```

## 글로벌 스토어 패턴

모든 글로벌 스토어는 Svelte 5 runes 기반의 **모듈 레벨 싱글톤** 패턴을 사용합니다. `.svelte.ts` 확장자 파일에서 모듈 스코프의 `$state`로 상태를 선언하고, getter로 읽기 전용 접근을 제공합니다.

### auth.svelte.ts -- 인증 상태

사용자 인증 정보와 로그인/로그아웃을 관리합니다.

```typescript
// 모듈 레벨 $state — 앱 전체에서 단일 인스턴스
let state = $state<AuthState>({
  authenticated: false, name: '', email: '', role: '', username: '', loading: true
});

export const auth = {
  get authenticated() { return state.authenticated; },
  get isAdmin() { return state.role === 'ADMIN'; },
  async fetchMe() { /* GET /api/auth/me → state 업데이트 */ },
  async login(username, password) { /* POST /api/auth/login */ },
  async logout() { /* POST /api/auth/logout → window.location.href = '/' */ },
  redirectToLogin() { window.location.href = '/oauth2/authorization/galaxy'; }
};

export { getCsrfToken };  // XSRF-TOKEN 쿠키에서 추출
```

**반응형 속성**: `authenticated`, `name`, `email`, `role`, `username`, `loading`, `isAdmin`

### tentacle.svelte.ts -- Tentacle 경로 프리픽스

SSH/SFTP에서 사용하는 Tentacle 서버 경로 프리픽스를 관리합니다.

```typescript
let prefix = $state('');
let headPrefix = $state('');
let loaded = $state(false);

export const tentacle = {
  get prefix() { return prefix; },       // 기본: '/home/octo/tentacle'
  get headPrefix() { return headPrefix; }, // 기본: '/home/octo/nas'
  get loaded() { return loaded; },
  async fetchPrefix() { /* 1회만 로드, loaded로 중복 방지 */ }
};
```

### menu.svelte.ts -- 메뉴 가시성

Admin이 설정한 메뉴 표시/숨김을 관리합니다. `+layout.svelte`에서 비활성 메뉴의 경로에 접근하면 첫 번째 visible 메뉴로 자동 리다이렉트합니다.

```typescript
export const menuStore = {
  get items() { return state.items; },
  get loaded() { return state.loaded; },
  async fetchMenus() { /* GET /api/admin/menus */ },
  isVisible(id: string): boolean {
    if (auth.isAdmin) return true;  // Admin은 항상 모든 메뉴 표시
    // 서버 설정이 없으면 기본 표시
    const item = state.items.find(m => m.id === id);
    return item ? item.visible : true;
  }
};
```

### reparse.svelte.ts -- Reparse Job (SSE)

성능 데이터 재파싱 Job의 진행 상태를 SSE로 추적합니다. localStorage 영속성과 SSE 자동 재연결을 결합한 가장 복잡한 스토어입니다.

```typescript
let jobs = $state<Map<string, ReparseJob>>(new Map());
let eventSource = $state<EventSource | null>(null);
let connected = $state(false);

export const reparseStore = {
  get jobs() { return jobs; },
  get connected() { return connected; },
  get activeJobs(): ReparseJob[] { /* preparing | running 필터 */ },
  get completedJobs(): ReparseJob[] { /* completed | failed 필터 */ },
  get hasActiveJobs(): boolean { /* 활성 job 존재 여부 */ },
  isReparsing(historyId: number): boolean { /* 특정 history 재파싱 중인지 */ },
  async startReparse(historyId) { /* API 호출 → SSE 연결 시작 */ },
  dismissJob(jobId) { /* 완료된 job 목록에서 제거 */ },
  init() { /* localStorage에서 활성 job 복원 → SSE 연결 */ },
  destroy() { /* SSE 연결 해제 */ }
};
```

## SSE / EventSource 패턴

### 연결 관리 (reparse 스토어 예시)

```
init() → localStorage 확인
   ├─ 활성 job 있음 → connect()
   │     ├─ EventSource 생성
   │     ├─ 'init' 이벤트 → 서버의 현재 job 목록으로 상태 동기화
   │     ├─ 'update' 이벤트 → job 상태 업데이트
   │     └─ onerror → disconnect() → 5초 후 재연결 (활성 job 있을 때만)
   └─ 활성 job 없음 → 대기
```

### 재연결 전략

- `onerror` 발생 시 기존 `EventSource`를 `close()` 후 정리
- localStorage에 활성 job ID가 남아있을 때만 5초 후 `setTimeout`으로 재연결
- 모든 job이 완료되면 재연결하지 않음

### 정리 (Cleanup)

```typescript
function disconnect() {
  if (eventSource) {
    eventSource.close();
    eventSource = null;
  }
  connected = false;
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
}
```

### localStorage 영속성

Reparse 스토어는 브라우저 새로고침/탭 전환 시에도 활성 Job을 추적합니다:

1. **저장**: job 상태 변경 시 활성 job의 ID 배열을 `localStorage.setItem('reparse-active-jobs', ...)`
2. **복원**: `init()`에서 `localStorage.getItem()`으로 활성 job ID를 읽고, 있으면 SSE 연결
3. **정리**: 모든 job이 완료/dismiss되면 `localStorage.removeItem()`

## ReparseFloatingCard 글로벌 마운트

`ReparseFloatingCard`는 `+layout.svelte`에서 글로벌로 마운트되어, 어떤 페이지에서든 Reparse 진행 상태를 보여줍니다.

```svelte
<!-- +layout.svelte -->
{#if isLoginPage}
  {@render children()}
{:else}
  <div class="min-h-screen bg-background">
    <header>...</header>
    <main>{@render children()}</main>
  </div>

  <!-- Global Reparse Floating Card -->
  <ReparseFloatingCard />
{/if}
```

- 로그인 페이지에서는 렌더링하지 않음
- 레이아웃 DOM 바깥(`</div>` 이후)에 배치하여 모든 페이지 콘텐츠 위에 플로팅
- `reparseStore.hasActiveJobs`가 true일 때만 카드 표시

## 앱 초기화 흐름 (+layout.svelte)

`+layout.svelte`의 `onMount`에서 4개의 글로벌 스토어를 초기화합니다:

```typescript
onMount(() => {
  auth.fetchMe();          // 인증 상태 확인
  tentacle.fetchPrefix();  // Tentacle 경로 로드
  menuStore.fetchMenus();  // 메뉴 가시성 로드
  reparseStore.init();     // Reparse SSE 복원
});
```

초기화 후 `$effect`에서 메뉴 가시성 기반 자동 리다이렉트를 수행합니다:

```typescript
$effect(() => {
  if (!menuStore.loaded || visibleMenuItems.length === 0) return;
  const currentPath = page.url.pathname;
  // 로그인, admin, ui-preview 페이지는 리다이렉트 제외
  if (currentPath === '/' || currentPath.startsWith('/admin') || ...) return;
  const isOnVisibleMenu = visibleMenuItems.some(item =>
    currentPath.startsWith(item.href)
  );
  if (!isOnVisibleMenu) goto(visibleMenuItems[0].href);
});
```

## Svelte 5 Runes 실전 패턴

### $state + SSE 실시간 업데이트

SSE 이벤트 핸들러에서 `$state` 변수를 직접 재할당하면 UI가 자동으로 반영됩니다. Map/Array는 새 인스턴스를 할당해야 반응성이 트리거됩니다.

```typescript
// reparse.svelte.ts
let jobs = $state<Map<string, ReparseJob>>(new Map());

function updateJobs(serverJobs: ReparseJob[]) {
  const newMap = new Map<string, ReparseJob>();
  for (const j of serverJobs) newMap.set(j.jobId, j);
  jobs = newMap;  // 새 Map 할당 → 반응성 트리거
}
```

### $derived로 계산된 값

`$derived`는 의존하는 `$state`가 변경될 때 자동 재계산됩니다. 스토어의 getter에서 필터링/변환에 활용합니다.

```typescript
// +layout.svelte
const visibleMenuItems = $derived(
  navMenuItems.filter(item => menuStore.isVisible(item.id))
);
const showAdminNav = $derived(auth.isAdmin);
let isLoginPage = $derived(page.url.pathname === '/');
```

### $effect로 사이드 이펙트

`$effect`는 의존하는 반응형 값이 변경될 때 실행됩니다. 네비게이션 리다이렉트, DOM 조작, 외부 API 호출 등에 사용합니다.

```typescript
// 메뉴 가시성에 따른 자동 리다이렉트
$effect(() => {
  if (!menuStore.loaded) return;  // 로드 전에는 실행하지 않음
  if (!isOnVisibleMenu) goto(visibleMenuItems[0].href);
});
```

### 모듈 레벨 싱글톤 패턴

`.svelte.ts` 파일에서 모듈 스코프의 `$state`를 사용하면 앱 전체에서 단일 인스턴스를 공유합니다. `createStore()` 팩토리 없이 import만으로 동일한 상태에 접근합니다.

```typescript
// auth.svelte.ts — 모듈 레벨 (함수 밖)
let state = $state<AuthState>({ ... });

// 어디서 import해도 같은 state 참조
export const auth = {
  get authenticated() { return state.authenticated; },
  ...
};
```

---

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

### `/devtools/perf-generator` -- Perf Content Code Generator

JSON 구조 입력 → perf-content 패턴에 맞는 Svelte 컴포넌트 코드를 자동 생성하는 개발 도구.

- **좌측**: JSON 입력 (Text/Tree 탭 전환), 필드 분석/매핑, 설정
- **우측**: Code 탭 (생성된 코드 + Copy) / Preview 탭 (PerfChart + DataTable 실시간 미리보기)
- `object-of-arrays`, `array-of-objects` 두 가지 JSON 구조 자동 감지

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

## UI/UX 패턴

### 페이지 전환

SvelteKit `onNavigate` 훅 + View Transitions API로 150ms fade 전환을 구현합니다. 미지원 브라우저에서는 기존처럼 즉시 전환됩니다 (graceful degradation).

### 로딩 상태

| 상황 | 패턴 | 컴포넌트 |
|------|------|----------|
| 페이지 초기 로딩 (테이블) | 테이블 스켈레톤 | `TableSkeleton` |
| 페이지 초기 로딩 (차트/카드) | Skeleton 조합 | `Skeleton` |
| 차트 렌더링 | 오버레이 스피너 | `PerfChart` 내장 |
| 비동기 버튼 | disabled + Loader 아이콘 | `saving` 상태 변수 |
| 데이터 없음 | 아이콘 + 안내 메시지 | `SearchXIcon` + 텍스트 |

### 사용자 피드백

| 액션 | 피드백 |
|------|--------|
| CRUD 성공 | `toast.success()` |
| CRUD 실패 | `toast.error()` (서버 메시지 파싱) |
| 파괴적 액션 (삭제) | `ConfirmDialog` → 확인 후 실행 |
| 미저장 변경사항 | `ConfirmDialog` (variant: default) |
| 폼 필수 필드 미입력 | 빨간 테두리 + "필수 항목입니다" 인라인 메시지 |
| 버튼 클릭 | `active:scale-[0.97]` 눌림 효과 |

### 폼 밸리데이션

`submitted` 상태 패턴을 사용합니다. Save 버튼은 항상 활성화되어 있고, 클릭 시 `submitted = true`로 설정하여 필수 필드 에러를 시각화합니다.

```svelte
<Select.Trigger class="... {submitted && !form.field ? 'border-destructive ring-1 ring-destructive/30' : ''}">
<Input class="... {submitted && !form.name ? 'border-destructive ring-1 ring-destructive/30' : ''}" />
{#if submitted && !form.name}<p class="text-[10px] text-destructive">필수 항목입니다</p>{/if}
```

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
