// @source frontend/src/lib/api/agent.ts
// @lines 180-200
// @note runBenchmark / getJobStatus 프론트 API
// @synced 2026-04-19T06:47:47.012Z

}

// ── Benchmark ──

export function runBenchmark(serverId: number, data: {
	deviceIds: string[];
	tool: string;
	params: Record<string, string>;
	jobName?: string;
	busyPolicy?: string;
}): Promise<{ jobId: string }> {
	return post(`/agent/benchmark/run?serverId=${serverId}`, data);
}

export function getJobStatus(serverId: number, jobId: string): Promise<JobStatus> {
	return get(`/agent/benchmark/status?serverId=${serverId}&jobId=${encodeURIComponent(jobId)}`);
}

export function getBenchmarkResult(serverId: number, jobId: string, deviceId?: string): Promise<BenchmarkResult> {
	let url = `/agent/benchmark/result?serverId=${serverId}&jobId=${encodeURIComponent(jobId)}`;
	if (deviceId) url += `&deviceId=${encodeURIComponent(deviceId)}`;
