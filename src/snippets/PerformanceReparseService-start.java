// @source src/main/java/com/samsung/move/testdb/reparse/PerformanceReparseService.java
// @lines 28-132
// @note FixedThreadPool(4) + jobs Map + historyJobMap 중복 방어 + startReparse
// @synced 2026-05-01T01:10:31.181Z

public class PerformanceReparseService {

    private final PerformanceHistoryService historyService;
    private final PerformanceTestCaseService testCaseService;
    private final PerformanceParserService parserService;
    private final PortalServerService serverService;
    private final String logPrefix;
    private final String headLogPath;

    private final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "reparse-worker");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentHashMap<String, ReparseJob> jobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> historyJobMap = new ConcurrentHashMap<>();
    private final AtomicLong version = new AtomicLong(0);

    public PerformanceReparseService(
            PerformanceHistoryService historyService,
            PerformanceTestCaseService testCaseService,
            PerformanceParserService parserService,
            PortalServerService serverService,
            @Value("${tentacle.log-prefix:/home/octo/tentacle}") String logPrefix,
            @Value("${tentacle.head.log-path:/home/octo/nas}") String headLogPath) {
        this.historyService = historyService;
        this.testCaseService = testCaseService;
        this.parserService = parserService;
        this.serverService = serverService;
        this.logPrefix = logPrefix;
        this.headLogPath = headLogPath;
    }

    public long getVersion() {
        return version.get();
    }

    public ReparseJob startReparse(Long historyId) {
        // 이미 진행중인지 확인
        String existingJobId = historyJobMap.get(historyId);
        if (existingJobId != null) {
            ReparseJob existing = jobs.get(existingJobId);
            if (existing != null && ("preparing".equals(existing.getState()) || "running".equals(existing.getState()))) {
                throw new IllegalStateException("History " + historyId + " is already being reparsed (job: " + existingJobId + ")");
            }
        }

        PerformanceHistory history = historyService.findById(historyId);
        if (history == null) {
            throw new IllegalArgumentException("History not found: " + historyId);
        }
        if (history.getLogPath() == null || history.getLogPath().isBlank()) {
            throw new IllegalArgumentException("History has no log path");
        }
        if ("RUNNING".equalsIgnoreCase(history.getResult())) {
            throw new IllegalArgumentException("Cannot reparse a running test");
        }

        Long tcId = history.getTcId();
        if (tcId == null) {
            throw new IllegalArgumentException("History has no TC ID");
        }

        // TC → parser name 조회
        PerformanceTestCase tc = testCaseService.findById(tcId);
        if (tc == null || tc.getParserId() == null) {
            throw new IllegalArgumentException("TestCase or parser not found for tcId: " + tcId);
        }
        PerformanceParser parser = parserService.findById(tc.getParserId());
        if (parser == null || parser.getName() == null) {
            throw new IllegalArgumentException("Parser not found for parserId: " + tc.getParserId());
        }
        String parserName = parser.getName();

        // 경로 해석 (PerformanceResultDataService 동일 로직)
        String logPath = history.getLogPath();
        String slotLocation = history.getSlotLocation();
        String tentacleName;
        String logDir;
        boolean isNas;

        int lastSlash = logPath.lastIndexOf('/');
        String dirPath = lastSlash > 0 ? logPath.substring(0, lastSlash) : logPath;

        if (logPath.contains("UFS")) {
            tentacleName = "HEAD";
            logDir = headLogPath + "/NAS/" + dirPath;
            isNas = true;
        } else {
            tentacleName = slotLocation != null ? slotLocation.replaceAll("^([A-Za-z]+\\d*).*", "$1") : "HEAD";
            logDir = logPrefix + "/history/" + dirPath;
            isNas = false;
        }

        ReparseJob job = new ReparseJob(historyId, tcId, tentacleName, logDir, isNas, parserName);
        jobs.put(job.getJobId(), job);
        historyJobMap.put(historyId, job.getJobId());
        version.incrementAndGet();

        executor.submit(() -> doReparse(job));
        log.info("Reparse started: jobId={}, historyId={}, tentacle={}, logDir={}, nas={}, parser={}",
                job.getJobId(), historyId, tentacleName, logDir, isNas, parserName);
        return job;
    }
