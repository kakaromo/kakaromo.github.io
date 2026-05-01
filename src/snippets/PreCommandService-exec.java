// @source src/main/java/com/samsung/move/head/precmd/service/PreCommandService.java
// @lines 190-250
// @note executeSync + parseCommands + resolveCommand(adb -s usbId) + extractVmName
// @synced 2026-05-01T01:05:23.624Z

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

            for (String rawCmd : commands) {
                String resolvedCmd = resolveCommand(rawCmd, usbId);
                try {
                    CommandResult result = executeSshCommand(vmName, tentacleIp, resolvedCmd);
                    if (result.exitCode != 0) break;
                } catch (Exception e) {
                    break;
                }
            }
        }
    }

    // ── 내부 메서드 ──

    private List<String> parseCommands(String json) {
        try {
            return new ObjectMapper().readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("명령어 파싱 실패: " + e.getMessage());
        }
    }

    String resolveCommand(String command, String usbId) {
        if (usbId == null || usbId.isBlank()) return command;
        Matcher m = ADB_PREFIX.matcher(command);
        if (m.find()) {
            return "adb -s " + usbId + " " + command.substring(m.end());
