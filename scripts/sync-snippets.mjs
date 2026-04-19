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
