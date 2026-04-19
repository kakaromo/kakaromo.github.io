// @source src/main/java/com/samsung/move/metadata/controller/MetadataController.java
// @lines 18-91
// @note REST API — types / for-tr / slot 상태 / slot 데이터
// @synced 2026-04-19T08:33:48.677Z

@RestController
@RequestMapping("/api/metadata")
@RequiredArgsConstructor
public class MetadataController {

    private final MetadataTypeService typeService;
    private final MetadataMonitorService monitorService;
    private final MetadataCommandExecutor commandExecutor;
    private final MetadataMonitorProperties props;

    /** 활성 metadata 타입 목록 */
    @GetMapping("/types")
    public List<UfsMetadataType> getEnabledTypes() {
        return typeService.findEnabledTypes();
    }

    /** 특정 제품에 지원되는 metadata 타입 */
    @GetMapping("/types/for-product")
    public List<UfsMetadataType> getTypesForProduct(
            @RequestParam(required = false) String controller,
            @RequestParam(required = false) String nandType,
            @RequestParam(required = false) String cellType) {
        return typeService.findTypesForProduct(controller, cellType, nandType);
    }

    /**
     * TR id 기반으로 해당 제품의 지원 metadata 타입 조회.
     * headType: 0=compatibility, 1=performance
     */
    @GetMapping("/types/for-tr")
    public List<UfsMetadataType> getTypesForTr(
            @RequestParam Long trId,
            @RequestParam int headType) {
        return typeService.findTypesForTr(trId, headType);
    }

    /** 현재 수집 중인 slot의 metadata 상태 */
    @GetMapping("/slot/{tentacleName}/{slotNumber}")
    public ResponseEntity<Map<String, Object>> getSlotStatus(
            @PathVariable String tentacleName,
            @PathVariable int slotNumber) {
        // slot key 찾기 (source:slotIndex)
        SlotMonitorContext ctx = findContextBySlot(tentacleName, slotNumber);
        if (ctx == null) {
            return ResponseEntity.ok(Map.of("monitoring", false));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("monitoring", true);
        result.put("testToolName", ctx.getCurrentTestToolName());
        result.put("startTimeMs", ctx.getStartTimeMs());
        result.put("elapsedSeconds", ctx.getElapsedSecondsValue());
        result.put("types", ctx.getMonitoredData().keySet());
        result.put("entryCounts", ctx.getMonitoredData().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size())));
        return ResponseEntity.ok(result);
    }

    /** 수집 중인 특정 metadata 데이터 조회 (인메모리) */
    @GetMapping("/slot/{tentacleName}/{slotNumber}/{typeKey}")
    public ResponseEntity<List<Map<String, Object>>> getSlotMetadata(
            @PathVariable String tentacleName,
            @PathVariable int slotNumber,
            @PathVariable String typeKey) {
        SlotMonitorContext ctx = findContextBySlot(tentacleName, slotNumber);
        if (ctx == null) {
            return ResponseEntity.notFound().build();
        }
        List<Map<String, Object>> data = ctx.getMonitoredData().get(typeKey);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(data);
    }
