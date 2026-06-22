// @source src/main/java/com/samsung/move/t32/grpc/T32RemoteClient.java
// @lines 28-130
// @note gRPC 어댑터 — Session(RAII ManagedChannel) + ping/attach/eval
// @synced 2026-06-22T22:22:10.910Z

/**
 * t32remote (Go gRPC) 어댑터. T32 PC 마다 t32remote.exe 가 1개 동작하므로
 * 채널은 호출 단위로 새로 만들고 종료한다 (단일 dump 흐름이 직렬이라 캐시 불필요).
 */
@Component
public class T32RemoteClient {

    private static final Logger log = LoggerFactory.getLogger(T32RemoteClient.class);

    private static final int DEFAULT_T32_NODE_PORT = 20000;
    private static final int DEFAULT_PACK_LEN = 1024;
    private static final long PING_DEADLINE_SECONDS = 5;
    private static final long ATTACH_DEADLINE_SECONDS = 30;

    /** 단일 dump 사이클 동안 채널을 열어두고 닫는 RAII 핸들. */
    public static class Session implements AutoCloseable {
        private final ManagedChannel channel;
        private final T32RemoteGrpc.T32RemoteBlockingStub stub;
        private final String host;
        private final int port;

        Session(String host, int port) {
            this.host = host;
            this.port = port;
            this.channel = ManagedChannelBuilder.forAddress(host, port)
                    .usePlaintext()
                    .maxInboundMessageSize(16 * 1024 * 1024)
                    .keepAliveTime(30, TimeUnit.SECONDS)
                    .keepAliveTimeout(5, TimeUnit.SECONDS)
                    .keepAliveWithoutCalls(true)
                    .build();
            this.stub = T32RemoteGrpc.newBlockingStub(channel);
        }

        public T32RemoteGrpc.T32RemoteBlockingStub stub() { return stub; }
        public String host() { return host; }
        public int port() { return port; }

        @Override
        public void close() {
            try {
                channel.shutdown().awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public Session open(String host, int port) {
        return new Session(host, port);
    }

    /** 연결만 확인. t32remote 가 살아있고 T32 에 Attach 된 상태인지 빠르게 본다. */
    public boolean ping(String host, int port) {
        try (Session s = open(host, port)) {
            s.stub().withDeadlineAfter(PING_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .ping(PingRequest.getDefaultInstance());
            return true;
        } catch (StatusRuntimeException e) {
            log.debug("[T32Remote] ping {}:{} 실패: {}", host, port, e.getStatus());
            return false;
        }
    }

    /**
     * t32remote 가 가리키는 T32 PowerView 에 Attach. 이미 Attach 되어 있어도 idempotent
     * 하게 t32_version 을 반환하도록 구현되어 있다 (서버 측 책임).
     */
    public AttachResponse attach(Session s) {
        return attach(s, DEFAULT_T32_NODE_PORT);
    }

    /**
     * RCL(NETASSIST) 포트를 지정해 Attach. t32remote → PowerView 가 붙는 포트로,
     * PowerView 의 config.t32 RCL PORT 와 일치해야 한다. 0 이하면 기본값 사용.
     */
    public AttachResponse attach(Session s, int nodePort) {
        int port = nodePort > 0 ? nodePort : DEFAULT_T32_NODE_PORT;
        AttachRequest req = AttachRequest.newBuilder()
                .setNode("localhost")
                .setPort(port)
                .setPackLen(DEFAULT_PACK_LEN)
                .build();
        return s.stub().withDeadlineAfter(ATTACH_DEADLINE_SECONDS, TimeUnit.SECONDS).attach(req);
    }

    /**
     * PRACTICE 식/함수를 평가하고 결과 문자열을 반환한다. 예: {@code eval(s, "STATE.TARGET()")}
     * 는 타겟 상태(예: {@code "running (bus error)"})를 돌려준다. (T32_ExecuteFunction)
     */
    public String eval(Session s, String expression) {
        EvalRequest req = EvalRequest.newBuilder().setExpression(expression).build();
        return s.stub().withDeadlineAfter(PING_DEADLINE_SECONDS, TimeUnit.SECONDS)
                .eval(req)
                .getValue();
    }

    /**
     * OS(cmd) 명령을 t32remote 세션에서 실행한다. t32remote 가 PowerView 와 같은 세션에서
     * 돌아 그 세션의 매핑 드라이브(Z:)·자격증명을 그대로 쓰므로, Z: 경로 파일 작업이
     * SSH/PsExec 세션·net use 문제 없이 동작한다. timeoutMs 안에 출력+exitCode 반환.
     */
