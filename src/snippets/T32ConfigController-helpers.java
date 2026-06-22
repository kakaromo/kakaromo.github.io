// @source src/main/java/com/samsung/move/t32/controller/T32ConfigController.java
// @lines 123-145
// @note saveAssignedServers + loadDto — 매핑 deleteAll → saveAll 패턴
// @synced 2026-06-22T22:22:10.943Z

    // ── Delete ──

    @DeleteMapping("/configs/{id}")
    public ResponseEntity<Void> deleteConfig(@PathVariable Long id, HttpSession session) {
        if (!configRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        configRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

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

