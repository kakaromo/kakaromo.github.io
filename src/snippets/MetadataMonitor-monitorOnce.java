// @source src/main/java/com/samsung/move/metadata/service/MetadataMonitorService.java
// @lines 290-351
// @note monitorOnce — commandType 분기 + JSON 파싱 + 인메모리 + 파일 저장
// @synced 2026-04-19T09:18:51.163Z

    private void monitorOnce(SlotMonitorContext ctx) {
        // Lock으로 동시 실행 방지 (scheduled task + stopMonitoring 최종 수집)
        if (!ctx.getMonitorLock().tryLock()) {
            log.debug("monitorOnce skipped for slot [{}] — already running", ctx.getSlotKey());
            return;
        }
        try {
            for (UfsMetadataCommand cmd : ctx.getCommands()) {
                String typeKey = cmd.getMetadataType().getTypeKey();
                try {
                    String rawJson;
                    if ("sysfs".equals(cmd.getCommandType())) {
                        rawJson = commandExecutor.executeSysfsRead(
                                ctx.getTentacleName(), ctx.getSerial(), cmd.getCommandTemplate());
                    } else if ("raw".equals(cmd.getCommandType())) {
                        rawJson = commandExecutor.executeRaw(
                                ctx.getTentacleName(), ctx.getSerial(), cmd.getCommandTemplate());
                    } else if ("keyvalue".equals(cmd.getCommandType())) {
                        rawJson = commandExecutor.executeKeyValue(
                                ctx.getTentacleName(), ctx.getSerial(), cmd.getCommandTemplate());
                    } else {
                        rawJson = commandExecutor.executeCommand(
                                ctx.getTentacleName(), ctx.getSerial(), cmd.getCommandTemplate());
                    }

                    // JSON 파싱 — non-JSON 출력이면 skip
                    String trimmed = rawJson.trim();
                    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                        log.warn("Non-JSON output for slot [{}] type [{}]: {}",
                                ctx.getSlotKey(), typeKey, trimmed.substring(0, Math.min(100, trimmed.length())));
                        continue;
                    }

                    Map<String, Object> parsed = objectMapper.readValue(
                            trimmed, new TypeReference<>() {});

                    // time 필드 추가
                    parsed.put("time", ctx.getElapsedSeconds().get());

                    // 누적 (CopyOnWriteArrayList — thread-safe)
                    ctx.getMonitoredData().get(typeKey).add(parsed);

                    // 파일 저장
                    String filePath = String.format("%s/slot%d/log/debug_%s.json",
                            props.getOutputBaseDir(), ctx.getSlotNumber(), typeKey);
                    String jsonArray = objectMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(ctx.getMonitoredData().get(typeKey));
                    commandExecutor.writeJsonToFile(ctx.getTentacleName(), filePath, jsonArray);

                } catch (Exception e) {
                    log.error("Monitoring failed for slot [{}] type [{}]: {}",
                            ctx.getSlotKey(), typeKey, e.getMessage());
                }
            }
            String enableKey = ctx.getTentacleName() + ":" + ctx.getSlotNumber();
            int intervalSec = slotIntervalSeconds.getOrDefault(enableKey,
                    props.getCollectionIntervalMin() * 60);
            ctx.getElapsedSeconds().addAndGet(intervalSec);
        } finally {
            ctx.getMonitorLock().unlock();
        }
    }
