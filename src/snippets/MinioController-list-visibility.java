// @source src/main/java/com/samsung/move/minio/controller/MinioController.java
// @lines 28-80
// @note GET /buckets — Admin은 visibility 토글 전체, User는 visible 만
// @synced 2026-04-19T09:04:03.510Z

@Slf4j
@RestController
@RequestMapping("/api/minio")
@RequiredArgsConstructor
public class MinioController {

    private final MinioStorageService storageService;
    private final BucketVisibilityRepository bucketVisibilityRepository;

    @GetMapping("/buckets")
    public ResponseEntity<?> listBuckets(HttpSession session) throws Exception {
        List<String> allBuckets = storageService.listBuckets();

        if (isAdmin(session)) {
            Map<String, Boolean> visibilityMap = new HashMap<>();
            bucketVisibilityRepository.findAll().forEach(bv ->
                    visibilityMap.put(bv.getBucketName(), bv.isVisible()));

            List<Map<String, Object>> result = new ArrayList<>();
            for (String bucket : allBuckets) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", bucket);
                item.put("visible", visibilityMap.getOrDefault(bucket, true));
                result.add(item);
            }
            return ResponseEntity.ok(result);
        } else {
            Set<String> hiddenBuckets = bucketVisibilityRepository.findAll().stream()
                    .filter(bv -> !bv.isVisible())
                    .map(BucketVisibility::getBucketName)
                    .collect(Collectors.toSet());

            List<String> visibleBuckets = allBuckets.stream()
                    .filter(b -> !hiddenBuckets.contains(b))
                    .toList();
            return ResponseEntity.ok(visibleBuckets);
        }
    }

    @PutMapping("/buckets/{bucketName}/visibility")
    public ResponseEntity<?> setBucketVisibility(
            @PathVariable String bucketName,
            @RequestBody Map<String, Boolean> body,
            HttpSession session) {
        if (!isAdmin(session)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin 권한이 필요합니다."));
        }
        Boolean visible = body.get("visible");
        if (visible == null) {
            throw new IllegalArgumentException("visible is required");
        }

        BucketVisibility bv = bucketVisibilityRepository.findByBucketName(bucketName)
