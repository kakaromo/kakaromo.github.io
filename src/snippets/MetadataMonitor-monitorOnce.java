// @source src/main/java/com/samsung/move/metadata/service/MetadataMonitorService.java
// @lines 290-351
// @note monitorOnce — commandType 분기 + JSON 파싱 + 인메모리 + 파일 저장
// @synced 2026-05-01T01:10:31.161Z

        }

        // 최종 수집 (lock으로 동시 실행 방지)
        try {
            monitorOnce(ctx);
        } catch (Exception e) {
            log.error("Final monitoring failed for slot [{}]: {}", slotKey, e.getMessage());
        }

        log.info("Stopped metadata monitoring for slot [{}], total entries per type: {}",
                slotKey, ctx.getMonitoredData().entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey, e -> e.getValue().size())));
    }

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
                    String template = resolvePlaceholders(cmd.getCommandTemplate(), ctx);
                    if (template == null) {
                        // placeholder 미해석 → 명령 skip (warn 은 resolvePlaceholders 에서 1회만)
                        continue;
                    }
                    String rawJson;
                    if ("sysfs".equals(cmd.getCommandType())) {
                        rawJson = commandExecutor.executeSysfsRead(
                                ctx.getTentacleName(), ctx.getSerial(), template);
                    } else if ("raw".equals(cmd.getCommandType())) {
                        rawJson = commandExecutor.executeRaw(
                                ctx.getTentacleName(), ctx.getSerial(), template);
                    } else if ("keyvalue".equals(cmd.getCommandType())) {
                        rawJson = commandExecutor.executeKeyValue(
                                ctx.getTentacleName(), ctx.getSerial(), template);
                    } else if ("table".equals(cmd.getCommandType())) {
                        rawJson = commandExecutor.executeTable(
                                ctx.getTentacleName(), ctx.getSerial(), template);
                    } else if ("bitmap".equals(cmd.getCommandType())) {
                        rawJson = commandExecutor.executeBitmap(
                                ctx.getTentacleName(), ctx.getSerial(), template);
                    } else if ("binary".equals(cmd.getCommandType())) {
                        if (cmd.getPredefinedStruct() == null) {
                            log.warn("binary command [id={}] missing predefinedStruct — skip", cmd.getId());
                            continue;
                        }
                        String outputPath = resolvePlaceholders(cmd.getBinaryOutputPath(), ctx);
                        if (outputPath == null) continue;
                        rawJson = commandExecutor.executeBinary(
                                ctx.getTentacleName(), ctx.getSerial(), template,
                                outputPath,
                                cmd.getPredefinedStruct().getId(),
                                cmd.getBinaryEndianness());
                    } else {
                        rawJson = commandExecutor.executeCommand(
                                ctx.getTentacleName(), ctx.getSerial(), template);
                    }
