// @source src/main/java/com/samsung/move/t32/service/T32DumpService.java
// @lines 284-326
// @note Step 3 Dump — {result_path}/{branch_path} 치환 + fail 키워드 감지
// @synced 2026-04-19T09:18:51.166Z

    // ── Step 3: Dump 실행 (실시간 스트리밍) ──

    private String executeStep3Dump(SseEmitter emitter, T32Config config, PortalServer t32Pc,
                                     String tentacleName, int slotNumber,
                                     String resultWindowsPath, String branchWindowsPath) {
        try {
            String command = resolveTemplate(config.getDumpCommand(), tentacleName, slotNumber);
            if (command == null || command.isBlank()) {
                sendEvent(emitter, "step-done", Map.of("step", 3, "status", "failed", "output", "Dump 명령어가 설정되지 않았습니다"));
                return null;
            }
            // 경로에 공백이 있을 수 있으므로 ""로 감싸서 치환
            if (resultWindowsPath != null && !resultWindowsPath.isBlank()) {
                command = command.replace("{result_path}", "\"" + resultWindowsPath + "\"");
            }
            if (branchWindowsPath != null && !branchWindowsPath.isBlank()) {
                command = command.replace("{branch_path}", "\"" + branchWindowsPath + "\"");
            }

            CommandResult result = executeSshCommandWithDumpProgress(t32Pc, command, DUMP_TIMEOUT_SECONDS, emitter);

            boolean outputHasFail = result.output.toLowerCase().contains("fail");
            boolean success = result.exitCode == 0 && !outputHasFail;

            String output = result.output;
            if (outputHasFail && result.exitCode == 0) {
                output += "\n\n⚠ stdout에서 fail 감지됨";
            }

            sendEvent(emitter, "step-done", Map.of(
                    "step", 3, "status", success ? "success" : "failed", "output", output));

            // stdout에서 결과 경로 추출 시도 (마지막 줄이나 특정 패턴)
            if (success) {
                return extractResultPath(result.output);
            }
            return null;

        } catch (Exception e) {
            sendEvent(emitter, "step-done", Map.of("step", 3, "status", "failed", "output", "오류: " + e.getMessage()));
            return null;
        }
    }
