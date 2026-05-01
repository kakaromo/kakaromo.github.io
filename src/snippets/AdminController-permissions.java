// @source src/main/java/com/samsung/move/admin/controller/AdminController.java
// @lines 173-216
// @note /users/{id}/permissions GET/PUT + /permissions/defaults + /users/{id}/head-access GET/PUT
// @synced 2026-05-01T01:05:23.650Z

    // ── User permissions ────────────────────────────────────────────

    @GetMapping("/users/{id}/permissions")
    public Map<String, Boolean> getUserPermissions(@PathVariable Long id) {
        return permissionService.getPermissionMap(id);
    }

    @SuppressWarnings("unchecked")
    @PutMapping("/users/{id}/permissions")
    public Map<String, Boolean> updateUserPermissions(@PathVariable Long id, @RequestBody Map<String, Boolean> permissions) {
        return permissionService.updatePermissions(id, permissions);
    }

    @GetMapping("/permissions/defaults")
    public List<Map<String, String>> getDefaultPermissions() {
        return permissionService.getDefaultPermissions();
    }

    // ── User head access ─────────────────────────────────────────────

    @GetMapping("/users/{id}/head-access")
    public List<Long> getUserHeadAccess(@PathVariable Long id) {
        return userHeadAccessRepository.findByUserId(id).stream()
                .map(UserHeadAccess::getHeadConnectionId)
                .collect(Collectors.toList());
    }

    @Transactional("portalTransactionManager")
    @PutMapping("/users/{id}/head-access")
    public List<Long> updateUserHeadAccess(@PathVariable Long id, @RequestBody List<Long> headConnectionIds) {
        userHeadAccessRepository.deleteByUserId(id);
        userHeadAccessRepository.flush();
        List<UserHeadAccess> entities = headConnectionIds.stream()
                .map(hcId -> UserHeadAccess.builder().userId(id).headConnectionId(hcId).build())
                .collect(Collectors.toList());
        userHeadAccessRepository.saveAll(entities);
        return headConnectionIds;
    }

    // ── Session config ──────────────────────────────────────────��─

    @GetMapping("/session-config")
    public Map<String, Integer> getSessionConfig() {
        return sessionConfigService.getConfig();
