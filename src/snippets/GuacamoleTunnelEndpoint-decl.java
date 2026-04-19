// @source src/main/java/com/samsung/move/guacamole/endpoint/GuacamoleTunnelEndpoint.java
// @lines 30-82
// @note @ServerEndpoint 선언 + 필드 + 생성자
// @synced 2026-04-19T08:48:08.168Z

 * WebSocket endpoint that connects directly to guacd daemon.
 *
 * SSH: 1:1 세션 (각 사용자 독립 터미널)
 * RDP: 화면 공유 모드 (같은 VM → 같은 RDP 세션을 여러 viewer가 공유)
 */
@Slf4j
@Component
@ServerEndpoint(value = "/api/guacamole/tunnel", subprotocols = "guacamole", configurator = SpringWebSocketConfigurator.class)
public class GuacamoleTunnelEndpoint {

    private final GuacamoleProperties properties;
    private final PortalServerService serverService;
    private final SharedTunnelManager sharedTunnelManager;
    private final SessionLockManager sessionLockManager;

    // SSH: 1:1 session → tunnel
    private final Map<Session, GuacamoleTunnel> sshTunnels = new ConcurrentHashMap<>();
    private final Map<Session, Thread> sshReaderThreads = new ConcurrentHashMap<>();

    // RDP/VNC: session → vmName
    private final Map<Session, String> rdpSessions = new ConcurrentHashMap<>();
    // session → user name (for lock release)
    private final Map<Session, String> sessionUsers = new ConcurrentHashMap<>();

    // 세션별 쓰기 lock — getAsyncRemote().sendText() 동시 호출 시 TEXT_FULL_WRITING 방지
    private final Map<Session, Object> sessionWriteLocks = new ConcurrentHashMap<>();

    private Object getWriteLock(Session session) {
        return sessionWriteLocks.computeIfAbsent(session, k -> new Object());
    }

    private void safeSendText(Session session, String text) {
        if (!session.isOpen()) return;
        synchronized (getWriteLock(session)) {
            try {
                session.getBasicRemote().sendText(text);
            } catch (IOException e) {
                log.debug("Send failed [session={}]: {}", session.getId(), e.getMessage());
            }
        }
    }

    public int getActiveTunnelCount() {
        return sshTunnels.size() + rdpSessions.size();
    }

    public GuacamoleTunnelEndpoint(GuacamoleProperties properties, PortalServerService serverService,
                                   SharedTunnelManager sharedTunnelManager, SessionLockManager sessionLockManager) {
        this.properties = properties;
        this.serverService = serverService;
        this.sharedTunnelManager = sharedTunnelManager;
        this.sessionLockManager = sessionLockManager;
    }
