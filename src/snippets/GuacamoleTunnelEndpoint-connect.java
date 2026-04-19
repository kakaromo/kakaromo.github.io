// @source src/main/java/com/samsung/move/guacamole/endpoint/GuacamoleTunnelEndpoint.java
// @lines 180-273
// @note connectDirect + buildConfig + readFromTunnel
// @synced 2026-04-19T08:33:48.674Z

    /**
     * SSH/VNC: 1:1 직접 연결
     */
    private void connectDirect(Session session, String vm, String protocol, GuacamoleConfiguration guacConfig,
                                String guacdHost, int guacdPort, PortalServer vmConfig) throws GuacamoleException {
        log.info("Connecting {} tunnel [vm={}, guacd={}:{}] -> {}",
                protocol.toUpperCase(), vm, guacdHost, guacdPort, vmConfig.getIp());

        GuacamoleSocket socket = new ConfiguredGuacamoleSocket(
                new InetGuacamoleSocket(guacdHost, guacdPort),
                guacConfig
        );

        GuacamoleTunnel tunnel = new SimpleGuacamoleTunnel(socket);
        sshTunnels.put(session, tunnel);

        log.info("Connected to guacd, tunnel UUID: {}", tunnel.getUUID());

        Thread readerThread = new Thread(() -> readFromTunnel(session, tunnel), "guac-reader-" + session.getId());
        sshReaderThreads.put(session, readerThread);
        readerThread.start();
    }

    private GuacamoleConfiguration buildConfig(String protocol, PortalServer vmConfig, int width, int height, int dpi) {
        GuacamoleConfiguration guacConfig = new GuacamoleConfiguration();
        guacConfig.setProtocol(protocol.toLowerCase());
        guacConfig.setParameter("hostname", vmConfig.getIp());
        guacConfig.setParameter("username", vmConfig.getUsername());
        guacConfig.setParameter("password", vmConfig.getPassword());

        if ("ssh".equalsIgnoreCase(protocol)) {
            guacConfig.setParameter("port", String.valueOf(vmConfig.getSshPort()));
            guacConfig.setParameter("font-name", "D2Coding");
            guacConfig.setParameter("font-size", "10");
            guacConfig.setParameter("color-scheme", "gray-black");
            guacConfig.setParameter("terminal-type", "xterm-256color");
            guacConfig.setParameter("width", String.valueOf(width));
            guacConfig.setParameter("height", String.valueOf(height));
            guacConfig.setParameter("dpi", String.valueOf(dpi));
            guacConfig.setParameter("disable-copy", "false");
            guacConfig.setParameter("disable-paste", "false");
        } else if ("rdp".equalsIgnoreCase(protocol)) {
            guacConfig.setParameter("port", String.valueOf(vmConfig.getRdpPort()));
            guacConfig.setParameter("width", String.valueOf(width));
            guacConfig.setParameter("height", String.valueOf(height));
            guacConfig.setParameter("dpi", String.valueOf(dpi));
            guacConfig.setParameter("ignore-cert", "true");
            guacConfig.setParameter("security", "any");
            guacConfig.setParameter("resize-method", "display-update");
            guacConfig.setParameter("server-layout", "ko-kr-qwerty");
            guacConfig.setParameter("disable-copy", "false");
            guacConfig.setParameter("disable-paste", "false");
            guacConfig.setParameter("normalize-clipboard", "windows");
            guacConfig.setParameter("enable-drive", "true");
            guacConfig.setParameter("drive-path", "/tmp/guac-drive");
            guacConfig.setParameter("enable-wallpaper", "false");
            guacConfig.setParameter("enable-theming", "false");
            guacConfig.setParameter("enable-font-smoothing", "false");
            guacConfig.setParameter("enable-full-window-drag", "false");
            guacConfig.setParameter("enable-desktop-composition", "false");
            guacConfig.setParameter("enable-menu-animations", "false");
            guacConfig.setParameter("disable-audio", "true");
        } else if ("vnc".equalsIgnoreCase(protocol)) {
            guacConfig.setParameter("port", String.valueOf(vmConfig.getVncPort()));
            guacConfig.setParameter("width", String.valueOf(width));
            guacConfig.setParameter("height", String.valueOf(height));
            guacConfig.setParameter("dpi", String.valueOf(dpi));
            guacConfig.setParameter("color-depth", "24");
            guacConfig.setParameter("cursor", "remote");
            guacConfig.setParameter("disable-copy", "false");
            guacConfig.setParameter("disable-paste", "false");
        }

        return guacConfig;
    }

    private void readFromTunnel(Session session, GuacamoleTunnel tunnel) {
        try {
            GuacamoleReader reader = tunnel.acquireReader();
            char[] message;
            while ((message = reader.read()) != null && session.isOpen()) {
                String text = new String(message);
                safeSendText(session, text);
            }
            log.info("guacd closed tunnel for session {}", session.getId());
        } catch (GuacamoleException e) {
            log.warn("Guacamole read error for session {}: {}", session.getId(), e.getMessage(), e);
        } catch (Exception e) {
            log.warn("Reader thread error for session {}: {} - {}", session.getId(), e.getClass().getSimpleName(), e.getMessage());
        } finally {
            tunnel.releaseReader();
            closeSshTunnel(session);
        }
    }
