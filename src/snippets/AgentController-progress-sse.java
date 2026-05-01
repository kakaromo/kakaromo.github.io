// @source src/main/java/com/samsung/move/agent/controller/AgentController.java
// @lines 248-307
// @note GET /api/agent/benchmark/progress — gRPC stream → SSE 중계
// @synced 2026-05-01T01:05:23.614Z

    @GetMapping(value = "/benchmark/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeJobProgress(@RequestParam Long serverId, @RequestParam String jobId) {
        AgentGrpcClient client = getClient(serverId);
        SseEmitter emitter = new SseEmitter(0L); // no timeout — job이 끝날 때까지 유지
        final var completed = new java.util.concurrent.atomic.AtomicBoolean(false);

        emitter.onCompletion(() -> completed.set(true));
        emitter.onTimeout(() -> completed.set(true));
        emitter.onError(e -> completed.set(true));

        client.subscribeJobProgressAsync(jobId, new StreamObserver<>() {
            @Override
            public void onNext(JobProgress progress) {
                if (completed.get()) return;
                try {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("jobId", progress.getJobId());
                    data.put("deviceId", progress.getDeviceId());
                    data.put("state", toJobStateString(progress.getState()));
                    data.put("message", progress.getMessage());
                    data.put("progressPercent", progress.getProgressPercent());
                    data.put("error", progress.getError());
                    if (progress.getMetricsCount() > 0) {
                        data.put("metrics", progress.getMetricsMap());
                    }
                    if (!progress.getRawOutput().isEmpty()) {
                        data.put("rawOutput", progress.getRawOutput());
                    }

                    String json = objectMapper.writeValueAsString(data);
                    emitter.send(SseEmitter.event().name("progress").data(json, MediaType.APPLICATION_JSON));
                } catch (Exception e) {
                    completed.set(true);
                }
            }

            @Override
            public void onError(Throwable t) {
                if (completed.get()) return;
                try {
                    jobExecutionService.updateState(jobId, "failed", t.getMessage());
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("error", t.getMessage()), MediaType.APPLICATION_JSON));
                    emitter.complete();
                } catch (Exception ignored) {}
            }

            @Override
            public void onCompleted() {
                if (completed.get()) return;
                try {
                    jobExecutionService.updateState(jobId, "completed", null);
                    emitter.send(SseEmitter.event().name("complete").data("{}"));
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        });

        return emitter;
    }
