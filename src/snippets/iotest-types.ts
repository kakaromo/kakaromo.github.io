// @source frontend/src/routes/agent/iotest/types.ts
// @lines 1-79
// @note IOTestConfig/Thread/Command 타입 · 재귀 commands (loop/if 중첩) · ProgressEvent · ThreadProgress
// @synced 2026-05-01T01:05:23.646Z

/** I/O Test configuration types */

export interface IOTestConfig {
	threads: IOTestThread[];
	duration_seconds: number;
	sync_start: boolean;
}

export interface IOTestThread {
	name: string;
	commands: IOTestCommand[];
}

export interface IOTestCommand {
	op: string;
	// fd name (multi-file-handle support)
	fd?: string;
	// open / unlink / stat / mkdir / truncate / sysfs_write / sysfs_read / rename
	path?: string;
	new_path?: string;  // rename target
	flags?: string;
	value?: string;
	// read / write
	offset?: string | number;
	bs?: string | number;
	count?: number;
	pattern?: string;
	// truncate
	size?: string | number;
	// create_files / delete_pattern
	dir?: string;
	prefix?: string;
	rule?: string;
	blocks?: number;
	// sleep
	ms?: number;
	// shell
	cmd?: string;
	// loop
	loop_count?: number;
	loop_duration?: number;  // seconds — duration-based loop
	commands?: IOTestCommand[];
	items?: string[];
	// if
	condition?: string;
	then?: IOTestCommand[];
	else?: IOTestCommand[];
}

/** Progress event from SSE */
export interface IOTestProgressEvent {
	thread: string;
	step: number;
	op: string;
	status: 'ok' | 'error' | 'running' | 'skipped';
	error?: string;
	duration_ns?: number;
	path?: string;
	offset?: number;
	bs?: number;
	bytes?: number;
	value?: string;
	progress?: number;
	total?: number;
	iter?: number;
	op_inner?: string;
}

/** Thread progress state for UI */
export interface ThreadProgress {
	name: string;
	totalSteps: number;
	completedSteps: number;
	currentOp: string;
	currentIter?: number;
	currentTotal?: number;
	status: 'running' | 'completed' | 'failed' | 'idle';
	percent: number;
}
