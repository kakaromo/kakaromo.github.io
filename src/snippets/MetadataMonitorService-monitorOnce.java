// com.samsung.move.metadata.service.MetadataMonitorService#monitorOnce
// ScheduledExecutor 가 주기마다 호출하는 실제 수집 로직.
// 하나의 context 에 여러 command 가 있으므로 순차 실행 — 7가지 commandType 분기.
private void monitorOnce(SlotMonitorContext ctx) {
    // 이전 태스크가 아직 실행 중이면 건너뜀 — stopMonitoring 의 최종 수집과도 겹치지 않게 lock 보호
    if (!ctx.getMonitorLock().tryLock()) return;
    try {
        for (UfsMetadataCommand cmd : ctx.getCommands()) {
            String typeKey = cmd.getMetadataType().getTypeKey();
            try {
                // {userdata} 등 placeholder 치환 (startMonitoring 에서 1회 조회한 값 사용)
                String template = resolvePlaceholders(cmd.getCommandTemplate(), ctx);
                String rawJson;

                // commandType 별 분기 — 7가지
                switch (cmd.getCommandType()) {
                    case "sysfs"    -> rawJson = commandExecutor.executeSysfsRead(ctx.getTentacleName(), ctx.getSerial(), template);
                    case "raw"      -> rawJson = commandExecutor.executeRaw(ctx.getTentacleName(), ctx.getSerial(), template);
                    case "keyvalue" -> rawJson = commandExecutor.executeKeyValue(ctx.getTentacleName(), ctx.getSerial(), template);
                    case "table"    -> rawJson = commandExecutor.executeTable(ctx.getTentacleName(), ctx.getSerial(), template);
                    case "bitmap"   -> rawJson = commandExecutor.executeBitmap(ctx.getTentacleName(), ctx.getSerial(), template);
                    case "binary"   -> {
                        if (cmd.getPredefinedStruct() == null) continue;   // 필수 struct 없으면 skip
                        String outputPath = resolvePlaceholders(cmd.getBinaryOutputPath(), ctx);
                        rawJson = commandExecutor.executeBinary(
                                ctx.getTentacleName(), ctx.getSerial(), template,
                                outputPath, cmd.getPredefinedStruct().getId(),
                                cmd.getBinaryEndianness());
                    }
                    default -> rawJson = commandExecutor.executeCommand(
                            ctx.getTentacleName(), ctx.getSerial(), template);   // "tool"
                }

                // non-JSON 출력은 skip (에러 메시지 등 잘못된 응답)
                String trimmed = rawJson.trim();
                if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) continue;

                // 시계열 entry 에 time 추가 + in-memory 누적 (CopyOnWriteArrayList — thread-safe)
                Map<String, Object> parsed = objectMapper.readValue(trimmed, new TypeReference<>() {});
                parsed.put("time", ctx.getElapsedSeconds().get());
                ctx.getMonitoredData().get(typeKey).add(parsed);

                // SFTP 로 tentacle VM 의 slot{N}/log/debug_{typeKey}.json 에 전체 배열 덮어쓰기
                String filePath = String.format("%s/slot%d/log/debug_%s.json",
                        props.getOutputBaseDir(), ctx.getSlotNumber(), typeKey);
                String jsonArray = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(ctx.getMonitoredData().get(typeKey));
                commandExecutor.writeJsonToFile(ctx.getTentacleName(), filePath, jsonArray);

            } catch (Exception e) {
                log.error("Monitoring failed [{}] [{}]: {}", ctx.getSlotKey(), typeKey, e.getMessage());
            }
        }
        // elapsedSeconds 누적 — 다음 entry 의 time 값 (차트 X축)
        int intervalSec = slotIntervalSeconds.getOrDefault(
                ctx.getTentacleName() + ":" + ctx.getSlotNumber(),
                props.getCollectionIntervalMin() * 60);
        ctx.getElapsedSeconds().addAndGet(intervalSec);
    } finally {
        ctx.getMonitorLock().unlock();
    }
}
