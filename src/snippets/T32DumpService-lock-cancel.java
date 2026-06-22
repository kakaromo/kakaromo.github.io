// @source src/main/java/com/samsung/move/t32/service/T32DumpService.java
// @lines 95-168
// @note RunningDump 맵 + checkAvailability(busy/lockedBy) + cancelDump(점유자만 interrupt)
// @synced 2026-06-22T22:22:10.908Z

    /**
     * 진행 중인 dump 워커. 사용자가 "중단"을 누르거나 SSE 연결이 끊겼을 때 해당 워커
     * 스레드를 interrupt 해 즉시 정리(lock 해제·t32remote 세션 close)하기 위해 보관한다.
     * 키는 lock 과 동일한 {@code configId}. lock 해제는 워커의 finally 단일 지점에 두고,
     * 여기서는 워커를 깨우기만 한다.
     */
    private record RunningDump(java.util.concurrent.Future<?> future, String userKey) {}
    private final ConcurrentHashMap<Long, RunningDump> running = new ConcurrentHashMap<>();

    // 한국어 Windows 콘솔 출력은 기본 MS949(CP949) 라, SSH stdout 을 UTF-8 로
    // 읽으면 한글이 깨진다. T32_SSH_CHARSET 으로 오버라이드 가능(기본 MS949).
    private static final java.nio.charset.Charset SSH_CHARSET = resolveSshCharset();

    private static java.nio.charset.Charset resolveSshCharset() {
        String name = System.getenv("T32_SSH_CHARSET");
        if (name == null || name.isBlank()) name = "MS949";
        try {
            return java.nio.charset.Charset.forName(name);
        } catch (Exception e) {
            return java.nio.charset.Charset.forName("MS949");
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static final long JTAG_TIMEOUT_SECONDS = 30;
    private static final long ATTACH_TIMEOUT_SECONDS = 30;
    private static final long DUMP_TIMEOUT_SECONDS = 300;



    // ── T32 Config 조회 ──

    public T32Config getConfigById(Long configId) {
        return configRepository.findById(configId).orElse(null);
    }

    // busy/lockedBy/lockedSince: 다른 사람이 dump 점유 중인지(check 단계에서 미리 표시).
    public record T32Availability(boolean available, Long configId,
                                  boolean busy, String lockedBy, String lockedSince) {}

    public T32Availability checkAvailability(Long serverId) {
        Optional<T32ConfigServer> mapping = configServerRepository.findByServerId(serverId);
        if (mapping.isEmpty()) return new T32Availability(false, null, false, null, null);

        Optional<T32Config> config = configRepository.findById(mapping.get().getT32ConfigId());
        if (config.isEmpty()) return new T32Availability(false, null, false, null, null);

        T32Config c = config.get();
        Optional<T32DumpLockService.Holder> holder = lockService.getHolder(c.getId());
        return new T32Availability(
                c.isEnabled(), c.getId(),
                holder.isPresent(),
                holder.map(T32DumpLockService.Holder::displayName).orElse(null),
                holder.map(h -> h.since().toString()).orElse(null));
    }

    /** serverId → 점유 대상 configId 해석(없으면 null). lock 획득 전 키 산출용. */
    public Long resolveConfigId(Long serverId) {
        return configServerRepository.findByServerId(serverId)
                .map(T32ConfigServer::getT32ConfigId)
                .orElse(null);
    }
