// @source src/main/java/com/samsung/move/agent/controller/AgentController.java
// @lines 1064-1100
// @note macro/installed-apps + start-recording + stop-recording — Go Agent gRPC 프록시
// @synced 2026-05-01T01:10:31.193Z

    // ══════════════════════════════════════════════════════════════
    // App Macro Recording / Replay / OCR (gRPC → Go Agent)
    // ══════════════════════════════════════════════════════════════

    @GetMapping("/macro/installed-apps")
    public List<Map<String, String>> listInstalledApps(@RequestParam Long serverId, @RequestParam String deviceId) {
        AgentGrpcClient client = getClient(serverId);
        ListInstalledAppsResponse response = client.listInstalledApps(
                ListInstalledAppsRequest.newBuilder().setDeviceId(deviceId).build());
        return response.getAppsList().stream().map(app -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("packageName", app.getPackageName());
            m.put("appName", app.getAppName());
            return m;
        }).toList();
    }

    @PostMapping("/macro/start-recording")
    public Map<String, Object> startRecording(@RequestParam Long serverId, @RequestBody Map<String, Object> body) {
        AgentGrpcClient client = getClient(serverId);
        StartRecordingResponse response = client.startRecording(
                StartRecordingRequest.newBuilder()
                        .setDeviceId((String) body.get("deviceId"))
                        .build());
        return Map.of("success", response.getSuccess(), "sessionId", response.getSessionId());
    }

    @PostMapping("/macro/stop-recording")
    public Map<String, Object> stopRecording(@RequestParam Long serverId, @RequestBody Map<String, Object> body) {
        AgentGrpcClient client = getClient(serverId);
        StopRecordingResponse response = client.stopRecording(
                StopRecordingRequest.newBuilder()
                        .setDeviceId((String) body.get("deviceId"))
                        .setSessionId((String) body.get("sessionId"))
                        .build());

        Map<String, Object> result = new LinkedHashMap<>();
