// com.samsung.move.metadata.service.MetadataMonitorService#clearSlot
// HEAD 의 initslot 명령이 도착하면 호출됨 — 슬롯의 모든 metadata 상태 초기화.
// 중요: VM 디스크의 debug_*.json 파일은 건드리지 않음 (이력 보존).
public void clearSlot(String tentacleName, int slotNumber) {
    String key = tentacleName + ":" + slotNumber;
    boolean hadEnabled = enabledSlots.remove(key);        // 활성화 플래그 해제
    Set<String> removedExcluded = excludedTypes.remove(key);  // 제외 타입 목록 제거
    Integer removedInterval = slotIntervalSeconds.remove(key); // 커스텀 주기 제거

    // 진행 중 수집이 있으면 future 취소 + activeMonitors 에서 제거
    for (var entry : activeMonitors.entrySet()) {
        SlotMonitorContext ctx = entry.getValue();
        if (ctx.getTentacleName().equals(tentacleName) && ctx.getSlotNumber() == slotNumber) {
            SlotMonitorContext removed = activeMonitors.remove(entry.getKey());
            if (removed != null && removed.getFuture() != null)
                removed.getFuture().cancel(true);
            previousStates.remove(entry.getKey());   // state 기억도 제거
            break;
        }
    }

    log.info("Metadata cleared for [{}] — enabled={}, excluded={}, interval={}",
            key, hadEnabled,
            removedExcluded != null ? removedExcluded.size() : 0,
            removedInterval);
}

// 슬롯 활성화/비활성화 — 사용자 UI 토글에서 호출
public void enableSlot(String tentacleName, int slotNumber) {
    enabledSlots.add(tentacleName + ":" + slotNumber);
}

public void disableSlot(String tentacleName, int slotNumber) {
    String key = tentacleName + ":" + slotNumber;
    enabledSlots.remove(key);
    // 이미 수집 중이었다면 즉시 중단
    for (var entry : activeMonitors.entrySet()) {
        SlotMonitorContext ctx = entry.getValue();
        if (ctx.getTentacleName().equals(tentacleName) && ctx.getSlotNumber() == slotNumber) {
            SlotMonitorContext removed = activeMonitors.remove(entry.getKey());
            if (removed != null && removed.getFuture() != null)
                removed.getFuture().cancel(true);
            break;
        }
    }
}
