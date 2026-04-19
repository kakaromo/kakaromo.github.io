// @source src/main/java/com/samsung/move/agent/grpc/AgentConnectionManager.java
// @lines 14-82
// @note serverId별 gRPC client 캐싱 + host:port 변경 시 재연결
// @synced 2026-04-19T06:47:47.011Z

@Component
public class AgentConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(AgentConnectionManager.class);

    private final ConcurrentHashMap<Long, AgentGrpcClient> clients = new ConcurrentHashMap<>();

    public AgentGrpcClient getOrCreate(Long serverId, String host, int port) {
        return clients.compute(serverId, (id, existing) -> {
            if (existing != null && existing.getHost().equals(host) && existing.getPort() == port) {
                return existing;
            }
            if (existing != null) {
                log.info("Agent server {}:{} changed, reconnecting", host, port);
                existing.close();
            }
            return new AgentGrpcClient(host, port);
        });
    }

    public AgentGrpcClient get(Long serverId) {
        return clients.get(serverId);
    }

    public void remove(Long serverId) {
        AgentGrpcClient client = clients.remove(serverId);
        if (client != null) {
            client.close();
        }
    }

    /**
     * Create a temporary client for connection testing (not cached).
     */
    public boolean testConnection(String host, int port) {
        try (AgentGrpcClient client = new AgentGrpcClient(host, port)) {
            return client.testConnection();
        }
    }

    /**
     * Force reconnect: close existing client and create new one.
     */
    public AgentGrpcClient reconnect(Long serverId, String host, int port) {
        AgentGrpcClient existing = clients.get(serverId);
        if (existing != null) {
            existing.close();
        }
        AgentGrpcClient newClient = new AgentGrpcClient(host, port);
        clients.put(serverId, newClient);
        log.info("Reconnected agent server {}:{} (id={})", host, port, serverId);
        return newClient;
    }

    /**
     * Get connection state for a server.
     */
    public String getConnectionState(Long serverId) {
        AgentGrpcClient client = clients.get(serverId);
        if (client == null) return "NOT_CONNECTED";
        return client.getConnectionState();
    }

    @PreDestroy
    public void shutdown() {
        clients.values().forEach(AgentGrpcClient::close);
        clients.clear();
    }
}
