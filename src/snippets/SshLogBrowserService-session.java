// @source src/main/java/com/samsung/move/logbrowser/service/SshLogBrowserService.java
// @lines 302-369
// @note getOrCreateCachedSession + execCommand (exit>1만 예외, rg no-match=1 허용)
// @synced 2026-04-19T10:15:34.658Z

    private synchronized Session getOrCreateCachedSession(String tentacleName) throws Exception {
        PortalServer vm = findVm(tentacleName);
        String host;
        int port;
        String username;
        String password;
        if (vm != null) {
            host = vm.getIp();
            port = vm.getSshPort();
            username = vm.getUsername();
            password = vm.getPassword();
        } else {
            host = tentacleName;
            port = tentacleSshPort;
            username = tentacleUsername;
            password = tentaclePassword;
        }

        String key = host + ":" + port;
        CachedSession cached = sessionCache.get(key);

        if (cached != null && cached.session.isConnected()) {
            cached.lastAccess = System.currentTimeMillis();
            return cached.session;
        }

        JSch jsch = new JSch();
        Session session = jsch.getSession(username, host, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("ServerAliveInterval", "30");
        session.setConfig("ServerAliveCountMax", "10");
        session.connect(30000);

        sessionCache.put(key, new CachedSession(session));
        log.info("Created cached SSH session for {}", key);
        return session;
    }

    private String execCommand(Session session, String command) throws Exception {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        channel.setInputStream(null);

        InputStream in = channel.getInputStream();
        channel.connect(30000);

        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) != -1) {
            sb.append(new String(buf, 0, len, StandardCharsets.UTF_8));
        }

        while (!channel.isClosed()) {
            Thread.sleep(50);
        }

        int exitStatus = channel.getExitStatus();
        channel.disconnect();

        // rg returns exit 1 for no matches — that's OK
        if (exitStatus > 1) {
            throw new RuntimeException("Command failed with exit status " + exitStatus);
        }

        return sb.toString();
    }
