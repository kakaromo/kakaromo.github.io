#!/usr/bin/env node
// 학습 가이드 주요 페이지의 라이트/다크 스크린샷을 `tests/visual/`에 생성한다.
// 로컬 dev 서버가 4321에서 떠 있어야 하며, 사전에 비밀번호 해시를 sessionStorage에 심어 보호 스크립트를 우회한다.
//
// 사용:
//   npm run dev &                     # 4321 기동
//   MOVE_DOCS_PW=<password> node scripts/visual-snapshots.mjs
//
// 환경변수 MOVE_DOCS_PW 미설정 시 비밀번호 프롬프트로 막혀 빈 페이지가 캡처되므로 실패 처리.

import { chromium } from 'playwright';
import { createHash } from 'node:crypto';
import { mkdir } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const OUT_DIR = resolve(__dirname, '..', 'tests', 'visual');
const BASE_URL = process.env.MOVE_DOCS_BASE ?? 'http://localhost:4321';

const PASSWORD = process.env.MOVE_DOCS_PW;
if (!PASSWORD) {
  console.error('❌ MOVE_DOCS_PW 환경변수가 필요합니다 (docs 비밀번호 보호 우회용).');
  process.exit(1);
}
const HASH = createHash('sha256').update(PASSWORD).digest('hex');

const PAGES = [
  { path: '/learn/', label: 'learn-index' },
  { path: '/learn/l1-overview/architecture/', label: 'l1-architecture' },
  { path: '/learn/l2-slots/', label: 'l2-slots-index' },
  { path: '/learn/l2-slots/03-tcp-client/', label: 'l2-03-tcp-client' },
  { path: '/learn/l2-slots/07-frontend-store/', label: 'l2-07-frontend-store' },
  { path: '/learn/playground/', label: 'playground' },
];

async function snap(browser, page, theme) {
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    colorScheme: theme,
  });
  const pg = await context.newPage();
  // 보호 스크립트 우회
  await pg.addInitScript(
    ({ hash }) => {
      try {
        sessionStorage.setItem('portal_docs_auth', hash);
      } catch {}
    },
    { hash: HASH },
  );
  // 테마 강제 (data-theme 속성)
  await pg.addInitScript((t) => {
    document.documentElement.setAttribute('data-theme', t);
    try {
      localStorage.setItem('starlight-theme', t);
    } catch {}
  }, theme);

  const url = `${BASE_URL}${page.path}`;
  const outPath = resolve(OUT_DIR, `${page.label}--${theme}.png`);
  console.log(`  → ${page.path}  (${theme})`);
  await pg.goto(url, { waitUntil: 'networkidle', timeout: 30000 });
  // Svelte client:visible 컴포넌트 하이드레이션 대기
  await pg.waitForTimeout(800);
  await pg.screenshot({ path: outPath, fullPage: true });
  await context.close();
  return outPath;
}

async function main() {
  await mkdir(OUT_DIR, { recursive: true });
  const browser = await chromium.launch();
  try {
    for (const page of PAGES) {
      await snap(browser, page, 'light');
      await snap(browser, page, 'dark');
    }
    console.log(`✓ ${PAGES.length * 2} 스크린샷을 ${OUT_DIR}에 저장했습니다.`);
  } finally {
    await browser.close();
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
