// @source frontend/src/routes/agent/iotest/presets.ts
// @lines 1-68
// @note IOTEST_PRESETS 배열 + 3 예시 (Offset Write · Offset R/W · Misaligned R/W) — {{i}} 템플릿 사용
// @synced 2026-05-01T01:10:31.189Z

/** Preset command sequences for common I/O test patterns */

import type { IOTestThread } from './types.js';

export type IOTestPresetCategory = 'Basic I/O' | 'Random/Stress' | 'Data Integrity' | 'File Management' | 'Concurrent' | 'Device Control';

export interface IOTestPreset {
	id: string;
	label: string;
	description: string;
	category: IOTestPresetCategory;
	threads: IOTestThread[];
}

export const IOTEST_PRESETS: IOTestPreset[] = [
	{
		id: 'offset_write',
		label: 'Offset Write',
		description: 'offset 0부터 4k씩 이동하며 순차 쓰기',
		category: 'Basic I/O',
		threads: [{
			name: 'offset_writer',
			commands: [
				{ op: 'open', path: '/data/local/tmp/test/file1', flags: 'O_WRONLY|O_CREATE|O_TRUNC' },
				{ op: 'loop', loop_count: 256, commands: [
					{ op: 'write', offset: '{{i*4096}}', bs: '4k', count: 1, pattern: 'zero' }
				]},
				{ op: 'fsync' },
				{ op: 'close' }
			]
		}]
	},
	{
		id: 'offset_read_write',
		label: 'Offset R/W',
		description: 'offset 이동하며 write 후 같은 offset에서 read 검증',
		category: 'Basic I/O',
		threads: [{
			name: 'offset_rw',
			commands: [
				{ op: 'open', path: '/data/local/tmp/test/file1', flags: 'O_RDWR|O_CREATE|O_TRUNC' },
				{ op: 'loop', loop_count: 100, commands: [
					{ op: 'write', offset: '{{i*4096}}', bs: '4k', count: 1, pattern: 'random' },
					{ op: 'read', offset: '{{i*4096}}', bs: '4k', count: 1 }
				]},
				{ op: 'close' }
			]
		}]
	},
	{
		id: 'misalign_rw',
		label: 'Misaligned R/W',
		description: '비정렬 offset(512B 단위)에서 read/write',
		category: 'Basic I/O',
		threads: [{
			name: 'misalign',
			commands: [
				{ op: 'open', path: '/data/local/tmp/test/file1', flags: 'O_RDWR|O_CREATE|O_TRUNC' },
				{ op: 'loop', loop_count: 200, commands: [
					{ op: 'write', offset: '{{i*512}}', bs: '512', count: 1, pattern: 'random' }
				]},
				{ op: 'loop', loop_count: 200, commands: [
					{ op: 'read', offset: '{{i*512}}', bs: '512', count: 1 }
				]},
				{ op: 'close' }
			]
		}]
	},
