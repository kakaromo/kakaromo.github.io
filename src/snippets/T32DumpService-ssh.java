// @source src/main/java/com/samsung/move/t32/service/T32DumpService.java
// @lines 332-391
// @note JSch SSH 실행 — stdout/stderr 실시간 step-output 전송 + timeout
// @synced 2026-04-19T06:47:47.019Z

    private CommandResult executeSshCommand(PortalServer server, String command, long timeoutSeconds,
                                            SseEmitter emitter, int step) throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(server.getUsername(), server.getIp(), server.getSshPort());
        session.setPassword(server.getPassword());
        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("PreferredAuthentications", "password,keyboard-interactive");
        session.connect(10_000);

        try {
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);

            InputStream stdout = channel.getInputStream();
            InputStream stderr = channel.getErrStream();
            channel.connect(10_000);

            StringBuilder output = new StringBuilder();
            long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stdout, StandardCharsets.UTF_8));
                 BufferedReader errReader = new BufferedReader(new InputStreamReader(stderr, StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    if (System.currentTimeMillis() > deadline) {
                        channel.disconnect();
                        output.append("\n[TIMEOUT] ").append(timeoutSeconds).append("초 초과로 중단됨");
                        return new CommandResult(-1, output.toString().trim());
                    }
                    output.append(line).append("\n");
                    sendEvent(emitter, "step-output", Map.of("step", step, "line", line));
                }
                while ((line = errReader.readLine()) != null) {
                    if (System.currentTimeMillis() > deadline) {
                        channel.disconnect();
                        return new CommandResult(-1, output.toString().trim());
                    }
                    output.append(line).append("\n");
                    sendEvent(emitter, "step-output", Map.of("step", step, "line", line));
                }
            }

            while (!channel.isClosed()) {
                if (System.currentTimeMillis() > deadline) {
                    channel.disconnect();
                    return new CommandResult(-1, output + "\n[TIMEOUT]");
                }
                Thread.sleep(50);
            }

            int exitCode = channel.getExitStatus();
            channel.disconnect();
            return new CommandResult(exitCode, output.toString().trim());

        } finally {
            session.disconnect();
        }
    }
