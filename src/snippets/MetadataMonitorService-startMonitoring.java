// com.samsung.move.metadata.service.MetadataMonitorService#startMonitoring
// TC running 진입 시점에 호출. 실패 분기가 여럿인데 "early return" 으로 flat 하게 유지.
private void startMonitoring(String slotKey, HeadSlotData slot) {
    if (activeMonitors.containsKey(slotKey)) return;

    // (1) 슬롯이 enabled 가 아니면 중단 (기본 OFF — 사용자가 명시적으로 켜야 수집)
    String tentName = SlotMonitorContext.parseTentacleName(slotKey, slot);
    String slotEnableKey = tentName + ":" + slot.getSlotIndex();
    if (!enabledSlots.contains(slotEnableKey)) return;

    // (2) TR 조회로 제품 정보 (controller/cellType/nandType) 확정
    String[] product = resolveProduct(slot);
    List<UfsProductMetadata> productMappings = productMetadataRepo.findByProduct(
            nz(product[0]), nz(product[1]), nz(product[2]));
    if (productMappings.isEmpty()) return;

    // (3) 여러 매핑의 typeIds 를 합쳐 enabled + non-excluded 명령만 수집
    Set<String> excluded = excludedTypes.getOrDefault(slotEnableKey, Set.of());
    Set<Long> typeIds = new LinkedHashSet<>();
    for (UfsProductMetadata pm : productMappings)
        typeIds.addAll(MetadataTypeService.parseTypeIds(pm.getMetadataTypeIds()));
    List<UfsMetadataCommand> commands = new ArrayList<>();
    for (Long typeId : typeIds) {
        for (UfsMetadataCommand cmd : commandRepo.findByMetadataTypeId(typeId)) {
            var mt = cmd.getMetadataType();
            if (mt != null && mt.isEnabled() && !excluded.contains(mt.getTypeKey()))
                commands.add(cmd);
        }
    }
    if (commands.isEmpty()) return;

    SlotMonitorContext ctx = new SlotMonitorContext(slotKey, slot, commands);

    // (4) 디바이스별 {userdata} placeholder 조회 — 1회만 (sda10 등)
    try {
        String userdataBlock = commandExecutor.resolveUserdataBlock(
                ctx.getTentacleName(), ctx.getSerial());
        if (userdataBlock != null)
            ctx.getPlaceholders().put("userdata", userdataBlock);
    } catch (Exception e) {
        log.warn("placeholder resolve failed for [{}]: {}", slotKey, e.getMessage());
    }

    // (5) debug tool push — 중복 방지 (pushedToolIds Set)
    for (UfsMetadataCommand cmd : commands) {
        DebugTool tool = cmd.getDebugTool();
        if (tool != null && ctx.getPushedToolIds().add(tool.getId())) {
            try {
                commandExecutor.pushDebugTool(ctx.getTentacleName(), ctx.getSerial(), tool);
            } catch (Exception e) { log.error("tool push failed: {}", e.getMessage()); }
        }
    }

    // (6) ScheduledExecutor 에 초 단위 주기 등록 — 즉시 첫 수집 + interval 간격
    int intervalSec = slotIntervalSeconds.getOrDefault(slotEnableKey,
            props.getCollectionIntervalMin() * 60);
    ScheduledFuture<?> future = executor.scheduleAtFixedRate(
            () -> monitorOnce(ctx), 0, intervalSec, TimeUnit.SECONDS);
    ctx.setFuture(future);

    activeMonitors.put(slotKey, ctx);
    log.info("Started metadata for [{}], {} commands, interval={}s",
            slotKey, commands.size(), intervalSec);
}
