// @source src/main/java/com/samsung/move/head/controller/HeadSseController.java
// @lines 31-168
// @note EmitterWrapper + stream + @Scheduled pushUpdates + buildPayload
// @synced 2026-04-19T09:32:45.509Z

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

    @Value("${portal.test-instance:false}")
    private boolean testInstance;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CopyOnWriteArrayList<EmitterWrapper> emitters = new CopyOnWriteArrayList<>();

    private static class EmitterWrapper {
        final SseEmitter emitter;
        final String source; // null = all sources
        long lastVersion = -1;

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
            statuses.removeIf(s -> !allowedNames.contains(s.get("name")));
        } catch (Exception e) {
            log.debug("Failed to filter head access: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${head.sse.push-interval-ms:1000}")
    public void pushUpdates() {
        if (emitters.isEmpty()) return;

        long currentVersion = stateStore.getVersion();

        for (EmitterWrapper wrapper : emitters) {
            if (wrapper.lastVersion >= currentVersion) continue;

            try {
                String json = objectMapper.writeValueAsString(buildPayload(wrapper.source));
                wrapper.emitter.send(SseEmitter.event()
                        .name("update")
                        .data(json, MediaType.APPLICATION_JSON));
                wrapper.lastVersion = currentVersion;
            } catch (Exception e) {
                try { wrapper.emitter.completeWithError(e); } catch (Exception ignored) {}
                emitters.remove(wrapper);
            }
        }
    }

    private Map<String, Object> buildPayload(String source) {
        List<HeadSlotData> slots = (source != null)
                ? stateStore.getSlotsBySource(source)
                : stateStore.getAllSlots();

        // DB SlotInfomation에서 testCaseIds/testCaseStatus/testTrId 등 merge
        slotInfoMerger.merge(slots);

        Map<String, Object> payload = new HashMap<>();
        payload.put("slots", slots);
        payload.put("version", stateStore.getVersion());
        payload.put("connections", getConnectionStatuses());
        return payload;
    }
