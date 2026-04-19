// @source frontend/src/routes/agent/iotest/opDefs.ts
// @lines 1-75
// @note OpDef 인터페이스 + 20+ op 등록 — open/close/read/write 필드 스펙 (fd · offset 템플릿 · pattern)
// @synced 2026-04-19T09:49:20.709Z

/** Operation definitions — fields, defaults, help text */

export interface OpFieldDef {
	key: string;
	label: string;
	type: 'input' | 'select' | 'textarea';
	defaultValue: string;
	choices?: string[];
	help?: string;
	placeholder?: string;
}

export interface OpDef {
	label: string;
	category: 'I/O' | 'File' | 'Control' | 'Device' | 'Flow';
	color: string;       // tailwind bg color
	fields: OpFieldDef[];
	help: string;
}

export const OP_DEFS: Record<string, OpDef> = {
	open: {
		label: 'Open',
		category: 'I/O',
		color: 'bg-blue-100',
		help: '파일 열기. fd 이름을 지정하면 여러 파일을 동시에 열 수 있음',
		fields: [
			{ key: 'path', label: 'Path', type: 'input', defaultValue: '/data/local/tmp/test/file1', placeholder: '/data/local/tmp/test/file1' },
			{ key: 'fd', label: 'FD Name', type: 'input', defaultValue: '', help: '비워두면 파일명 사용. 여러 파일: A, B 등으로 구분' },
			{
				key: 'flags', label: 'Flags', type: 'select', defaultValue: 'O_RDWR|O_CREATE',
				choices: [
					'O_RDONLY', 'O_WRONLY', 'O_RDWR',
					'O_WRONLY|O_CREATE', 'O_WRONLY|O_CREATE|O_TRUNC',
					'O_RDWR|O_CREATE', 'O_WRONLY|O_CREATE|O_DIRECT',
					'O_RDONLY|O_DIRECT', 'O_RDWR|O_DIRECT',
					'O_WRONLY|O_SYNC', 'O_WRONLY|O_APPEND'
				]
			}
		]
	},
	close: {
		label: 'Close',
		category: 'I/O',
		color: 'bg-blue-100',
		help: '파일 닫기. fd 이름으로 특정 파일 지정 가능',
		fields: [
			{ key: 'fd', label: 'FD Name', type: 'input', defaultValue: '', help: '비워두면 마지막 열린 파일' }
		]
	},
	read: {
		label: 'Read',
		category: 'I/O',
		color: 'bg-green-100',
		help: 'pread() — offset 지정 읽기',
		fields: [
			{ key: 'fd', label: 'FD Name', type: 'input', defaultValue: '', help: '비워두면 마지막 열린 파일' },
			{ key: 'offset', label: 'Offset', type: 'input', defaultValue: '0', help: '4k/1m, {{i*4096}}, random:0-1m, seq:0,4k,8k' },
			{ key: 'bs', label: 'Block Size', type: 'input', defaultValue: '4k', help: '4k, random:4k,8k,16k,64k, seq:4k,8k,16k' },
			{ key: 'count', label: 'Count', type: 'input', defaultValue: '1', help: '반복 횟수' }
		]
	},
	write: {
		label: 'Write',
		category: 'I/O',
		color: 'bg-amber-100',
		help: 'pwrite() — offset 지정 쓰기',
		fields: [
			{ key: 'fd', label: 'FD Name', type: 'input', defaultValue: '', help: '비워두면 마지막 열린 파일' },
			{ key: 'offset', label: 'Offset', type: 'input', defaultValue: '0', help: '4k/1m, {{i*4096}}, random:0-1m, seq:0,4k,8k' },
			{ key: 'bs', label: 'Block Size', type: 'input', defaultValue: '4k', help: '4k, random:4k,8k,16k,64k, seq:4k,8k,16k' },
			{ key: 'count', label: 'Count', type: 'input', defaultValue: '1' },
			{ key: 'pattern', label: 'Pattern', type: 'select', defaultValue: 'zero', choices: ['zero', 'random', 'byte:0xFF', 'byte:0x55', 'byte:0xAA'] }
		]
	},
