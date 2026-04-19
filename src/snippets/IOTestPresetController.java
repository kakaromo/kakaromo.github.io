// @source src/main/java/com/samsung/move/agent/controller/AgentController.java
// @lines 1291-1333
// @note /agent/iotest-presets CRUD + configJson Map/String 자동 변환 빌더
// @synced 2026-04-19T10:15:34.675Z

    @GetMapping("/iotest-presets")
    public List<Map<String, Object>> getIOTestPresets() {
        return ioTestPresetService.findAll().stream().map(this::toIOTestPresetMap).toList();
    }

    @PostMapping("/iotest-presets")
    public Map<String, Object> createIOTestPreset(@RequestBody Map<String, Object> body) {
        IOTestPreset p = buildIOTestPresetFromBody(body);
        return toIOTestPresetMap(ioTestPresetService.create(p));
    }

    @PutMapping("/iotest-presets/{id}")
    public Map<String, Object> updateIOTestPreset(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        IOTestPreset p = buildIOTestPresetFromBody(body);
        return toIOTestPresetMap(ioTestPresetService.update(id, p));
    }

    @DeleteMapping("/iotest-presets/{id}")
    public ResponseEntity<Map<String, Object>> deleteIOTestPreset(@PathVariable Long id) {
        ioTestPresetService.delete(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private IOTestPreset buildIOTestPresetFromBody(Map<String, Object> body) {
        return IOTestPreset.builder()
                .name((String) body.get("name"))
                .description((String) body.get("description"))
                .category((String) body.get("category"))
                .configJson(body.get("configJson") instanceof String s ? s : objectMapper.valueToTree(body.get("configJson")).toString())
                .build();
    }

    private Map<String, Object> toIOTestPresetMap(IOTestPreset p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", p.getId());
        map.put("name", p.getName());
        map.put("description", p.getDescription());
        map.put("category", p.getCategory());
        map.put("configJson", p.getConfigJson());
        map.put("createdAt", p.getCreatedAt());
        map.put("updatedAt", p.getUpdatedAt());
        return map;
    }
