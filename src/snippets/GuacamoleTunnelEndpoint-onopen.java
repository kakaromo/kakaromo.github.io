// @source src/main/java/com/samsung/move/guacamole/endpoint/GuacamoleTunnelEndpoint.java
// @lines 84-147
// @note onOpen — 파라미터 파싱 + Lock + VM 조회 + buildConfig + connect
// @synced 2026-04-19T08:19:17.625Z

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        session.setMaxIdleTimeout(0);
        session.setMaxTextMessageBufferSize(1024 * 1024);

        log.info("WebSocket opened: {}", session.getId());

        Map<String, List<String>> params = session.getRequestParameterMap();
        String vm = getParam(params, "vm");
        String protocol = getParam(params, "protocol");
        String user = getParam(params, "user");
        if (user == null || user.isBlank()) user = "unknown-" + session.getId().substring(0, 6);
        int width = parseInt(getParam(params, "width"), 1920);
        int height = parseInt(getParam(params, "height"), 1080);
        int dpi = parseInt(getParam(params, "dpi"), 96);

        if (vm == null || protocol == null) {
            closeWithError(session, "Missing required parameters: vm, protocol");
            return;
        }

        if (protocol.contains("?")) {
            protocol = protocol.substring(0, protocol.indexOf("?"));
        }

        // RDP/VNC: 세션 Lock 체크
        if ("rdp".equalsIgnoreCase(protocol) || "vnc".equalsIgnoreCase(protocol)) {
            SessionLockManager.LockInfo existing = sessionLockManager.tryAcquire(vm, user, protocol);
            if (existing != null) {
                log.warn("Session locked [vm={}, lockedBy={}, attemptBy={}]", vm, existing.user(), user);
                closeWithError(session, "SESSION_LOCKED:" + existing.user());
                return;
            }
            sessionUsers.put(session, user);
        }

        PortalServer vmConfig = serverService.findByName(vm)
                .filter(PortalServer::isVisible)
                .orElse(null);

        if (vmConfig == null) {
            closeWithError(session, "VM not found or disabled: " + vm);
            return;
        }

        try {
            GuacamoleConfiguration guacConfig = buildConfig(protocol, vmConfig, width, height, dpi);

            String guacdHost = vmConfig.getGuacdHost() != null ? vmConfig.getGuacdHost() : properties.getGuacdHost();
            int guacdPort = vmConfig.getGuacdPort() != null ? vmConfig.getGuacdPort() : properties.getGuacdPort();

            log.info("Tunnel request [vm={}, protocol={}, user={}, size={}x{}, dpi={}, guacd={}:{}, ip={}, rdpPort={}]",
                    vm, protocol, user, width, height, dpi, guacdHost, guacdPort,
                    vmConfig.getIp(), vmConfig.getRdpPort());

            // 모든 프로토콜 1:1 직접 연결
            connectDirect(session, vm, protocol, guacConfig, guacdHost, guacdPort, vmConfig);
        } catch (Exception e) {
            log.error("Failed to connect for session {}: {}", session.getId(), e.getMessage(), e);
            // 연결 실패 시 Lock 해제
            releaseSessionLock(session);
            closeWithError(session, "Failed to connect: " + e.getMessage());
        }
    }
