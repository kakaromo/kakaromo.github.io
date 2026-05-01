// @source src/main/java/com/samsung/move/t32/controller/T32ConfigController.java
// @lines 123-145
// @note saveAssignedServers + loadDto — 매핑 deleteAll → saveAll 패턴
// @synced 2026-05-01T01:10:31.195Z

    // ── Helpers ──

    private void saveAssignedServers(Long configId, List<Long> serverIds) {
        configServerRepository.deleteByT32ConfigId(configId);
        if (serverIds != null) {
            List<T32ConfigServer> mappings = serverIds.stream()
                    .map(sid -> T32ConfigServer.builder().t32ConfigId(configId).serverId(sid).build())
                    .toList();
            configServerRepository.saveAll(mappings);
        }
    }

    private T32ConfigDto loadDto(Long configId) {
        T32Config config = configRepository.findById(configId).orElseThrow();

        Map<Long, PortalServer> serverMap = serverRepository.findAll().stream()
                .collect(Collectors.toMap(PortalServer::getId, Function.identity()));
        Map<Long, ServerGroup> groupMap = serverGroupRepository.findAll().stream()
                .collect(Collectors.toMap(ServerGroup::getId, Function.identity()));

        return toDto(config, serverMap, groupMap);
    }

