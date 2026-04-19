// @source src/main/java/com/samsung/move/head/precmd/controller/PreCommandController.java
// @lines 130-194
// @note TC CRUD — assign(슬롯 자동 해제) / unassign — position 기반
// @synced 2026-04-19T09:49:20.688Z

    // ── TC별 사전 명령어 등록 (setLocation + position 기반) ──

    @GetMapping("/tc")
    public Map<String, Object> getTcAssignments(@RequestParam String setLocation) {
        var sp = slotPreCommandRepository.findBySetLocation(setLocation).orElse(null);
        String ids = (sp != null) ? sp.getTcPreCommandIds() : null;
        return Map.of("tcPreCommandIds", ids != null ? ids : "");
    }

    @PostMapping("/tc/assign")
    @Transactional("portalTransactionManager")
    public ResponseEntity<?> assignTc(@RequestBody TcAssignRequest request) {
        service.findById(request.preCommandId());

        var sp = slotPreCommandRepository.findBySetLocation(request.setLocation()).orElse(null);
        if (sp == null) {
            sp = SlotPreCommand.builder()
                    .setLocation(request.setLocation())
                    .build();
        }

        // TC 등록 시 슬롯 Pre-Command 자동 해제 (양립 불가)
        sp.setPreCommand(null);

        String[] ids = parseTcIds(sp.getTcPreCommandIds(), request.tcPosition() + 1);
        ids[request.tcPosition()] = String.valueOf(request.preCommandId());
        sp.setTcPreCommandIds(String.join(",", ids));
        slotPreCommandRepository.save(sp);

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/tc/unassign")
    @Transactional("portalTransactionManager")
    public ResponseEntity<?> unassignTc(@RequestBody TcUnassignRequest request) {
        slotPreCommandRepository.findBySetLocation(request.setLocation())
                .ifPresent(sp -> {
                    String[] ids = parseTcIds(sp.getTcPreCommandIds(), request.tcPosition() + 1);
                    ids[request.tcPosition()] = "0";
                    sp.setTcPreCommandIds(String.join(",", ids));

                    if (sp.getPreCommand() == null && isAllZero(ids)) {
                        slotPreCommandRepository.delete(sp);
                    } else {
                        slotPreCommandRepository.save(sp);
                    }
                });
        return ResponseEntity.ok(Map.of("success", true));
    }

    /** tcPreCommandIds 동기화 (testcaseIds 변경 시 프론트에서 호출) */
    @PostMapping("/tc/sync")
    @Transactional("portalTransactionManager")
    public ResponseEntity<?> syncTcPreCommandIds(@RequestBody TcSyncRequest request) {
        slotPreCommandRepository.findBySetLocation(request.setLocation())
                .ifPresent(sp -> {
                    sp.setTcPreCommandIds(request.tcPreCommandIds());
                    slotPreCommandRepository.save(sp);
                });
        return ResponseEntity.ok(Map.of("success", true));
    }

    public record TcAssignRequest(Long preCommandId, String setLocation, int tcPosition) {}
    public record TcUnassignRequest(String setLocation, int tcPosition) {}
    public record TcSyncRequest(String setLocation, String tcPreCommandIds) {}
