// @source src/main/java/com/samsung/move/metadata/service/MetadataMonitorService.java
// @lines 134-234
// @note @Scheduled checkSlotStateChanges + startMonitoring (TR 기반 제품→command 매핑 + 수집 주기)
// @synced 2026-04-19T09:04:03.496Z

    @Scheduled(fixedDelayString = "${metadata.monitor.poll-interval-ms:5000}")
    public void checkSlotStateChanges() {
        if (!props.isEnabled()) return;

        for (HeadSlotData slot : stateStore.getAllSlots()) {
            String key = slot.getSource() + ":" + slot.getSlotIndex();
            SlotPreviousState prev = previousStates.get(key);
            String currentTestState = slot.getTestState();
            String currentTestTool = slot.getTestToolName();

            boolean wasRunning = prev != null && isRunning(prev.testState());
            boolean isRunning = isRunning(currentTestState);

            if (!wasRunning && isRunning) {
                startMonitoring(key, slot);
            } else if (wasRunning && !isRunning) {
                stopMonitoring(key, slot);
            } else if (wasRunning && isRunning) {
                String prevTool = prev != null ? prev.testToolName() : null;
                if (currentTestTool != null && !currentTestTool.equals(prevTool)) {
                    stopMonitoring(key, slot);
                    startMonitoring(key, slot);
                }
            }

            previousStates.put(key, new SlotPreviousState(currentTestState, currentTestTool));
        }
    }

    private void startMonitoring(String slotKey, HeadSlotData slot) {
        if (activeMonitors.containsKey(slotKey)) {
            return;
        }

        // 슬롯별 활성화 체크 (기본값 OFF)
        String tentName = SlotMonitorContext.parseTentacleName(slotKey, slot);
        String slotEnableKey = tentName + ":" + slot.getSlotIndex();
        if (!enabledSlots.contains(slotEnableKey)) {
            return;
        }

        // testTrId(SlotInfomation에서 merge됨) → TR 조회 → controller/cellType/nandType 사용.
        // TR 조회 실패 시 slot.controller/nandType/cellType fallback.
        // null/공백은 빈 문자열로 정규화 — UNIQUE 제약이 있는 DB와 일관된 비교를 위해.
        String[] product = resolveProduct(slot);
        String mctrl = nz(product[0]);
        String mcell = nz(product[1]);
        String mnand = nz(product[2]);

        List<UfsProductMetadata> productMappings = productMetadataRepo.findByProduct(mctrl, mcell, mnand);
        if (productMappings.isEmpty()) {
            return;
        }

        Set<String> excluded = excludedTypes.getOrDefault(slotEnableKey, Set.of());
        // product mapping들에 걸린 type id들을 모두 모아서 enabled + non-excluded 필터 후 command 수집
        Set<Long> typeIds = new java.util.LinkedHashSet<>();
        for (UfsProductMetadata pm : productMappings) {
            typeIds.addAll(MetadataTypeService.parseTypeIds(pm.getMetadataTypeIds()));
        }
        List<UfsMetadataCommand> commands = new ArrayList<>();
        for (Long typeId : typeIds) {
            List<UfsMetadataCommand> typeCommands = commandRepo.findByMetadataTypeId(typeId);
            for (UfsMetadataCommand cmd : typeCommands) {
                var mt = cmd.getMetadataType();
                if (mt != null && mt.isEnabled() && !excluded.contains(mt.getTypeKey())) {
                    commands.add(cmd);
                }
            }
        }

        if (commands.isEmpty()) {
            log.debug("No commands configured for slot [{}]", slotKey);
            return;
        }

        SlotMonitorContext ctx = new SlotMonitorContext(slotKey, slot, commands);

        // Debug tool push (중복 방지)
        for (UfsMetadataCommand cmd : commands) {
            DebugTool tool = cmd.getDebugTool();
            if (tool != null && ctx.getPushedToolIds().add(tool.getId())) {
                try {
                    commandExecutor.pushDebugTool(ctx.getTentacleName(), ctx.getSerial(), tool);
                } catch (Exception e) {
                    log.error("Failed to push tool for slot [{}]: {}", slotKey, e.getMessage());
                }
            }
        }

        // 즉시 첫 수집 + 주기적 수집 스케줄
        int intervalSec = slotIntervalSeconds.getOrDefault(slotEnableKey,
                props.getCollectionIntervalMin() * 60);
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(
                () -> monitorOnce(ctx), 0, intervalSec, TimeUnit.SECONDS);
        ctx.setFuture(future);

        activeMonitors.put(slotKey, ctx);
        log.info("Started metadata monitoring for slot [{}], {} commands, interval={}s",
                slotKey, commands.size(), intervalSec);
    }
