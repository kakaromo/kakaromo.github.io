// @source src/main/java/com/samsung/move/admin/controller/AdminController.java
// @lines 568-610
// @note /permission-requests/{id}/approve — findByIdForUpdate(비관적락) + 권한·Head access 저장 + 요청 상태 전이
// @synced 2026-05-01T01:05:23.650Z

    @SuppressWarnings("unchecked")
    @Transactional("portalTransactionManager")
    @PutMapping("/permission-requests/{id}/approve")
    public Map<String, Object> approvePermissionRequest(@PathVariable Long id,
            @RequestBody Map<String, Object> body, HttpSession session) {
        PermissionRequest req = permissionRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("요청을 찾을 수 없습니다: " + id));
        if (!"PENDING".equals(req.getStatus())) {
            return Map.of("success", false, "error", "이미 처리된 요청입니다");
        }
        // 권한 저장
        Map<String, Boolean> permissions = (Map<String, Boolean>) body.get("permissions");
        if (permissions != null) {
            permissionService.updatePermissions(req.getUserId(), permissions);
        }
        // Head 접근 저장
        List<Number> headAccessIds = (List<Number>) body.get("headAccessIds");
        if (headAccessIds != null) {
            userHeadAccessRepository.deleteByUserId(req.getUserId());
            for (Number hid : headAccessIds) {
                userHeadAccessRepository.save(UserHeadAccess.builder()
                        .userId(req.getUserId())
                        .headConnectionId(hid.longValue())
                        .build());
            }
        }
        // 요청 승인 처리
        PortalUser admin = (PortalUser) session.getAttribute("portalUser");
        req.setStatus("APPROVED");
        req.setReviewedBy(admin != null ? admin.getId() : null);
        req.setReviewedAt(java.time.LocalDateTime.now());
        permissionRequestRepository.save(req);
        adminNotificationService.notifyPermissionRequestResolved();
        return Map.of("success", true);
    }

    @Transactional("portalTransactionManager")
    @PutMapping("/permission-requests/{id}/reject")
    public Map<String, Object> rejectPermissionRequest(@PathVariable Long id,
            @RequestBody Map<String, String> body, HttpSession session) {
        PermissionRequest req = permissionRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("요청을 찾을 수 없습니다: " + id));
        if (!"PENDING".equals(req.getStatus())) {
