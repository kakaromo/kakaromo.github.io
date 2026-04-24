// com.samsung.move.metadata.controller.MetadataController
// 사용자 REST API — 15개 엔드포인트. 대부분 slot key(tentacleName:slotNumber) 기준.
@RestController
@RequestMapping("/api/metadata")
@RequiredArgsConstructor
public class MetadataController {

    private final MetadataTypeService typeService;
    private final MetadataMonitorService monitorService;
    private final MetadataCommandExecutor commandExecutor;
    private final MetadataMonitorProperties props;

    // 타입 조회 3종 — 전체 / 제품 기준 / TR 기준
    @GetMapping("/types")
    public List<UfsMetadataType> getEnabledTypes() { return typeService.findEnabledTypes(); }

    @GetMapping("/types/for-product")
    public List<UfsMetadataType> getTypesForProduct(
            @RequestParam(required = false) String controller,
            @RequestParam(required = false) String nandType,
            @RequestParam(required = false) String cellType) {
        return typeService.findTypesForProduct(controller, cellType, nandType);
    }

    @GetMapping("/types/for-tr")
    public List<UfsMetadataType> getTypesForTr(
            @RequestParam Long trId, @RequestParam int headType) {
        return typeService.findTypesForTr(trId, headType);
    }

    // 수집 상태 — 프론트가 2~5초 주기로 poll
    @GetMapping("/slot/{tentacleName}/{slotNumber}")
    public ResponseEntity<Map<String, Object>> getSlotStatus(
            @PathVariable String tentacleName, @PathVariable int slotNumber) {
        SlotMonitorContext ctx = findContextBySlot(tentacleName, slotNumber);
        if (ctx == null) return ResponseEntity.ok(Map.of("monitoring", false));

        return ResponseEntity.ok(Map.of(
                "monitoring", true,
                "testToolName", ctx.getCurrentTestToolName(),
                "startTimeMs", ctx.getStartTimeMs(),
                "elapsedSeconds", ctx.getElapsedSecondsValue(),
                "types", ctx.getMonitoredData().keySet(),
                "entryCounts", ctx.getMonitoredData().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size()))));
    }

    // in-memory 시계열 데이터 — 차트 갱신용 (1초 polling 대상)
    @GetMapping("/slot/{tentacleName}/{slotNumber}/{typeKey}")
    public ResponseEntity<List<Map<String, Object>>> getSlotMetadata(
            @PathVariable String tentacleName, @PathVariable int slotNumber,
            @PathVariable String typeKey) { /* ... */ }

    // VM SFTP 파일 직접 읽기 — 종료된 TC history 조회
    @GetMapping("/file")
    public ResponseEntity<String> readFile(
            @RequestParam String tentacleName, @RequestParam String path,
            @RequestParam(required = false) String tentacleIp) { /* ... */ }

    // 슬롯 활성화 토글 — 사용자 UI 의 "metadata 수집 on" 스위치
    @PutMapping("/slot/{tentacleName}/{slotNumber}/enabled")
    public ResponseEntity<Map<String, Boolean>> toggleSlotCollection(
            @PathVariable String tentacleName, @PathVariable int slotNumber,
            @RequestParam boolean enabled) {
        if (enabled) monitorService.enableSlot(tentacleName, slotNumber);
        else monitorService.disableSlot(tentacleName, slotNumber);
        return ResponseEntity.ok(Map.of("enabled", enabled));
    }

    // 제외 타입 설정 — 특정 typeKey 만 끄기 (예: bitmap 은 용량 크니 제외)
    @PutMapping("/slot/{tentacleName}/{slotNumber}/excluded-types")
    public ResponseEntity<Set<String>> setExcludedTypes(
            @PathVariable String tentacleName, @PathVariable int slotNumber,
            @RequestBody Set<String> excludedTypes) {
        monitorService.setExcludedTypes(tentacleName, slotNumber, excludedTypes);
        return ResponseEntity.ok(excludedTypes);
    }

    // 슬롯별 주기 (초) — 최소 10초. 기본은 전역 collectionIntervalMin * 60
    @PutMapping("/slot/{tentacleName}/{slotNumber}/interval")
    public ResponseEntity<Map<String, Integer>> setSlotInterval(
            @PathVariable String tentacleName, @PathVariable int slotNumber,
            @RequestParam int intervalSeconds) {
        int clamped = Math.max(10, intervalSeconds);
        monitorService.setSlotIntervalSeconds(tentacleName, slotNumber, clamped);
        return ResponseEntity.ok(Map.of("intervalSeconds", clamped));
    }
}
