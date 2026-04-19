// @source src/main/java/com/samsung/move/agent/controller/AgentController.java
// @lines 178-218
// @note POST /api/agent/benchmark/run → gRPC RunBenchmark + JobExecution 저장
// @synced 2026-04-19T07:03:50.663Z

    @PostMapping("/benchmark/run")
    public Map<String, Object> runBenchmark(@RequestParam Long serverId, @RequestBody Map<String, Object> body) {
        AgentGrpcClient client = getClient(serverId);

        @SuppressWarnings("unchecked")
        List<String> deviceIds = (List<String>) body.get("deviceIds");
        String toolStr = (String) body.get("tool");
        @SuppressWarnings("unchecked")
        Map<String, String> params = (Map<String, String>) body.get("params");
        String jobName = (String) body.getOrDefault("jobName", "");

        BenchmarkTool tool = parseBenchmarkTool(toolStr);
        RunBenchmarkRequest request = RunBenchmarkRequest.newBuilder()
                .addAllDeviceIds(deviceIds)
                .setTool(tool)
                .putAllParams(params != null ? params : Map.of())
                .setJobName(jobName)
                .setBusyPolicy((String) body.getOrDefault("busyPolicy", "reject"))
                .build();

        RunBenchmarkResponse response = client.runBenchmark(request);

        // 이력 저장
        try {
            AgentServer server = serverService.findById(serverId);
            jobExecutionService.save(JobExecution.builder()
                    .jobId(response.getJobId())
                    .serverId(serverId)
                    .serverName(server.getName())
                    .type("benchmark")
                    .tool(toolStr)
                    .jobName(jobName)
                    .deviceIds(objectMapper.writeValueAsString(deviceIds))
                    .config(objectMapper.writeValueAsString(body))
                    .build());
        } catch (Exception e) {
            log.warn("Failed to save job execution history: {}", e.getMessage());
        }

        return Map.of("jobId", response.getJobId());
    }
