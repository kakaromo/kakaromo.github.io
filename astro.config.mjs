// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import remarkMermaidjs from 'remark-mermaidjs';

export default defineConfig({
  site: 'https://kakaromo.github.io',
  markdown: {
    remarkPlugins: [remarkMermaidjs],
  },
  integrations: [
    starlight({
      title: 'Portal Docs',
      defaultLocale: 'root',
      locales: {
        root: { label: '한국어', lang: 'ko-KR' },
      },
      customCss: ['./src/styles/custom.css'],
      head: [
        {
          tag: 'script',
          content: `
(function(){
  var KEY = 'portal_docs_auth';
  var HASH = '79a04eeefd2f97d1695c4d86cb0624715eff182b35deda3b5c71e4f431f5390d';
  if (sessionStorage.getItem(KEY) === HASH) return;
  async function sha256(msg) {
    var buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(msg));
    return Array.from(new Uint8Array(buf)).map(function(b){return b.toString(16).padStart(2,'0')}).join('');
  }
  document.documentElement.style.display = 'none';
  var pw = prompt('Portal Docs 비밀번호를 입력하세요');
  if (!pw) { document.body.innerHTML = '<h2 style="text-align:center;margin-top:20vh">접근이 거부되었습니다</h2>'; document.documentElement.style.display=''; return; }
  sha256(pw).then(function(h){
    if (h === HASH) { sessionStorage.setItem(KEY, HASH); document.documentElement.style.display=''; }
    else { document.body.innerHTML = '<h2 style="text-align:center;margin-top:20vh">비밀번호가 틀렸습니다</h2>'; document.documentElement.style.display=''; }
  });
})();
`,
        },
      ],
      sidebar: [
        {
          label: '시작하기',
          items: [
            { label: '소개', slug: 'getting-started/overview' },
            { label: '설치 및 실행', slug: 'getting-started/installation' },
            { label: '프로젝트 구조', slug: 'getting-started/project-structure' },
          ],
        },
        {
          label: '사용 가이드',
          items: [
            { label: '대시보드', slug: 'guide/dashboard' },
            {
              label: '테스트 관리',
              items: [
                { label: '성능 테스트', slug: 'guide/performance-testing' },
                { label: '호환성 테스트', slug: 'guide/compatibility-testing' },
                { label: '성능 비교', slug: 'guide/performance-compare' },
                { label: 'TC 그룹', slug: 'guide/tc-groups' },
                { label: 'Excel 내보내기', slug: 'guide/excel-export' },
              ],
            },
            {
              label: '슬롯 & 모니터링',
              items: [
                { label: '실시간 슬롯 모니터링', slug: 'guide/slot-monitoring' },
                { label: 'DLM 디버그', slug: 'guide/dlm-debug' },
                { label: 'UFS Metadata 모니터링', slug: 'guide/ufs-metadata' },
                { label: 'UFS 참조 데이터', slug: 'guide/ufs-info' },
              ],
            },
            {
              label: '원격 접속 & 로그',
              items: [
                { label: '원격 터미널 (SSH/RDP)', slug: 'guide/remote-terminal' },
                { label: '로그 브라우저', slug: 'guide/log-browser' },
                { label: '파일 스토리지', slug: 'guide/file-storage' },
              ],
            },
            {
              label: 'Agent (디바이스 평가)',
              items: [
                { label: 'Agent 사용 가이드', slug: 'guide/agent' },
              ],
            },
            { label: 'Bitbucket 브랜치 모니터', slug: 'guide/bitbucket-monitor' },
            { label: 'T32 Dump (JTAG 디버깅)', slug: 'guide/t32-dump' },
            { label: 'TC별 Pre-Command', slug: 'guide/tc-pre-command' },
            { label: 'Binary Struct Mapper', slug: 'guide/bin-mapper' },
            { label: '관리자 대시보드', slug: 'guide/admin' },
          ],
        },
        {
          label: '아키텍처',
          items: [
            { label: '시스템 개요', slug: 'architecture/system-overview' },
            { label: '기술 스택', slug: 'architecture/tech-stack' },
            { label: '데이터베이스', slug: 'architecture/database' },
            { label: '프론트엔드', slug: 'architecture/frontend' },
            { label: '인증 (OAuth2)', slug: 'architecture/authentication' },
            { label: 'Redis 캐시', slug: 'architecture/caching' },
            { label: 'Head TCP 프로토콜', slug: 'architecture/head-protocol' },
            { label: 'Guacamole 통합', slug: 'architecture/guacamole' },
            { label: 'Excel Export 설계', slug: 'architecture/excel-export' },
            { label: 'MinIO 스토리지', slug: 'architecture/minio' },
            { label: 'Agent 아키텍처', slug: 'architecture/agent' },
            { label: 'UFS Metadata 설계', slug: 'architecture/ufs-metadata' },
            { label: 'Bitbucket 모니터', slug: 'architecture/bitbucket-monitor' },
            { label: 'T32 Dump', slug: 'architecture/t32-dump' },
            { label: 'BinMapper 엔진', slug: 'architecture/bin-mapper' },
          ],
        },
        {
          label: 'API 레퍼런스',
          items: [
            { label: '개요', slug: 'api/overview' },
            { label: 'TestDB API', slug: 'api/testdb' },
            { label: 'Head & Slots API', slug: 'api/head' },
            { label: 'Log Browser API', slug: 'api/log-browser' },
            { label: 'MinIO API', slug: 'api/minio' },
            { label: 'BinMapper API', slug: 'api/binmapper' },
            { label: 'Debug API', slug: 'api/debug' },
            { label: 'Metadata API', slug: 'api/metadata' },
            { label: 'Guacamole API', slug: 'api/guacamole' },
            { label: 'UFSInfo API', slug: 'api/ufsinfo' },
            { label: 'Agent API', slug: 'api/agent' },
            { label: 'Bitbucket API', slug: 'api/bitbucket' },
            { label: 'T32 API', slug: 'api/t32' },
            { label: 'Auth & Admin API', slug: 'api/auth-admin' },
          ],
        },
        {
          label: '내부 동작',
          items: [
            { label: '요청의 생명주기', slug: 'internals/request-lifecycle' },
            { label: '슬롯 모니터링 흐름', slug: 'internals/slot-monitoring-flow' },
            { label: '벤치마크 실행 흐름', slug: 'internals/benchmark-execution-flow' },
            { label: 'Excel Export 흐름', slug: 'internals/excel-export-flow' },
            { label: '원격 접속 흐름', slug: 'internals/remote-session-flow' },
          ],
        },
        {
          label: '개발자 가이드',
          items: [
            { label: '개발 환경 설정', slug: 'developer/setup' },
            { label: '코딩 컨벤션', slug: 'developer/conventions' },
            { label: '컴포넌트 가이드', slug: 'developer/components' },
            { label: 'DataTable 가이드', slug: 'developer/datatable-guide' },
            { label: 'UI 크기 조절', slug: 'developer/ui-sizing' },
            {
              label: '확장 가이드',
              items: [
                { label: '새 파서 추가하기', slug: 'developer/adding-parser' },
                { label: 'Excel Generator 추가', slug: 'developer/adding-excel-generator' },
                { label: '새 API 추가하기', slug: 'developer/adding-api-endpoint' },
                { label: '새 gRPC RPC 추가하기', slug: 'developer/adding-grpc-rpc' },
                { label: '새 페이지 추가하기', slug: 'developer/adding-frontend-route' },
                { label: '새 SSE/WS 엔드포인트', slug: 'developer/adding-sse-endpoint' },
                { label: '새 데이터소스 추가하기', slug: 'developer/adding-datasource' },
              ],
            },
            { label: '문제 해결', slug: 'developer/troubleshooting' },
          ],
        },
        {
          label: '레퍼런스',
          items: [
            { label: '용어집', slug: 'reference/glossary' },
            { label: '인프라 구성', slug: 'reference/infrastructure' },
            { label: '변경 이력', slug: 'reference/changelog' },
          ],
        },
      ],
    }),
  ],
});
