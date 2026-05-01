// @source src/main/java/com/samsung/move/t32/controller/T32ConfigController.java
// @lines 77-110
// @note updateConfig — 비밀번호 마스킹 패턴(공백이면 기존 값 유지)
// @synced 2026-05-01T01:05:23.653Z

    @PutMapping("/configs/{id}")
    public ResponseEntity<T32ConfigDto> updateConfig(@PathVariable Long id, @RequestBody CreateRequest request) {
        return configRepository.findById(id)
                .map(existing -> {
                    existing.setServerGroupId(request.serverGroupId());
                    existing.setJtagServerId(request.jtagServerId());
                    existing.setJtagUsername(request.jtagUsername());
                    // 비밀번호가 비어있으면 기존 값 유지 (프론트에서 마스킹)
                    if (request.jtagPassword() != null && !request.jtagPassword().isBlank()) {
                        existing.setJtagPassword(request.jtagPassword());
                    }
                    existing.setT32PcId(request.t32PcId());
                    existing.setT32PcUsername(request.t32PcUsername());
                    if (request.t32PcPassword() != null && !request.t32PcPassword().isBlank()) {
                        existing.setT32PcPassword(request.t32PcPassword());
                    }
                    existing.setJtagCommand(request.jtagCommand());
                    existing.setJtagSuccessPattern(request.jtagSuccessPattern());
                    existing.setT32PortCheckCommand(request.t32PortCheckCommand());
                    existing.setDumpCommand(request.dumpCommand());
                    existing.setFwCodeLinuxBase(request.fwCodeLinuxBase());
                    existing.setFwCodeWindowsBase(request.fwCodeWindowsBase());
                    existing.setResultBasePath(request.resultBasePath());
                    existing.setResultWindowsBasePath(request.resultWindowsBasePath());
                    existing.setDescription(request.description());
                    existing.setEnabled(request.enabled());
                    configRepository.save(existing);

                    saveAssignedServers(id, request.assignedServerIds());

                    return ResponseEntity.ok(loadDto(id));
                })
                .orElse(ResponseEntity.notFound().build());
    }
