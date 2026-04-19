// @source src/main/java/com/samsung/move/guacamole/endpoint/GuacamoleTunnelEndpoint.java
// @lines 275-322
// @note onMessage(heartbeat + 터널 write) + onClose(정리)
// @synced 2026-04-19T08:48:08.169Z

    @OnMessage
    public void onMessage(Session session, String message) {
        // Heartbeat 갱신 (Lock 타임아웃 방지)
        String user = sessionUsers.get(session);
        if (user != null) {
            for (var entry : sessionLockManager.getAllLocks().entrySet()) {
                if (entry.getValue().user().equals(user)) {
                    sessionLockManager.heartbeat(entry.getKey(), user);
                    break;
                }
            }
        }

        // RDP 공유 세션인지 확인
        String vmName = rdpSessions.get(session);
        if (vmName != null) {
            sharedTunnelManager.sendInput(vmName, message);
            return;
        }

        // SSH 직접 세션
        GuacamoleTunnel tunnel = sshTunnels.get(session);
        if (tunnel != null) {
            try {
                GuacamoleWriter writer = tunnel.acquireWriter();
                writer.write(message.toCharArray());
                tunnel.releaseWriter();
            } catch (GuacamoleException e) {
                log.error("Error writing to tunnel: {}", e.getMessage());
                closeSshTunnel(session);
            }
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        log.info("WebSocket closed: {} - code={}, reason={}", session.getId(), reason.getCloseCode(), reason.getReasonPhrase());
        sessionWriteLocks.remove(session);
        releaseSessionLock(session);

        String vmName = rdpSessions.remove(session);
        if (vmName != null) {
            sharedTunnelManager.removeViewer(vmName, session);
        } else {
            closeSshTunnel(session);
        }
    }

