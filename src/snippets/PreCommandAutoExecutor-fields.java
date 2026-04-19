// @source src/main/java/com/samsung/move/head/precmd/service/PreCommandAutoExecutor.java
// @lines 34-83
// @note 필드 + 중복 방지 Set + onSlotStateChanged 훅 진입점
// @synced 2026-04-19T09:04:03.500Z

@Component
@RequiredArgsConstructor
public class PreCommandAutoExecutor {

    private final PreCommandService preCommandService;
    private final SlotPreCommandRepository slotPreCommandRepository;
    private final SlotInfomationRepository slotInfomationRepository;
    private final CompatibilityTestCaseRepository compatibilityTestCaseRepository;
    private final PerformanceTestCaseRepository performanceTestCaseRepository;

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    private static final Pattern SET_LOCATION_PATTERN = Pattern.compile("^([A-Za-z]+\\d+)-(\\d+).*");

    /** 슬롯 Pre-Command 중복 실행 방지 — key: setLocation */
    private final Set<String> executedSlots = ConcurrentHashMap.newKeySet();

    /** TC position별 중복 실행 방지 — key: "setLocation:tcPosition" */
    private final Set<String> executedTcs = ConcurrentHashMap.newKeySet();

    public void onSlotStateChanged(String source, HeadSlotData oldData, HeadSlotData newData) {
        String newState = newData.getTestState();
        String setLocation = newData.getSetLocation();
        if (setLocation == null || setLocation.isBlank()) return;

        // init이 아닌 상태 → 실행 추적 초기화
        if (newState == null || !newState.toLowerCase().contains("init")) {
            executedSlots.remove(setLocation);
            clearTcExecuted(setLocation);
            return;
        }

        // SlotPreCommand 조회 (setLocation 기반)
        var spOpt = slotPreCommandRepository.findBySetLocation(setLocation);
        if (spOpt.isEmpty()) return;
        var sp = spOpt.get();

        // TC Pre-Command 우선 확인
        boolean tcHandled = tryExecuteTcPreCommand(source, newData, setLocation, sp.getTcPreCommandIds());

        // TC Pre-Command가 없을 때만 슬롯 Pre-Command 실행
        if (!tcHandled && sp.getPreCommand() != null && executedSlots.add(setLocation)) {
            final long preCommandId = sp.getPreCommand().getId();
            executor.submit(() -> {
                try {
                    preCommandService.executeSync(preCommandId, source, List.of(newData.getSlotIndex()));
                } catch (Exception ignored) {}
            });
        }
    }
