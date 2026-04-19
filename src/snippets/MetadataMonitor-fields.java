// @source src/main/java/com/samsung/move/metadata/service/MetadataMonitorService.java
// @lines 40-133
// @note 슬롯별 활성 모니터 · excluded types · 8개 스레드풀 + SlotMonitorContext
// @synced 2026-04-19T06:47:47.015Z

    private final UfsMetadataCommandRepository commandRepo;
    private final UfsProductMetadataRepository productMetadataRepo;
    private final CompatibilityTestRequestRepository compatTrRepo;
    private final PerformanceTestRequestRepository perfTrRepo;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** key = "source:slotIndex" */
    private final ConcurrentHashMap<String, SlotMonitorContext> activeMonitors = new ConcurrentHashMap<>();

    /** 이전 상태 저장 (testState + testToolName) */
    private final ConcurrentHashMap<String, SlotPreviousState> previousStates = new ConcurrentHashMap<>();

    /** 슬롯별 metadata 수집 활성화 — key = "tentacleName:slotNumber" (e.g. "T1:0"), 기본값 OFF */
    private final Set<String> enabledSlots = ConcurrentHashMap.newKeySet();
    /** 슬롯별 제외할 메타데이터 타입 키 (key = "tentacleName:slotNumber", value = Set of typeKeys) */
    private final ConcurrentHashMap<String, Set<String>> excludedTypes = new ConcurrentHashMap<>();
    /** 슬롯별 수집 주기 (초) — 설정 없으면 전역 기본값 사용 */
    private final ConcurrentHashMap<String, Integer> slotIntervalSeconds = new ConcurrentHashMap<>();

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(8, r -> {
        Thread t = new Thread(r, "metadata-monitor");
        t.setDaemon(true);
        return t;
    });

    private record SlotPreviousState(String testState, String testToolName) {}

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down metadata monitor — cancelling {} active monitors", activeMonitors.size());
        for (SlotMonitorContext ctx : activeMonitors.values()) {
            if (ctx.getFuture() != null) {
                ctx.getFuture().cancel(true);
            }
        }
        activeMonitors.clear();
        executor.shutdownNow();
    }

    @Getter
    public static class SlotMonitorContext {
        private final String slotKey;
        private final String tentacleName;
        private final int slotNumber;
        private final String serial;
        private final String currentTestToolName;
        private final String controller;
        private final String nandType;
        private final String cellType;
        private final String fwVer;
        private final List<UfsMetadataCommand> commands;
        private final Map<String, List<Map<String, Object>>> monitoredData = new ConcurrentHashMap<>();
        private final Set<Long> pushedToolIds = ConcurrentHashMap.newKeySet();
        private final long startTimeMs = System.currentTimeMillis();
        private final AtomicInteger elapsedSeconds = new AtomicInteger(0);
        private final ReentrantLock monitorLock = new ReentrantLock();
        private volatile ScheduledFuture<?> future;

        SlotMonitorContext(String slotKey, HeadSlotData slot, List<UfsMetadataCommand> commands) {
            this.slotKey = slotKey;
            this.tentacleName = parseTentacleName(slotKey, slot);
            this.slotNumber = slot.getSlotIndex();
            this.serial = slot.getUsbId();
            this.currentTestToolName = slot.getTestToolName();
            this.controller = slot.getController();
            this.nandType = slot.getNandType();
            this.cellType = slot.getCellType();
            this.fwVer = slot.getFwVer();
            this.commands = commands;
            for (UfsMetadataCommand cmd : commands) {
                monitoredData.put(cmd.getMetadataType().getTypeKey(), new CopyOnWriteArrayList<>());
            }
        }

        private static String parseTentacleName(String slotKey, HeadSlotData slot) {
            String loc = slot.getSetLocation();
            if (loc != null && loc.contains("-")) {
                return loc.substring(0, loc.lastIndexOf('-'));
            }
            // fallback: source name
            String source = slot.getSource();
            return source != null ? source : "unknown";
        }

        void setFuture(ScheduledFuture<?> future) {
            this.future = future;
        }

        public int getElapsedSecondsValue() {
            return elapsedSeconds.get();
        }
    }

