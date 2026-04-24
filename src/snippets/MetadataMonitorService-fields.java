// com.samsung.move.metadata.service.MetadataMonitorService — 필드 & 스레드풀
// 1) 슬롯 상태 감지는 Spring @Scheduled (5초 주기)
// 2) 실제 수집은 slot 별 ScheduledExecutorService 태스크 (초 단위 주기, thread pool 8)
// 3) 활성화/제외/주기 세 가지 제어 맵은 tentacleName:slotNumber 를 키로
@Slf4j @Service @RequiredArgsConstructor
public class MetadataMonitorService {

    private final HeadSlotStateStore stateStore;
    private final HeadConnectionManager connectionManager;
    private final MetadataCommandExecutor commandExecutor;
    private final MetadataMonitorProperties props;
    private final UfsMetadataCommandRepository commandRepo;
    private final UfsProductMetadataRepository productMetadataRepo;

    /** key = "source:slotIndex" (HEAD 기준) */
    private final ConcurrentHashMap<String, SlotMonitorContext> activeMonitors = new ConcurrentHashMap<>();
    /** 이전 상태 저장 — running 진입/이탈 감지용 */
    private final ConcurrentHashMap<String, SlotPreviousState> previousStates = new ConcurrentHashMap<>();

    /** 슬롯별 수집 활성화 (기본값 OFF) — key = "tentacleName:slotNumber" */
    private final Set<String> enabledSlots = ConcurrentHashMap.newKeySet();
    /** 슬롯별 제외할 메타데이터 타입 키 */
    private final ConcurrentHashMap<String, Set<String>> excludedTypes = new ConcurrentHashMap<>();
    /** 슬롯별 수집 주기(초) — 설정 없으면 전역 기본값 (분*60) */
    private final ConcurrentHashMap<String, Integer> slotIntervalSeconds = new ConcurrentHashMap<>();

    // 슬롯 독립 스레드풀 — 한 슬롯이 느려도 다른 슬롯이 밀리지 않게
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(8, r -> {
        Thread t = new Thread(r, "metadata-monitor");
        t.setDaemon(true);   // JVM 종료 시 자동 해제
        return t;
    });

    private record SlotPreviousState(String testState, String testToolName) {}

    @PreDestroy
    public void shutdown() {
        for (SlotMonitorContext ctx : activeMonitors.values()) {
            if (ctx.getFuture() != null) ctx.getFuture().cancel(true);
        }
        activeMonitors.clear();
        executor.shutdownNow();
    }
}
