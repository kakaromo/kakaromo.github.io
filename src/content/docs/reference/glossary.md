---
title: 용어집
description: MOVE 에서 사용되는 주요 용어·약어·L3 개념을 카테고리별로 정리
---

MOVE 에서 사용되는 용어를 **도메인 / 런타임 컴포넌트 / 데이터·컬럼 / 인증·권한 / 성능 시각화 / 프론트 / L3 개념** 으로 나눠 정리합니다. 여러 문서에서 반복 등장하는 용어의 단일 출처입니다.

## 도메인 · 조직

| 용어 | 설명 |
|------|------|
| **MOVE (Mobile OS Validation Eco-system)** | 이 프로젝트의 공식 명칭 (2026-04-14 확정, 이전 명칭 Samsung Portal 에서 리브랜딩). Java 패키지도 `com.samsung.move` |
| **TR (TestRequest)** | 테스트 요청 단위. 펌웨어 버전 등 테스트 대상 정보를 포함 |
| **TC (TestCase)** | 테스트 케이스. 실행할 테스트의 종류와 설정을 정의 |
| **History** | 테스트 실행 이력. TR + TC 조합의 실제 실행 결과를 기록 |
| **Set** | 테스트 대상 디바이스 세트. 슬롯 위치 정보 포함 |
| **setLocation** | `T1-1` 같은 tentacle-slot 복합 식별자. 정규식으로 tentacle 이름 추출 |
| **testTrId / testTrName** | 슬롯에 할당된 TR 의 id / name. Metadata 제품 매칭 등에 사용 |
| **testTool / testToolName** | Run 중인 도구 이름 (예: `agent_iotest`). 변경되면 Metadata 재시작 |
| **SlotInfomation** | 하드웨어 슬롯 메타 DB. `tentacleIp` 로 SSH 직접 접속도 가능 (2026-04-18) |

## 하드웨어 · 스토리지

| 용어 | 설명 |
|------|------|
| **Slot** | Head 서버의 테스트 슬롯. 디바이스가 물리적으로 연결되는 위치 |
| **Head Server** | UFS 하드웨어 테스트를 제어하는 서버. TCP 듀얼 소켓으로 Portal 과 통신 |
| **Tentacle** | 테스트 디바이스가 연결된 서버. T1, T2, T3, T4 로 구분 |
| **UFS (Universal Flash Storage)** | 모바일/임베디드용 플래시 스토리지 표준 |
| **NAND** | 비휘발성 메모리. UFS 스토리지의 물리적 저장 매체 |
| **FW (Firmware)** | UFS 디바이스의 내장 소프트웨어 |
| **OEM** | 주문자 상표 부착 생산 (Original Equipment Manufacturer). UFS 제조사 |
| **controller / cellType / nandType** | UFS 제품을 식별하는 3 축. Metadata `UfsProductMetadata` 의 wildcard 매칭 키 |
| **f2fs** | Flash-Friendly File System. Android 가 userdata 파티션에 주로 사용. Metadata 의 3 전용 뷰(Iostat/Heatmap/Bitmap) 대상 |
| **f2fs segment** | f2fs 의 기본 할당 단위. 6가지 type (HD/WD/CD/HN/WN/CN = Hot/Warm/Cold × Data/Node) |
| **fileSystem (FILE_SYSTEM)** | 성능 테스트가 동작한 파일시스템 (f2fs/ext4/raw). 성능 차트 subtext + History 컬럼 + Excel export 포함 |

## Metadata (UFS 모니터링)

| 용어 | 설명 |
|------|------|
| **command_type (7가지)** | `tool` / `sysfs` / `keyvalue` / `raw` / `table` / `bitmap` / `binary`. UFS 공급사·펌웨어 다양성을 흡수하는 파싱 분기. 새 포맷이 나오면 Executor 메서드 + switch 분기만 추가 |
| **`{userdata}` placeholder** | f2fs 파티션 block 이름(`sda10` 등) 을 `readlink /dev/block/by-name/userdata` 로 1회 조회 후 commandTemplate 에서 치환 |
| **typeKey** | Metadata 타입의 URL-safe 키 (`f2fs_iostat_info`, `segment_info` 등). 프론트가 contains 매칭으로 f2fs 전용 뷰 자동 활성화 |
| **Iostat / Heatmap / Bitmap 뷰** | f2fs 전용 3 뷰 (IostatTableView / SegmentHeatmap / BitmapGridView) — 시간·공간 2D 시각화 |
| **initslot clearSlot** | HEAD `initslot` 명령 도착 시 `MetadataMonitorService.clearSlot()` 호출 — enabled/excludedTypes/interval + activeMonitors 초기화. VM 의 `debug_*.json` 은 이력 보존 |
| **predefined_structs.kind** | `metadata` / `dlm` / `general` 3 종 분리 — Metadata Admin UI 는 `kind='metadata'` 만 선택 가능 |

## Trace Analysis

| 용어 | 설명 |
|------|------|
| **Trace Analysis** | UFS/Block/UFSCUSTOM I/O trace 로그 분석 모듈 (L2 19호). 수 GB parquet → Arrow IPC 차트 + 상세 통계 |
| **UFS trace / Block trace / UFSCUSTOM** | 3 종 원본 로그 타입 — parquet 변환 단계에서 공용 10 컬럼 스키마로 정규화 |
| **Arrow IPC** | Apache Arrow Inter-Process Communication. 차트 데이터를 JSON 대신 컬럼 지향 바이너리로 전송 (페이로드 50~70% 감소, 파싱 수 ms) |
| **ProjectionMask / RowFilter** | parquet 의 컬럼 선택 + 조건 pruning — 5GB 파일에서 필요한 컬럼·row group 만 디코딩 |
| **AsyncFileReader (Rust)** | parquet crate 의 trait — 임의 byte range 를 async 로 공급. MinIO `MinioParquetReader` 가 구현해 `/tmp` 경유 없이 range-GET 스트리밍 |
| **time-bucket decimate** | target_points × 3 이내로 샘플링하는 다운샘플링 알고리즘. 버킷당 first/last/qd_argmax 3 점 보존 |
| **Deck.gl OrthographicView** | WebGL 2D 직교 투영 뷰. `ScatterplotLayer` binary attribute 로 1M 포인트 60fps |

## 런타임 컴포넌트

| 용어 | 설명 |
|------|------|
| **guacd** | Apache Guacamole 데몬. SSH/RDP 를 Guacamole 프로토콜로 변환. VM 별 host/port 또는 글로벌 fallback |
| **BinMapper** | Binary Struct Mapper (L2 엔진 축). C/C++ 구조체 정의로 바이너리 데이터를 매핑. Metadata binary command_type 이 **서비스 계층에서 재사용** |
| **MinIO** | S3 호환 오브젝트 스토리지. 파일 업로드/탐색/다운로드. Trace parquet 의 async range-GET 저장소로도 사용 |
| **Agent** | Android 디바이스 벤치마크/시나리오/trace 수집 Go gRPC 서버 (port 50051) |
| **Excel Service** | 성능 결과를 .xlsx 로 export 하는 Go gRPC 서버 (port 50052). 네이티브 Excel 차트 포함 |
| **Trace 서비스 (Rust)** | UFS/Block/UFSCUSTOM parser + parquet + Arrow IPC 서비스 (port 50053) |
| **head / headType** | HEAD 연결 타입. 0=호환성, 1=성능. Slots 페이지 탭 필터링에 사용 |
| **Tentacle VM** | Portal 이 SSH/SFTP 로 접속하는 디바이스 호스트. logPath / metadata JSON 저장소 |

## 전송 · 프로토콜

| 용어 | 설명 |
|------|------|
| **SSE (Server-Sent Events)** | 서버→클라 단방향 실시간. 슬롯 상태, 벤치마크 진행, T32 step, 권한 알림, Reparse 진행에 사용 |
| **gRPC** | Google 의 RPC 프레임워크. HTTP/2 + protobuf. MOVE 는 Agent/Excel/Trace 3 외부 서비스 통신에 사용 |
| **Hybrid Flow (OIDC)** | ADFS 인증 프로토콜. `response_type=code+id_token`, `response_mode=form_post` |
| **CSRF (XSRF-TOKEN)** | 쿠키 기반 CSRF 방어. 모든 변경 요청에 `X-XSRF-TOKEN` 헤더 필요. ADFS callback 만 예외 |

## 인증 · 권한

| 용어 | 설명 |
|------|------|
| **ADFS (Active Directory Federation Services)** | Samsung 사내 SSO. Portal 이 수동 Hybrid Flow 로 연동 |
| **portal.auth.disabled** | 로컬 개발 편의용 플래그. true 면 가상 Developer 로 전 API 통과 |
| **disabled 게이트** | 신규 ADFS 사용자는 `enabled=false` 로 생성. 관리자 승인 전까지 `/api/**` 차단 (except `/api/auth/**`) |
| **접근 요청 (PermissionRequest)** | disabled 사용자가 제출하는 활성화 요청. PENDING/APPROVED/REJECTED 상태 |
| **ActionPermission** | `url_pattern + http_method → permission_key` DB 매핑. 컨트롤러 수정 없이 권한 적용 |
| **UserHeadAccess** | 사용자별 Head row 접근 화이트리스트. 레코드 없음 = 전체 허용 |
| **permission_key** | `menu:*` + `action:*` 형식의 권한 식별자. 예: `menu:slots`, `action:agent:benchmark` |
| **Test Mode** | 테스트 DB 인스턴스 접근을 제한하는 2차 게이트. `action:global:test-mode` 권한 + 주황 배너 |

## 성능 시각화

| 용어 | 설명 |
|------|------|
| **Parser / parserId** | 성능 결과 데이터의 구조를 정의. `parserRegistry.ts` 에서 시각화 컴포넌트와 매핑 |
| **Reparse** | 완료된 성능 TC 의 원본 로그에서 결과 JSON 을 다시 생성. `ExecutorService(4 threads)` 백그라운드 |
| **parsingcontroller** | 원격 tentacle 서버에 배포된 로그 파싱 바이너리. `parsingMethod` + 로그 파일 입력 → JSON 결과 |
| **latencyType** | ioType 비트마스크. R=1, W=2, U=4. 조합 예: RW=3, RWU=7. `parsingcontroller` 에 전달 |
| **GenPerf** | 범용 성능 데이터 시각화 컴포넌트. Read/Write/FlushTime 탭, Line/Scatter 전환 |
| **PerfChart** | ECharts 기반 공통 차트 컴포넌트. `renderChartToImage()` 가 다크모드 감지해 테마 반영 (다크모드는 2026-04 제거됨) |
| **ChartColorPicker** | Legend 색상 커스터마이징. 팔레트 버튼 + 색상 피커. `sharedColors` 로 차트 간 공유 |
| **CompareSheet / VS 버튼** | 성능 결과 비교 시트. VS 버튼 hover 시 미리보기 팝오버 |
| **ScenarioCanvas** | @xyflow/svelte 기반 Agent 시나리오 편집기. 노드·엣지로 시각적 구성 |
| **DAG** | Directed Acyclic Graph. ScenarioCanvas 에서 조건 분기가 포함된 시나리오의 실행 흐름 |
| **Perf Generator** | JSON 샘플 → Svelte 컴포넌트 코드 자동 생성 devtool (L2 엔진 축) |

## 데이터 · 컬럼

| 용어 | 설명 |
|------|------|
| **headType** | 0=호환성 / 1=성능. HEAD 연결 구분 |
| **Δ / Delta 모드** | 누적 값을 변화량으로 표현. 각 key 독립 on/off. 차트 범례·테이블에 `(Δ)` 표시 |
| **Arrow Schema** | RecordBatch 의 컬럼 정의 (name + type). IPC stream 당 1개 |
| **RecordBatch** | Arrow 의 기본 단위. Schema + 같은 길이 Array 리스트 |

## 프론트 · Svelte

| 용어 | 설명 |
|------|------|
| **Svelte 5 Runes** | 반응성 시스템 — `$state`, `$derived`, `$effect`, `$props` |
| **paneforge** | 분할 패널 라이브러리. Trace/Agent 페이지의 3-pane 레이아웃 + `autoSaveId` localStorage |
| **common.ts** | 공통 CSS 유틸 (`inputSm`, `emptyState`, `tagMuted`, `btnXs` 등). 41 파일 적용 |
| **noPermission 화면** | disabled 사용자용 전용 라우팅. 경로 무관하게 "접근 권한이 없습니다" + 접근 요청 UI |

## L3 개념 (Trace L2 에서 도입, 2026-04-24)

| 용어 | 문서 |
|---|---|
| **Apache Arrow & IPC** | [`/learn/l3-concepts/data/apache-arrow/`](/learn/l3-concepts/data/apache-arrow/) |
| **Parquet ProjectionMask + RowFilter** | [`/learn/l3-concepts/data/parquet-projection/`](/learn/l3-concepts/data/parquet-projection/) |
| **AsyncFileReader trait (Rust)** | [`/learn/l3-concepts/rust/async-file-reader/`](/learn/l3-concepts/rust/async-file-reader/) |
| **Deck.gl OrthographicView + binary attribute** | [`/learn/l3-concepts/webgl/deckgl-orthographic/`](/learn/l3-concepts/webgl/deckgl-orthographic/) |

기존 L3:

| 카테고리 | 개념 |
|---|---|
| **Spring Boot 4** | `@Component`, `@Entity`, `@RequiredArgsConstructor`, `@Scheduled`, gRPC, `@ServerEndpoint`, `SseEmitter`, `StreamObserver` |
| **Svelte 5** | `$derived`, `$effect`, `$props`, `$state` |

## 기타

| 용어 | 설명 |
|------|------|
| **HikariCP** | Spring Boot 기본 JDBC 커넥션 풀 |
| **DataZoom** | ECharts 줌 기능. 마우스 휠로 차트 확대/축소 |
| **SessionLock** | 원격 VM 세션 잠금. 동시 접근 방지 배타 잠금 |
| **AppMacro** | Android 이벤트 녹화/재생 (로드맵, L2 미등록) |
| **TC Group** | 자주 쓰는 TC 조합을 그룹으로 저장. Slots 페이지 Chip UI |
| **Bitbucket 브랜치 감지** | @Scheduled 폴링 방식 (Webhook 미지원 사내 Bitbucket 대응). DETECTED → DOWNLOADED 상태 전이 |

---

## 관련 문서

- [변경 이력](/reference/changelog/) — 용어가 도입된 시점 추적
- [인프라 구성](/reference/infrastructure/) — 포트·호스트·외부 서비스 배치
- [L2 19종 비교](/learn/l2-compare/) — 모든 L2 의 축별 대조
