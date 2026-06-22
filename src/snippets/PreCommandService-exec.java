// @source src/main/java/com/samsung/move/head/precmd/service/PreCommandService.java
// @lines 190-250
// @note executeSync + parseCommands + resolveCommand(adb -s usbId) + extractVmName
// @synced 2026-06-22T22:22:10.914Z


                    // 슬롯의 모든 명령이 끝나면 결과를 슬롯 로그 디렉토리에 저장. 실패해도 로그는 남김.
                    try {
                        writeSlotLog(vmName, tentacleIp, slotIdx, slotLog.toString());
                    } catch (Exception logEx) {
                        // 로그 저장 실패는 메인 흐름을 막지 않음 — 진단용 SSE 이벤트만 전송
                        sendEvent(emitter, "log-write-failed", Map.of(
                                "slotIndex", slotIdx,
                                "error", logEx.getMessage() != null ? logEx.getMessage() : "log write failed"));
                    }

                    if (slotFailed) failed++;
                    sendEvent(emitter, "slot-done", Map.of(
                            "slotIndex", slotIdx, "slotLabel", slotLabel,
                            "status", slotFailed ? "failed" : "success"));
                    sendSummary(emitter, total, ++completed, failed, skipped);
                }

                sendEvent(emitter, "done", Map.of(
                        "total", total, "failed", failed, "skipped", skipped,
                        "success", total - failed - skipped));
                emitter.complete();

            } catch (Exception e) {
                // error handled via SSE
                try {
                    sendEvent(emitter, "error", Map.of("message", e.getMessage()));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 자동 실행용 동기 메서드 (SSE 없이). PreCommandAutoExecutor에서 호출.
     */
    public void executeSync(Long preCommandId, String source, List<Integer> slotNumbers) {
        PreCommand preCommand = findById(preCommandId);
        List<String> commands = parseCommands(preCommand.getCommands());

        List<HeadSlotData> allSlots = slotStateStore.getSlotsBySource(source);
        Map<Integer, HeadSlotData> slotMap = new HashMap<>();
        for (HeadSlotData s : allSlots) {
            slotMap.put(s.getSlotIndex(), s);
        }

        for (int slotIdx : slotNumbers) {
            HeadSlotData slotData = slotMap.get(slotIdx);
            if (slotData == null) continue;

            String usbId = slotData.getUsbId();
            String vmName = extractVmName(slotData.getSetLocation());
            String tentacleIp = slotData.getTentacleIp();
            if (vmName == null && (tentacleIp == null || tentacleIp.isBlank())) continue;

            StringBuilder slotLog = new StringBuilder();
            appendLogHeader(slotLog, preCommand, slotData.getSetLocation(), slotIdx);
            int cmdIdx = 0;
            for (String rawCmd : commands) {
