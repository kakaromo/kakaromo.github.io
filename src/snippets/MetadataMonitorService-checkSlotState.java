// com.samsung.move.metadata.service.MetadataMonitorService#checkSlotStateChanges
// 5초마다 전 슬롯을 스캔해서 running 진입/이탈 감지.
// 진입: startMonitoring(), 이탈: stopMonitoring(), testTool 교체: stop + start.
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
            // 새로 시작된 TC — 수집 시작
            startMonitoring(key, slot);
        } else if (wasRunning && !isRunning) {
            // TC 종료 — 수집 중단 + 최종 저장
            stopMonitoring(key, slot);
        } else if (wasRunning && isRunning) {
            // running 상태는 유지인데 testTool 이 바뀌면 재시작
            String prevTool = prev != null ? prev.testToolName() : null;
            if (currentTestTool != null && !currentTestTool.equals(prevTool)) {
                stopMonitoring(key, slot);
                startMonitoring(key, slot);
            }
        }

        previousStates.put(key, new SlotPreviousState(currentTestState, currentTestTool));
    }
}

private boolean isRunning(String testState) {
    return testState != null && testState.trim().equalsIgnoreCase("running");
}
