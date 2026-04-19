// @source src/main/java/com/samsung/move/agent/grpc/AgentGrpcClient.java
// @lines 20-100
// @note ManagedChannel 설정 + blocking/async stub + subscribeJobProgressAsync
// @synced 2026-04-19T09:32:45.513Z

public class AgentGrpcClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AgentGrpcClient.class);

    private final ManagedChannel channel;
    private final DeviceAgentGrpc.DeviceAgentBlockingStub blockingStub;
    private final DeviceAgentGrpc.DeviceAgentStub asyncStub;
    private final String host;
    private final int port;

    public AgentGrpcClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .maxInboundMessageSize(256 * 1024 * 1024) // 256MB for trace raw data
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(5, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .build();
        this.blockingStub = DeviceAgentGrpc.newBlockingStub(channel);
        this.asyncStub = DeviceAgentGrpc.newStub(channel);
    }

    /**
     * Check if the channel is in a connected/ready state.
     */
    public boolean isConnected() {
        ConnectivityState state = channel.getState(false);
        return state == ConnectivityState.READY || state == ConnectivityState.IDLE;
    }

    /**
     * Get the current channel connectivity state.
     */
    public String getConnectionState() {
        return channel.getState(false).name();
    }

    // ── Device Management ──

    public ListDevicesResponse listDevices() {
        return blockingStub.listDevices(ListDevicesRequest.getDefaultInstance());
    }

    public ConnectDeviceResponse connectDevice(String serial) {
        return blockingStub.connectDevice(
                ConnectDeviceRequest.newBuilder().setSerial(serial).build());
    }

    public DisconnectDeviceResponse disconnectDevice(String serial) {
        return blockingStub.disconnectDevice(
                DisconnectDeviceRequest.newBuilder().setSerial(serial).build());
    }

    // ── Benchmarking ──

    public RunBenchmarkResponse runBenchmark(RunBenchmarkRequest request) {
        return blockingStub.runBenchmark(request);
    }

    public GetJobStatusResponse getJobStatus(String jobId) {
        return blockingStub.getJobStatus(
                GetJobStatusRequest.newBuilder().setJobId(jobId).build());
    }

    public Iterator<JobProgress> subscribeJobProgress(String jobId) {
        return blockingStub.subscribeJobProgress(
                SubscribeJobProgressRequest.newBuilder().setJobId(jobId).build());
    }

    public void subscribeJobProgressAsync(String jobId, StreamObserver<JobProgress> observer) {
        asyncStub.subscribeJobProgress(
                SubscribeJobProgressRequest.newBuilder().setJobId(jobId).build(), observer);
    }

    public GetBenchmarkResultResponse getBenchmarkResult(String jobId, String deviceId) {
        return blockingStub.getBenchmarkResult(GetBenchmarkResultRequest.newBuilder()
                .setJobId(jobId)
                .setDeviceId(deviceId != null ? deviceId : "")
                .build());
