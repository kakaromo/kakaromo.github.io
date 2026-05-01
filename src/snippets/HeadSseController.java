// @source src/main/java/com/samsung/move/head/controller/HeadSseController.java
// @lines 31-168
// @note EmitterWrapper + stream + @Scheduled pushUpdates + buildPayload
// @synced 2026-05-01T01:10:31.152Z


@RestController
@RequestMapping("/api/head")
@RequiredArgsConstructor
public class HeadSseController {

    private static final Logger log = LoggerFactory.getLogger(HeadSseController.class);

    private final HeadSlotStateStore stateStore;
    private final HeadConnectionProperties properties;
    private final HeadConnectionManager connectionManager;
    private final HeadConnectionService connectionService;
    private final SlotInfoMerger slotInfoMerger;
    private final UserHeadAccessRepository userHeadAccessRepository;
    private final AdminNotificationService adminNotificationService;

    @Value("${portal.test-instance:false}")
    private boolean testInstance;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CopyOnWriteArrayList<EmitterWrapper> emitters = new CopyOnWriteArrayList<>();

    private static class EmitterWrapper {
        final SseEmitter emitter;
        final String source; // null = all sources
        long lastVersion = -1;
        int lastPayloadHash = 0;

        EmitterWrapper(SseEmitter emitter, String source) {
            this.emitter = emitter;
            this.source = source;
        }
    }

    @GetMapping(value = "/slots/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(required = false) String source) {
        // timeout 0L = 무제한 — 클라이언트가 연결을 끊을 때까지 유지
        // 유한 timeout 사용 시 AsyncRequestTimeoutException WARN 로그가 주기적으로 발생
        SseEmitter emitter = new SseEmitter(0L);

        EmitterWrapper wrapper = new EmitterWrapper(emitter, source);
        emitters.add(wrapper);

        emitter.onCompletion(() -> emitters.remove(wrapper));
        emitter.onTimeout(() -> {
            emitters.remove(wrapper);
            emitter.complete();
        });
        emitter.onError(e -> emitters.remove(wrapper));

        // Send initial state immediately
        try {
            String json = objectMapper.writeValueAsString(buildPayload(source));
            emitter.send(SseEmitter.event()
                    .name("init")
                    .data(json, MediaType.APPLICATION_JSON));
            wrapper.lastVersion = stateStore.getVersion();
        } catch (Exception e) {
            emitters.remove(wrapper);
        }

        return emitter;
    }

    @GetMapping("/slots")
    public Map<String, Object> snapshot(@RequestParam(required = false) String source) {
        return buildPayload(source);
    }

    @GetMapping("/connections")
    public List<Map<String, Object>> connections(HttpSession session) {
        boolean admin = isAdmin(session);
        List<Map<String, Object>> statuses = getConnectionStatuses();
        // test instance → testMode만, 일반 → testMode 제외
        statuses.removeIf(s -> testInstance != Boolean.TRUE.equals(s.get("testMode")));
        // 유저별 Head 접근 제한 적용
        if (!admin) {
            filterByUserHeadAccess(session, statuses);
        }
        return statuses;
    }

    private void filterByUserHeadAccess(HttpSession session, List<Map<String, Object>> statuses) {
        try {
            PortalUser user = (PortalUser) session.getAttribute("portalUser");
            if (user == null) return;
            List<UserHeadAccess> accessList = userHeadAccessRepository.findByUserId(user.getId());
            if (accessList.isEmpty()) return; // 매핑 없으면 전체 허용
            // 허용된 head connection ID → name 변환
            Set<Long> allowedIds = new HashSet<>();
            for (UserHeadAccess access : accessList) {
                allowedIds.add(access.getHeadConnectionId());
            }
            Set<String> allowedNames = new HashSet<>();
            for (var conn : connectionService.findAll()) {
                if (allowedIds.contains(conn.getId())) {
                    allowedNames.add(conn.getName());
                }
            }
            List<String> deniedNames = new ArrayList<>();
            for (Map<String, Object> s : statuses) {
                Object name = s.get("name");
                if (name != null && !allowedNames.contains(name)) {
                    deniedNames.add(String.valueOf(name));
                }
            }
            statuses.removeIf(s -> !allowedNames.contains(s.get("name")));
            if (!deniedNames.isEmpty()) {
                log.info("[Head 접근 차단] user={}, denied={}", user.getUsername(), deniedNames);
                for (String denied : deniedNames) {
                    try {
                        adminNotificationService.notifyHeadAccessDenied(
                                user.getUsername(), user.getDisplayName(), denied);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            log.debug("Failed to filter head access: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${head.sse.push-interval-ms:1000}")
    public void pushUpdates() {
        if (emitters.isEmpty()) return;

        long currentVersion = stateStore.getVersion();

        for (EmitterWrapper wrapper : emitters) {
            try {
                // payload를 항상 만들어 해시로 변화 감지.
                // version이 그대로라도 SlotInfomation(DB) 병합 결과가 바뀌었으면 push.
                // 평가 완료 시 HEAD TCP가 재전송 없이 DB만 갱신되는 케이스를 커버.
                String json = objectMapper.writeValueAsString(buildPayload(wrapper.source));
                int hash = json.hashCode();
                if (wrapper.lastVersion >= currentVersion && wrapper.lastPayloadHash == hash) continue;

                wrapper.emitter.send(SseEmitter.event()
                        .name("update")
