// @source src/main/java/com/samsung/move/t32/service/T32DumpService.java
// @lines 222-282
// @note Step 1 JTAG (success pattern regex) + Step 2 Attach (Down 감지)
// @synced 2026-05-01T01:05:23.622Z

    // ── Step 1: JTAG 연결 ──

    private boolean executeStep1Jtag(SseEmitter emitter, T32Config config, PortalServer jtagServer,
                                     String tentacleName, int slotNumber) {
        try {
            String command = resolveTemplate(config.getJtagCommand(), tentacleName, slotNumber);
            if (command == null || command.isBlank()) {
                sendEvent(emitter, "step-done", Map.of("step", 1, "status", "failed", "output", "JTAG 명령어가 설정되지 않았습니다"));
                return false;
            }

            CommandResult result = executeSshCommand(jtagServer, command, JTAG_TIMEOUT_SECONDS, emitter, 1);

            String pattern = config.getJtagSuccessPattern();
            boolean success;
            if (pattern != null && !pattern.isBlank()) {
                success = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(result.output).find();
            } else {
                success = result.exitCode == 0;
            }

            sendEvent(emitter, "step-done", Map.of(
                    "step", 1, "status", success ? "success" : "failed", "output", result.output));
            return success;

        } catch (Exception e) {
            sendEvent(emitter, "step-done", Map.of("step", 1, "status", "failed", "output", "오류: " + e.getMessage()));
            return false;
        }
    }

    // ── Step 2: T32 Attach ──

    private boolean executeStep2Attach(SseEmitter emitter, T32Config config, PortalServer t32Pc) {
        try {
            String command = config.getT32PortCheckCommand();
            if (command == null || command.isBlank()) {
                sendEvent(emitter, "step-done", Map.of("step", 2, "status", "failed", "output", "T32 Attach 명령어가 설정되지 않았습니다"));
                return false;
            }

            CommandResult result = executeSshCommand(t32Pc, command, ATTACH_TIMEOUT_SECONDS, emitter, 2);

            // "Down"이 포함되면 Debug Port Fail
            boolean debugPortFail = result.output.toLowerCase().contains("down");
            boolean success = result.exitCode == 0 && !debugPortFail;

            String statusMsg = success ? "success" : "failed";
            String output = result.output;
            if (debugPortFail) {
                output += "\n\n⚠ Debug Port Fail: JTAG 연결 상태를 확인하세요";
            }

            sendEvent(emitter, "step-done", Map.of("step", 2, "status", statusMsg, "output", output));
            return success;

        } catch (Exception e) {
            sendEvent(emitter, "step-done", Map.of("step", 2, "status", "failed", "output", "오류: " + e.getMessage()));
            return false;
        }
    }
