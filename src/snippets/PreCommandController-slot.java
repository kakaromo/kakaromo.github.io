// @source src/main/java/com/samsung/move/head/precmd/controller/PreCommandController.java
// @lines 60-128
// @note 슬롯 CRUD — list / assign(TC 자동 초기화) / unassign
// @synced 2026-04-19T09:18:51.168Z

    @GetMapping("/slots")
    public List<Map<String, Object>> listSlotAssignments(@RequestParam List<String> setLocations) {
        return slotPreCommandRepository.findBySetLocationIn(setLocations).stream()
                .map(sp -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("setLocation", sp.getSetLocation());
                    if (sp.getPreCommand() != null) {
                        m.put("preCommandId", sp.getPreCommand().getId());
                        m.put("preCommandName", sp.getPreCommand().getName());
                    }
                    boolean hasTc = sp.getTcPreCommandIds() != null && !sp.getTcPreCommandIds().isBlank()
                            && sp.getTcPreCommandIds().chars().anyMatch(c -> c != '0' && c != ',');
                    m.put("hasTcPreCommand", hasTc);
                    return m;
                })
                .collect(Collectors.toList());
    }

    @PostMapping("/slots/assign")
    @Transactional("portalTransactionManager")
    public ResponseEntity<?> assignSlots(@RequestBody AssignRequest request) {
        PreCommand preCommand = service.findById(request.preCommandId());
        for (String loc : request.setLocations()) {
            SlotPreCommand existing = slotPreCommandRepository.findBySetLocation(loc).orElse(null);
            if (existing != null) {
                existing.setPreCommand(preCommand);
                // 슬롯 등록 시 TC Pre-Command 초기화 (양립 불가)
                existing.setTcPreCommandIds(null);
                slotPreCommandRepository.save(existing);
            } else {
                slotPreCommandRepository.save(SlotPreCommand.builder()
                        .setLocation(loc)
                        .preCommand(preCommand)
                        .build());
            }
        }
        return ResponseEntity.ok(Map.of("success", true, "count", request.setLocations().size()));
    }

    /** 슬롯에 TC Pre-Command가 등록되어 있는지 확인 */
    @GetMapping("/slots/has-tc")
    public ResponseEntity<?> hasTcPreCommands(@RequestParam List<String> setLocations) {
        boolean hasTc = setLocations.stream().anyMatch(loc ->
                slotPreCommandRepository.findBySetLocation(loc)
                        .map(sp -> sp.getTcPreCommandIds() != null && !sp.getTcPreCommandIds().isBlank()
                                && sp.getTcPreCommandIds().chars().anyMatch(c -> c != '0' && c != ','))
                        .orElse(false));
        return ResponseEntity.ok(Map.of("hasTc", hasTc));
    }

    @PostMapping("/slots/unassign")
    @Transactional("portalTransactionManager")
    public ResponseEntity<?> unassignSlots(@RequestBody UnassignRequest request) {
        for (String loc : request.setLocations()) {
            slotPreCommandRepository.findBySetLocation(loc)
                    .ifPresent(sp -> {
                        if (sp.getTcPreCommandIds() == null || sp.getTcPreCommandIds().isBlank()) {
                            slotPreCommandRepository.delete(sp);
                        } else {
                            sp.setPreCommand(null);
                            slotPreCommandRepository.save(sp);
                        }
                    });
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    public record AssignRequest(Long preCommandId, List<String> setLocations) {}
    public record UnassignRequest(List<String> setLocations) {}
