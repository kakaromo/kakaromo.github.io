// @source src/main/java/com/samsung/move/head/tcp/HeadTcpClient.java
// @lines 109-141
// @note connect() — listen bind + outSocket + connect 커맨드
// @synced 2026-04-19T05:26:12.210Z

    private void connect() throws Exception {
        localIp = resolveLocalIp();
        listenSuffix = String.format("%02d", listenPort % 100);
        headSuffix = String.format("%02d", headPort % 100);

        // 1. bind + listen (backlog 10, 참고 코드와 동일)
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.setSoTimeout(30000);
        serverSocket.bind(new InetSocketAddress(localIp, listenPort), 10);
        log.info("[{}] Listening on {}:{}", name, localIp, listenPort);

        // 2. HEAD에 outbound 연결 (세션 유지 — 이 소켓을 닫으면 HEAD가 전송 실패)
        outSocket = new Socket(headHost, headPort);
        outSocket.setTcpNoDelay(true);
        outSocket.setKeepAlive(true);
        log.info("[{}] Connected to HEAD at {}:{}", name, headHost, headPort);

        // 3. connect command 전송
        int listenSuffixNum = listenPort % 100;
        int headSuffixNum = headPort % 100;
        String connectCmd = localIp + ":" + listenSuffixNum + ":" + headHost + "[" + headSuffixNum + "] connection!!\n";
        sendCommand(outSocket, connectCmd);
        log.info("[{}] Sent connect command: {}", name, connectCmd.trim());

        // 4. Mark as connected
        connected.set(true);
        stateStore.updateConnectionStatus(name, true);
        log.info("[{}] Connection established, waiting for HEAD data", name);

        // 5. Accept loop — HEAD는 데이터를 보낼 때마다 listenPort로 새 연결을 맺고 닫는다
        acceptLoop();
    }
