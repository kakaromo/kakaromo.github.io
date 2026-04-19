#!/usr/bin/env node
// portal 레포의 지정된 파일·라인 범위만 `src/snippets/`로 복사한다.
// 목적: 런타임 fetch 없이 빌드타임에 `?raw` import로 코드 표시.
// 사용법: `node scripts/sync-snippets.mjs` — 결과물은 git에 커밋.
//
// manifest 스펙:
//   id         — 스니펫 식별자 (파일명으로도 사용)
//   source     — portal 레포 내 절대 경로
//   lines?     — [from, to] (1-indexed, inclusive). 생략 시 전체 파일
//   language   — 확장자. 'java' | 'ts' | 'svelte' | 'yaml' 등
//   note?      — 사람 주석. 왜 이 범위를 떼어냈는지

import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const REPO_ROOT = resolve(__dirname, '..');
const SNIPPETS_DIR = join(REPO_ROOT, 'src', 'snippets');
const PORTAL_ROOT = process.env.MOVE_PORTAL_ROOT ?? '/Users/songhyun/project/portal';

/** @type {Array<{id: string, source: string, lines?: [number, number], language: string, note?: string}>} */
const manifest = [
  {
    id: 'application-yaml-head',
    source: 'src/main/resources/application.yaml',
    lines: [10, 30],
    language: 'yaml',
    note: 'head.* + tentacle.* 설정',
  },
  {
    id: 'HeadConnection-entity',
    source: 'src/main/java/com/samsung/move/head/entity/HeadConnection.java',
    lines: [1, 78],
    language: 'java',
    note: 'portal_head_connections 엔티티 + 포트 계산 헬퍼',
  },
  {
    id: 'HeadTcpClient-connect',
    source: 'src/main/java/com/samsung/move/head/tcp/HeadTcpClient.java',
    lines: [109, 141],
    language: 'java',
    note: 'connect() — listen bind + outSocket + connect 커맨드',
  },
  {
    id: 'HeadTcpClient-accept-read',
    source: 'src/main/java/com/samsung/move/head/tcp/HeadTcpClient.java',
    lines: [147, 215],
    language: 'java',
    note: 'acceptLoop + readFromSocket + processChunk',
  },
  {
    id: 'HeadMessageParser',
    source: 'src/main/java/com/samsung/move/head/service/HeadMessageParser.java',
    lines: [15, 170],
    language: 'java',
    note: '정규식 패턴 + parseMessage + parseSlotMatch 32필드',
  },
  {
    id: 'HeadSlotStateStore',
    source: 'src/main/java/com/samsung/move/head/service/HeadSlotStateStore.java',
    lines: [1, 119],
    language: 'java',
    note: 'ConcurrentHashMap + AtomicLong version + updateSlots',
  },
  {
    id: 'HeadSseController',
    source: 'src/main/java/com/samsung/move/head/controller/HeadSseController.java',
    lines: [31, 168],
    language: 'java',
    note: 'EmitterWrapper + stream + @Scheduled pushUpdates + buildPayload',
  },
  {
    id: 'headSlotStore-svelte',
    source: 'frontend/src/lib/api/headSlotStore.svelte.ts',
    lines: [55, 177],
    language: 'ts',
    note: 'createHeadSlotStore — $state + EventSource + dedup + retry',
  },

  // ── Agent (L2 벤치마크) ──
  {
    id: 'AgentServer-entity',
    source: 'src/main/java/com/samsung/move/agent/entity/AgentServer.java',
    lines: [1, 57],
    language: 'java',
    note: 'portal_agent_servers — Agent gRPC 서버 등록',
  },
  {
    id: 'JobExecution-entity',
    source: 'src/main/java/com/samsung/move/agent/entity/JobExecution.java',
    lines: [1, 81],
    language: 'java',
    note: 'portal_job_executions — job 이력 (벤치마크/시나리오/Trace 공용)',
  },
  {
    id: 'AgentController-run',
    source: 'src/main/java/com/samsung/move/agent/controller/AgentController.java',
    lines: [178, 218],
    language: 'java',
    note: 'POST /api/agent/benchmark/run → gRPC RunBenchmark + JobExecution 저장',
  },
  {
    id: 'AgentController-progress-sse',
    source: 'src/main/java/com/samsung/move/agent/controller/AgentController.java',
    lines: [248, 307],
    language: 'java',
    note: 'GET /api/agent/benchmark/progress — gRPC stream → SSE 중계',
  },
  {
    id: 'AgentGrpcClient',
    source: 'src/main/java/com/samsung/move/agent/grpc/AgentGrpcClient.java',
    lines: [20, 100],
    language: 'java',
    note: 'ManagedChannel 설정 + blocking/async stub + subscribeJobProgressAsync',
  },
  {
    id: 'AgentConnectionManager',
    source: 'src/main/java/com/samsung/move/agent/grpc/AgentConnectionManager.java',
    lines: [14, 82],
    language: 'java',
    note: 'serverId별 gRPC client 캐싱 + host:port 변경 시 재연결',
  },
  {
    id: 'device_agent-proto',
    source: 'src/main/proto/device_agent.proto',
    lines: [1, 50],
    language: 'text',
    note: 'DeviceAgent service RPC 목록',
  },
  {
    id: 'agent-api-ts',
    source: 'frontend/src/lib/api/agent.ts',
    lines: [180, 200],
    language: 'ts',
    note: 'runBenchmark / getJobStatus 프론트 API',
  },

  // ── 원격 터미널 (L2 remote) ──
  {
    id: 'PortalServer-entity',
    source: 'src/main/java/com/samsung/move/admin/entity/PortalServer.java',
    lines: [1, 79],
    language: 'java',
    note: 'portal_servers — 접속 대상 서버 + ssh/rdp/vnc 포트 + guacd_host/port',
  },
  {
    id: 'GuacamoleProperties',
    source: 'src/main/java/com/samsung/move/guacamole/config/GuacamoleProperties.java',
    lines: [1, 21],
    language: 'java',
    note: 'guacamole.* yaml 설정 바인딩',
  },
  {
    id: 'GuacamoleTunnelEndpoint-decl',
    source: 'src/main/java/com/samsung/move/guacamole/endpoint/GuacamoleTunnelEndpoint.java',
    lines: [30, 82],
    language: 'java',
    note: '@ServerEndpoint 선언 + 필드 + 생성자',
  },
  {
    id: 'GuacamoleTunnelEndpoint-onopen',
    source: 'src/main/java/com/samsung/move/guacamole/endpoint/GuacamoleTunnelEndpoint.java',
    lines: [84, 147],
    language: 'java',
    note: 'onOpen — 파라미터 파싱 + Lock + VM 조회 + buildConfig + connect',
  },
  {
    id: 'GuacamoleTunnelEndpoint-connect',
    source: 'src/main/java/com/samsung/move/guacamole/endpoint/GuacamoleTunnelEndpoint.java',
    lines: [180, 273],
    language: 'java',
    note: 'connectDirect + buildConfig + readFromTunnel',
  },
  {
    id: 'GuacamoleTunnelEndpoint-onmessage-close',
    source: 'src/main/java/com/samsung/move/guacamole/endpoint/GuacamoleTunnelEndpoint.java',
    lines: [275, 322],
    language: 'java',
    note: 'onMessage(heartbeat + 터널 write) + onClose(정리)',
  },

  // ── Metadata 모니터링 (L2 metadata) ──
  {
    id: 'UfsMetadataCommand-entity',
    source: 'src/main/java/com/samsung/move/metadata/entity/UfsMetadataCommand.java',
    lines: [1, 65],
    language: 'java',
    note: 'ufs_metadata_commands — commandType 4가지(tool/sysfs/raw/keyvalue)',
  },
  {
    id: 'MetadataMonitorProperties',
    source: 'src/main/java/com/samsung/move/metadata/config/MetadataMonitorProperties.java',
    lines: [1, 21],
    language: 'java',
    note: 'metadata.monitor.* — enabled / pollInterval / collectionInterval',
  },
  {
    id: 'MetadataMonitor-fields',
    source: 'src/main/java/com/samsung/move/metadata/service/MetadataMonitorService.java',
    lines: [40, 133],
    language: 'java',
    note: '슬롯별 활성 모니터 · excluded types · 8개 스레드풀 + SlotMonitorContext',
  },
  {
    id: 'MetadataMonitor-scheduled',
    source: 'src/main/java/com/samsung/move/metadata/service/MetadataMonitorService.java',
    lines: [134, 234],
    language: 'java',
    note: '@Scheduled checkSlotStateChanges + startMonitoring (TR 기반 제품→command 매핑 + 수집 주기)',
  },
  {
    id: 'MetadataMonitor-monitorOnce',
    source: 'src/main/java/com/samsung/move/metadata/service/MetadataMonitorService.java',
    lines: [290, 351],
    language: 'java',
    note: 'monitorOnce — commandType 분기 + JSON 파싱 + 인메모리 + 파일 저장',
  },
  {
    id: 'MetadataController-slot',
    source: 'src/main/java/com/samsung/move/metadata/controller/MetadataController.java',
    lines: [18, 91],
    language: 'java',
    note: 'REST API — types / for-tr / slot 상태 / slot 데이터',
  },

  // ── T32 Dump (L2 t32) ──
  {
    id: 'T32Config-entity',
    source: 'src/main/java/com/samsung/move/t32/entity/T32Config.java',
    lines: [1, 88],
    language: 'java',
    note: 'portal_t32_configs — 서버 그룹·JTAG/T32PC·명령 템플릿·경로 매핑',
  },
  {
    id: 'T32DumpController',
    source: 'src/main/java/com/samsung/move/t32/controller/T32DumpController.java',
    lines: [1, 52],
    language: 'java',
    note: 'POST /api/t32/dump/execute — SSE 반환 + DumpRequest record',
  },
  {
    id: 'T32DumpService-executeDump',
    source: 'src/main/java/com/samsung/move/t32/service/T32DumpService.java',
    lines: [78, 220],
    language: 'java',
    note: 'executeDump 오케스트레이션 — 4 Step 순차 + 경로 변환 + Canary ZIP',
  },
  {
    id: 'T32DumpService-step1-2',
    source: 'src/main/java/com/samsung/move/t32/service/T32DumpService.java',
    lines: [222, 282],
    language: 'java',
    note: 'Step 1 JTAG (success pattern regex) + Step 2 Attach (Down 감지)',
  },
  {
    id: 'T32DumpService-step3',
    source: 'src/main/java/com/samsung/move/t32/service/T32DumpService.java',
    lines: [284, 326],
    language: 'java',
    note: 'Step 3 Dump — {result_path}/{branch_path} 치환 + fail 키워드 감지',
  },
  {
    id: 'T32DumpService-ssh',
    source: 'src/main/java/com/samsung/move/t32/service/T32DumpService.java',
    lines: [332, 391],
    language: 'java',
    note: 'JSch SSH 실행 — stdout/stderr 실시간 step-output 전송 + timeout',
  },

  // ── Pre-Command (L2 precmd) ──
  {
    id: 'PreCommand-entity',
    source: 'src/main/java/com/samsung/move/head/precmd/entity/PreCommand.java',
    lines: [1, 50],
    language: 'java',
    note: 'portal_pre_commands — 명령 템플릿 (name + commands JSON array)',
  },
  {
    id: 'SlotPreCommand-entity',
    source: 'src/main/java/com/samsung/move/head/precmd/entity/SlotPreCommand.java',
    lines: [1, 55],
    language: 'java',
    note: 'portal_slot_pre_commands — setLocation(UK) + preCommand + tcPreCommandIds CSV',
  },
  {
    id: 'PreCommandAutoExecutor-fields',
    source: 'src/main/java/com/samsung/move/head/precmd/service/PreCommandAutoExecutor.java',
    lines: [34, 83],
    language: 'java',
    note: '필드 + 중복 방지 Set + onSlotStateChanged 훅 진입점',
  },
  {
    id: 'PreCommandAutoExecutor-tc',
    source: 'src/main/java/com/samsung/move/head/precmd/service/PreCommandAutoExecutor.java',
    lines: [85, 172],
    language: 'java',
    note: 'tryExecuteTcPreCommand — testcaseStatus에서 첫 미완료 position 찾고 TC Pre-Command 실행',
  },
  {
    id: 'PreCommandService-exec',
    source: 'src/main/java/com/samsung/move/head/precmd/service/PreCommandService.java',
    lines: [190, 250],
    language: 'java',
    note: 'executeSync + parseCommands + resolveCommand(adb -s usbId) + extractVmName',
  },
  {
    id: 'PreCommandController-slot',
    source: 'src/main/java/com/samsung/move/head/precmd/controller/PreCommandController.java',
    lines: [60, 128],
    language: 'java',
    note: '슬롯 CRUD — list / assign(TC 자동 초기화) / unassign',
  },
  {
    id: 'PreCommandController-tc',
    source: 'src/main/java/com/samsung/move/head/precmd/controller/PreCommandController.java',
    lines: [130, 194],
    language: 'java',
    note: 'TC CRUD — assign(슬롯 자동 해제) / unassign — position 기반',
  },

  // ── Log Browser (L2 log-browser) ──
  {
    id: 'LogBrowserConfig',
    source: 'src/main/java/com/samsung/move/logbrowser/config/LogBrowserConfig.java',
    lines: [1, 39],
    language: 'java',
    note: '@ConditionalOnProperty tentacle.access-mode로 Local vs SSH 빈 선택',
  },
  {
    id: 'LogBrowserService-interface',
    source: 'src/main/java/com/samsung/move/logbrowser/service/LogBrowserService.java',
    lines: [1, 33],
    language: 'java',
    note: '공통 인터페이스 + FileEntry/FileContent/SearchResult record',
  },
  {
    id: 'SshLogBrowserService-list',
    source: 'src/main/java/com/samsung/move/logbrowser/service/SshLogBrowserService.java',
    lines: [59, 97],
    language: 'java',
    note: 'SSH listFiles — ChannelSftp.ls + 폴더 먼저 정렬',
  },
  {
    id: 'SshLogBrowserService-search',
    source: 'src/main/java/com/samsung/move/logbrowser/service/SshLogBrowserService.java',
    lines: [191, 211],
    language: 'java',
    note: 'SSH searchInFile — rg -n --no-heading --encoding auto + shellEscape + iconv pipe',
  },
  {
    id: 'SshLogBrowserService-session',
    source: 'src/main/java/com/samsung/move/logbrowser/service/SshLogBrowserService.java',
    lines: [302, 369],
    language: 'java',
    note: 'getOrCreateCachedSession + execCommand (exit>1만 예외, rg no-match=1 허용)',
  },
  {
    id: 'LocalLogBrowserService-list',
    source: 'src/main/java/com/samsung/move/logbrowser/service/LocalLogBrowserService.java',
    lines: [33, 70],
    language: 'java',
    note: 'Local listFiles — Files.list + 동일 정렬 + ".." 엔트리',
  },
  {
    id: 'LocalLogBrowserService-search-path',
    source: 'src/main/java/com/samsung/move/logbrowser/service/LocalLogBrowserService.java',
    lines: [206, 312],
    language: 'java',
    note: 'Local searchInFile (ProcessBuilder rg) + resolveLocalPath (path traversal 방어)',
  },
  {
    id: 'LogBrowserController-download-dir',
    source: 'src/main/java/com/samsung/move/logbrowser/controller/LogBrowserController.java',
    lines: [82, 144],
    language: 'java',
    note: 'GET /download-dir — Local ZipOutputStream / SSH 원격 zip 후 SFTP',
  },

  // ── Excel Export (L2 excel) ──
  {
    id: 'excel-service-proto',
    source: 'src/main/proto/excel_service.proto',
    lines: [1, 24],
    language: 'text',
    note: 'RPC GenerateExcel + ExcelRequest/Response 메시지',
  },
  {
    id: 'ExcelGrpcClient',
    source: 'src/main/java/com/samsung/move/testdb/excel/ExcelGrpcClient.java',
    lines: [1, 30],
    language: 'java',
    note: 'GrpcChannelFactory → BlockingStub + generateExcel 빌더 호출',
  },
  {
    id: 'PerformanceResultDataService-fetch',
    source: 'src/main/java/com/samsung/move/testdb/service/PerformanceResultDataService.java',
    lines: [36, 161],
    language: 'java',
    note: 'fetchResultData — History/TC/Parser DB 조합 + LogBrowser JSON 읽기 + 불완전 JSON 복구',
  },
  {
    id: 'ExcelExportController',
    source: 'src/main/java/com/samsung/move/testdb/controller/ExcelExportController.java',
    lines: [1, 34],
    language: 'java',
    note: 'GET /{historyId}/excel — data fetch + gRPC call + ResponseEntity<byte[]>',
  },

  // ── Bitbucket 브랜치 모니터 (L2 bitbucket) ──
  {
    id: 'BitbucketMonitorProperties',
    source: 'src/main/java/com/samsung/move/bitbucket/config/BitbucketMonitorProperties.java',
    lines: [1, 13],
    language: 'java',
    note: '@ConfigurationProperties bitbucket.monitor — enabled + defaultTargetPath',
  },
  {
    id: 'BitbucketRepo-entity',
    source: 'src/main/java/com/samsung/move/bitbucket/entity/BitbucketRepo.java',
    lines: [1, 67],
    language: 'java',
    note: 'portal_bitbucket_repos — serverUrl/projectKey/repoSlug/PAT(평문)/autoDownload/controller',
  },
  {
    id: 'BitbucketBranch-entity',
    source: 'src/main/java/com/samsung/move/bitbucket/entity/BitbucketBranch.java',
    lines: [1, 48],
    language: 'java',
    note: 'portal_bitbucket_branches — status(DETECTED/DOWNLOADING/DOWNLOADED/FAILED) + filePath + commitDate',
  },
  {
    id: 'BitbucketMonitorService-poll',
    source: 'src/main/java/com/samsung/move/bitbucket/service/BitbucketMonitorService.java',
    lines: [33, 108],
    language: 'java',
    note: '@Scheduled fixedDelay 5분 polling + pollRepo + autoDownload 분기 (DETECTED 저장 or downloadBranch)',
  },
  {
    id: 'BitbucketApiClient-ssl-list',
    source: 'src/main/java/com/samsung/move/bitbucket/service/BitbucketApiClient.java',
    lines: [28, 119],
    language: 'java',
    note: 'HttpClient(SSL 무시) + listBranches 페이지네이션 + Bearer PAT + metadata authorTimestamp 추출',
  },
  {
    id: 'BitbucketApiClient-archive',
    source: 'src/main/java/com/samsung/move/bitbucket/service/BitbucketApiClient.java',
    lines: [121, 195],
    language: 'java',
    note: 'downloadArchive (at=%2f encoded ref, InputStream) + getCommitTimestamp fallback',
  },
  {
    id: 'BitbucketMonitorService-download',
    source: 'src/main/java/com/samsung/move/bitbucket/service/BitbucketMonitorService.java',
    lines: [117, 224],
    language: 'java',
    note: 'downloadBranch 6단계 — 디렉토리 생성 → ZIP 스트리밍(1MB SSE 진행) → extractZip(Zip Slip 방지) → ZIP 삭제 → DB 업데이트',
  },

  // ── MinIO 파일 스토리지 (L2 minio) ──
  {
    id: 'MinioProperties',
    source: 'src/main/java/com/samsung/move/minio/config/MinioProperties.java',
    lines: [1, 17],
    language: 'java',
    note: '@ConfigurationProperties minio — endpoint / port / credentials',
  },
  {
    id: 'MinioConfig',
    source: 'src/main/java/com/samsung/move/minio/config/MinioConfig.java',
    lines: [1, 18],
    language: 'java',
    note: 'MinioClient.builder() — io.minio:minio 8.5.14 SDK 래퍼',
  },
  {
    id: 'MinioStorageService',
    source: 'src/main/java/com/samsung/move/minio/service/MinioStorageService.java',
    lines: [1, 150],
    language: 'java',
    note: '버킷/오브젝트 CRUD — list/listRecursive/upload/download/stat/createFolder/delete',
  },
  {
    id: 'MinioUploadController',
    source: 'src/main/java/com/samsung/move/minio/controller/MinioUploadController.java',
    lines: [1, 53],
    language: 'java',
    note: 'POST /upload — MultipartFile + 2GB 압축 강제 검증 + prefix 경로 조립',
  },
  {
    id: 'MinioController-list-visibility',
    source: 'src/main/java/com/samsung/move/minio/controller/MinioController.java',
    lines: [28, 80],
    language: 'java',
    note: 'GET /buckets — Admin은 visibility 토글 전체, User는 visible 만',
  },
  {
    id: 'MinioController-download',
    source: 'src/main/java/com/samsung/move/minio/controller/MinioController.java',
    lines: [152, 206],
    language: 'java',
    note: '/download-folder (ZipOutputStream recursive) + /download (InputStreamResource + UTF-8 filename)',
  },

  // ── 인증·권한 (L2 auth) ──
  {
    id: 'AdfsProperties',
    source: 'src/main/java/com/samsung/move/auth/config/AdfsProperties.java',
    lines: [1, 20],
    language: 'java',
    note: '@ConfigurationProperties portal.adfs — enabled/clientId/authorizeUrl/redirectUrl/logoutUrl/scope',
  },
  {
    id: 'PortalUser-entity',
    source: 'src/main/java/com/samsung/move/auth/entity/PortalUser.java',
    lines: [1, 62],
    language: 'java',
    note: 'portal_users — username / adfsUserId / role(USER/ADMIN) / enabled',
  },
  {
    id: 'AuthController-adfs-login',
    source: 'src/main/java/com/samsung/move/auth/controller/AuthController.java',
    lines: [158, 180],
    language: 'java',
    note: 'GET /adfs/login — nonce 생성 + authorize URL 조립 + redirect',
  },
  {
    id: 'AuthController-adfs-callback',
    source: 'src/main/java/com/samsung/move/auth/controller/AuthController.java',
    lines: [181, 252],
    language: 'java',
    note: 'POST /adfs/callback — id_token 파싱 + claims 추출 + PortalUser 생성/갱신 + 세션 저장',
  },
  {
    id: 'UserPermissionService-constants',
    source: 'src/main/java/com/samsung/move/auth/service/UserPermissionService.java',
    lines: [1, 55],
    language: 'java',
    note: 'ALL_PERMISSIONS 17개 정의 (메뉴 8 + 액션 9)',
  },
  {
    id: 'ActionPermissionInterceptor',
    source: 'src/main/java/com/samsung/move/auth/interceptor/ActionPermissionInterceptor.java',
    lines: [1, 101],
    language: 'java',
    note: 'URL + HTTP method → DB ActionPermission 매칭 + 권한 체크 + 403 응답',
  },

  // ── TestDB 결과 모델 (L2 testdb) ──
  {
    id: 'PerformanceTestRequest-entity',
    source: 'src/main/java/com/samsung/move/testdb/entity/PerformanceTestRequest.java',
    lines: [1, 70],
    language: 'java',
    note: 'FW 메타(컨트롤러·NAND·CS 버전) + getFw() @Transient + Redis setFw 무시',
  },
  {
    id: 'PerformanceTestCase-entity',
    source: 'src/main/java/com/samsung/move/testdb/entity/PerformanceTestCase.java',
    lines: [1, 57],
    language: 'java',
    note: '성능 시나리오 정의 — name / fileName / parserId / category / ioType',
  },
  {
    id: 'PerformanceHistory-entity',
    source: 'src/main/java/com/samsung/move/testdb/entity/PerformanceHistory.java',
    lines: [1, 68],
    language: 'java',
    note: '실행 결과 스냅샷 — logPath(원격 JSON 포인터) + result + ManyToOne TR/TC',
  },
  {
    id: 'CompatibilityHistory-entity',
    source: 'src/main/java/com/samsung/move/testdb/entity/CompatibilityHistory.java',
    lines: [1, 60],
    language: 'java',
    note: '호환성 실행 결과 — LocalDateTime 기반 + failCause + setProductName',
  },
  {
    id: 'PerformanceHistoryRepository',
    source: 'src/main/java/com/samsung/move/testdb/repository/PerformanceHistoryRepository.java',
    lines: [1, 54],
    language: 'java',
    note: 'JpaSpecificationExecutor + @Query 대시보드 집계 (countByTr/TcAndResult)',
  },
  {
    id: 'PerformanceResultDataService-fetch',
    source: 'src/main/java/com/samsung/move/testdb/service/PerformanceResultDataService.java',
    lines: [1, 161],
    language: 'java',
    note: 'fetchResultData — history→TC→parser 경로 해석 + COLLECTING + ResultData record',
  },
  {
    id: 'PerformanceResultDataService-repair',
    source: 'src/main/java/com/samsung/move/testdb/service/PerformanceResultDataService.java',
    lines: [163, 218],
    language: 'java',
    note: 'tryRepairJson — trailing comma + 닫히지 않은 bracket/문자열 복구',
  },
  {
    id: 'ReparseJob',
    source: 'src/main/java/com/samsung/move/testdb/reparse/ReparseJob.java',
    lines: [1, 56],
    language: 'java',
    note: '비동기 리파싱 Job 상태 머신 — preparing/running/completed/failed',
  },
  {
    id: 'PerformanceReparseService-start',
    source: 'src/main/java/com/samsung/move/testdb/reparse/PerformanceReparseService.java',
    lines: [28, 132],
    language: 'java',
    note: 'FixedThreadPool(4) + jobs Map + historyJobMap 중복 방어 + startReparse',
  },
  {
    id: 'DashboardController-stats',
    source: 'src/main/java/com/samsung/move/testdb/controller/DashboardController.java',
    lines: [1, 119],
    language: 'java',
    note: '/api/dashboard/stats — 두 도메인 공통 집계 (trCount/passFail/byFw/byTc/recent)',
  },

  // ── BinMapper (L2 binmapper) ──
  {
    id: 'PredefinedStruct-entity',
    source: 'src/main/java/com/samsung/move/binmapper/entity/PredefinedStruct.java',
    lines: [1, 48],
    language: 'java',
    note: 'predefined_structs — name/category/structText(TEXT) 저장, 재사용 가능한 struct 사전',
  },
  {
    id: 'CppType-readValue',
    source: 'src/main/java/com/samsung/move/binmapper/model/CppType.java',
    lines: [160, 197],
    language: 'java',
    note: 'enum 13종 + fromString(alias map 100+) + readValue(ByteBuffer) switch 분기',
  },
  {
    id: 'CppStructLexer-tokenize',
    source: 'src/main/java/com/samsung/move/binmapper/parser/CppStructLexer.java',
    lines: [1, 80],
    language: 'java',
    note: 'KEYWORDS Set + tokenize 메인 루프 (#pragma / __attribute__ / ident / number 분기)',
  },
  {
    id: 'CppStructParser-entry',
    source: 'src/main/java/com/samsung/move/binmapper/parser/CppStructParser.java',
    lines: [1, 80],
    language: 'java',
    note: 'parse 엔트리 + typedef/struct/union/enum 분기 + pragma pack 상태 유지',
  },
  {
    id: 'BinaryReader-map',
    source: 'src/main/java/com/samsung/move/binmapper/service/BinaryReaderService.java',
    lines: [1, 72],
    language: 'java',
    note: 'map 엔트리 + resolveEndianness + calculateStructSize (union vs struct, pack 분기)',
  },
  {
    id: 'BinaryReader-mapFields',
    source: 'src/main/java/com/samsung/move/binmapper/service/BinaryReaderService.java',
    lines: [74, 187],
    language: 'java',
    note: 'mapFields 재귀 — union/bitfield/char 배열/배열/nested struct/primitive 6 분기',
  },
  {
    id: 'BinaryReader-primitive-bitfield',
    source: 'src/main/java/com/samsung/move/binmapper/service/BinaryReaderService.java',
    lines: [189, 265],
    language: 'java',
    note: 'readPrimitiveField (ASCII 힌트 + enum 라벨) + mapBitfield (storage unit + bit mask)',
  },
  {
    id: 'BinaryReader-align-endian',
    source: 'src/main/java/com/samsung/move/binmapper/service/BinaryReaderService.java',
    lines: [267, 322],
    language: 'java',
    note: 'getFieldSize/Alignment + align (padding 계산) + resolveEndianness (ELF magic) + readCharArray',
  },
  {
    id: 'BinMapperController',
    source: 'src/main/java/com/samsung/move/binmapper/controller/BinMapperController.java',
    lines: [1, 54],
    language: 'java',
    note: '/api/binmapper/parse (MultipartFile + 6가지 struct 소스) + parse-struct + parse-header',
  },

  // ── Perf Generator (L2 perfgen) · 프론트엔드 전용 devtool ──
  {
    id: 'PerfGenerator-types',
    source: 'frontend/src/lib/components/PerfGenerator.types.ts',
    lines: [1, 26],
    language: 'ts',
    note: 'FieldRole · FieldType · TopLevelShape · FieldNode · TabInfo · AnalysisResult 공유 타입',
  },
  {
    id: 'PerfGenerator-flatten',
    source: 'frontend/src/lib/components/PerfGenerator.svelte',
    lines: [17, 79],
    language: 'svelte',
    note: '$state 정의 · guessYAxisUnit/capitalize · flattenFields 재귀 (4 분기 + role 휴리스틱)',
  },
  {
    id: 'PerfGenerator-analyzeA',
    source: 'frontend/src/lib/components/PerfGenerator.svelte',
    lines: [81, 133],
    language: 'svelte',
    note: 'analyzeJson Case 1 — object-of-arrays 분기 (arrayKeys 수집 → tabs · cycleField)',
  },
  {
    id: 'PerfGenerator-analyzeB',
    source: 'frontend/src/lib/components/PerfGenerator.svelte',
    lines: [135, 220],
    language: 'svelte',
    note: 'analyzeJson Case 2 — array-of-objects 분기 (top fields + nested tabs + 빈 tabKeys fallback)',
  },
  {
    id: 'PerfGenerator-derived',
    source: 'frontend/src/lib/components/PerfGenerator.svelte',
    lines: [222, 274],
    language: 'svelte',
    note: '$derived analysis · $effect tabOverrides 동기화 · uniqueFields 중복 제거 · setFieldRole',
  },
  {
    id: 'PerfGenerator-gen-header',
    source: 'frontend/src/lib/components/PerfGenerator.svelte',
    lines: [304, 395],
    language: 'svelte',
    note: 'generateObjectOfArrays — interface 조립 + tabDefs + chartOption 문자열 템플릿 조립',
  },
  {
    id: 'JsonTreeView-effect',
    source: 'frontend/src/lib/components/JsonTreeView.svelte',
    lines: [1, 60],
    language: 'svelte',
    note: 'parseResult $derived + auto-expand $effect (depth≤2, array limit 5)',
  },
  {
    id: 'PerfPreview-header',
    source: 'frontend/src/lib/components/PerfPreview.svelte',
    lines: [1, 80],
    language: 'svelte',
    note: 'Props + tabData $derived (shape 분기) + cycleAccessor / data/stat 필드 선별',
  },

  // ── iotest syscall 엔진 (L2 iotest · 세 번째 엔진 — side-effect 포함) ──
  {
    id: 'IOTestPreset-entity',
    source: 'src/main/java/com/samsung/move/agent/entity/IOTestPreset.java',
    lines: [1, 38],
    language: 'java',
    note: 'portal_iotest_presets — name/description/category/configJson(MEDIUMTEXT) 단일 컬럼 DSL 저장',
  },
  {
    id: 'iotest-types',
    source: 'frontend/src/routes/agent/iotest/types.ts',
    lines: [1, 79],
    language: 'ts',
    note: 'IOTestConfig/Thread/Command 타입 · 재귀 commands (loop/if 중첩) · ProgressEvent · ThreadProgress',
  },
  {
    id: 'iotest-opdefs-head',
    source: 'frontend/src/routes/agent/iotest/opDefs.ts',
    lines: [1, 75],
    language: 'ts',
    note: 'OpDef 인터페이스 + 20+ op 등록 — open/close/read/write 필드 스펙 (fd · offset 템플릿 · pattern)',
  },
  {
    id: 'iotest-presets-sample',
    source: 'frontend/src/routes/agent/iotest/presets.ts',
    lines: [1, 68],
    language: 'ts',
    note: 'IOTEST_PRESETS 배열 + 3 예시 (Offset Write · Offset R/W · Misaligned R/W) — {{i}} 템플릿 사용',
  },
  {
    id: 'IOTestPresetController',
    source: 'src/main/java/com/samsung/move/agent/controller/AgentController.java',
    lines: [1291, 1333],
    language: 'java',
    note: '/agent/iotest-presets CRUD + configJson Map/String 자동 변환 빌더',
  },
  {
    id: 'RunBenchmark-proto',
    source: 'src/main/proto/device_agent.proto',
    lines: [96, 164],
    language: 'text',
    note: 'BenchmarkTool enum(IOTEST=4) + RunBenchmarkRequest(params map) + JobProgress(stream용)',
  },
];

async function extractLines(text, lines) {
  if (!lines) return text;
  const [from, to] = lines;
  const all = text.split('\n');
  if (from < 1 || to > all.length || from > to) {
    throw new Error(`invalid lines [${from}, ${to}] (file has ${all.length} lines)`);
  }
  return all.slice(from - 1, to).join('\n');
}

async function main() {
  if (!existsSync(PORTAL_ROOT)) {
    console.error(`portal repo not found at ${PORTAL_ROOT}. set MOVE_PORTAL_ROOT env var.`);
    process.exit(1);
  }
  await mkdir(SNIPPETS_DIR, { recursive: true });

  if (manifest.length === 0) {
    console.log('manifest is empty — nothing to sync (expected until Phase 3).');
    return;
  }

  let ok = 0;
  let fail = 0;
  for (const entry of manifest) {
    const srcPath = join(PORTAL_ROOT, entry.source);
    try {
      const raw = await readFile(srcPath, 'utf8');
      const extracted = await extractLines(raw, entry.lines);
      const isYaml = entry.language === 'yaml';
      const cmt = isYaml ? '#' : '//';
      const header =
        [
          `${cmt} @source ${entry.source}`,
          entry.lines ? `${cmt} @lines ${entry.lines[0]}-${entry.lines[1]}` : null,
          entry.note ? `${cmt} @note ${entry.note}` : null,
          `${cmt} @synced ${new Date().toISOString()}`,
        ]
          .filter(Boolean)
          .join('\n') + '\n\n';

      const outPath = join(SNIPPETS_DIR, `${entry.id}.${entry.language}`);
      await writeFile(outPath, header + extracted + '\n');
      console.log(`  ✓ ${entry.id} (${entry.source}${entry.lines ? `:${entry.lines[0]}-${entry.lines[1]}` : ''})`);
      ok++;
    } catch (err) {
      console.error(`  ✗ ${entry.id}: ${err.message}`);
      fail++;
    }
  }
  console.log(`synced ${ok}/${manifest.length} snippets (${fail} failed).`);
  if (fail > 0) process.exit(1);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
