// @source src/main/java/com/samsung/move/head/tcp/HeadTcpClient.java
// @lines 147-215
// @note acceptLoop + readFromSocket + processChunk
// @synced 2026-04-19T04:30:19.798Z

    private void acceptLoop() throws IOException {
        long lastDataTime = System.currentTimeMillis();

        while (running.get()) {
            try {
                Socket client = serverSocket.accept();
                client.setTcpNoDelay(true);
                inSocket = client;
                lastDataTime = System.currentTimeMillis();

                try {
                    readFromSocket(client);
                } finally {
                    try { client.close(); } catch (IOException ignored) {}
                    inSocket = null;
                }
            } catch (java.net.SocketTimeoutException e) {
                // accept timeout — outSocket 상태 확인 후 계속 대기
                if (outSocket == null || outSocket.isClosed()) {
                    log.info("[{}] outSocket closed, exiting accept loop", name);
                    break;
                }
                long idleMs = System.currentTimeMillis() - lastDataTime;
                // 3시간 이상 데이터 없으면 exit 보내고 reconnect
                if (idleMs >= IDLE_RECONNECT_MS) {
                    log.info("[{}] No data for {}h, sending exit and reconnecting",
                            name, idleMs / 3600000);
                    try { sendDisconnectForce(); } catch (IOException ignored) {}
                    break;
                }
            }
        }
    }

    private void readFromSocket(Socket socket) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

        StringBuilder buffer = new StringBuilder();
        char[] cbuf = new char[8192];
        int bytesRead;
        long totalBytes = 0;

        while (running.get() && (bytesRead = reader.read(cbuf)) != -1) {
            totalBytes += bytesRead;
            buffer.append(cbuf, 0, bytesRead);

            String content = buffer.toString();
            int endIdx;
            while ((endIdx = content.indexOf("^end^")) != -1) {
                String chunk = content.substring(0, endIdx);
                content = content.substring(endIdx + 5);

                processChunk(chunk);
            }
            buffer.setLength(0);
            buffer.append(content);
        }

//        log.info("[{}] HEAD disconnected, total bytes received: {}", name, totalBytes);
    }

    private void processChunk(String chunk) {
        List<HeadSlotData> slotDataList = HeadMessageParser.parseMessage(chunk, name, headType);
        if (!slotDataList.isEmpty()) {
            stateStore.updateSlots(name, slotDataList);
            log.debug("[{}] Updated {} slots", name, slotDataList.size());
        }
    }
