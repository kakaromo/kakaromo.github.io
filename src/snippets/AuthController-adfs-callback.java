// @source src/main/java/com/samsung/move/auth/controller/AuthController.java
// @lines 181-252
// @note POST /adfs/callback — id_token 파싱 + claims 추출 + PortalUser 생성/갱신 + 세션 저장
// @synced 2026-04-19T09:32:45.533Z

    /**
     * ADFS callback — form_post로 id_token 수신, claims 파싱, 세션 생성 후 SPA로 redirect
     */
    @PostMapping("/adfs/callback")
    public void adfsCallback(
            @RequestParam(required = false) String id_token,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String error_description,
            HttpSession session,
            HttpServletResponse response) throws Exception {

        if (error != null) {
            log.warn("ADFS callback error: {} — {}", error, error_description);
            response.sendRedirect("/?adfs_error=" + URLEncoder.encode(
                    error_description != null ? error_description : error, StandardCharsets.UTF_8));
            return;
        }

        if (id_token == null || id_token.isBlank()) {
            response.sendRedirect("/?adfs_error=no_id_token");
            return;
        }

        // JWT payload 파싱 (서명 검증 생략 — 내부망 ADFS 신뢰)
        Map<String, Object> claims = parseJwtPayload(id_token);
        if (claims == null) {
            response.sendRedirect("/?adfs_error=invalid_token");
            return;
        }

        log.debug("[ADFS] id_token claims: {}", claims);

        // claims에서 사용자 정보 추출
        // userid: 고유 키 (변경 안 됨), loginid: username (변경될 수 있음), username: 표시 이름
        String adfsUserId = getStringClaim(claims, "userid");
        if (adfsUserId == null) adfsUserId = getStringClaim(claims, "sub");
        if (adfsUserId == null) {
            response.sendRedirect("/?adfs_error=no_userid");
            return;
        }

        String loginId = getStringClaim(claims, "loginid");
        if (loginId == null) loginId = getStringClaim(claims, "upn");
        if (loginId == null) loginId = adfsUserId;

        String displayName = getStringClaim(claims, "username");
        if (displayName == null) displayName = getStringClaim(claims, "commonname");
        if (displayName == null) displayName = loginId;

        String email = getStringClaim(claims, "mail");
        if (email == null) email = getStringClaim(claims, "email");

        log.info("[ADFS] login: adfsUserId={}, loginId={}, displayName={}", adfsUserId, loginId, displayName);

        // portal_users에 자동 등록/업데이트 + 권한 부여
        PortalUser portalUser = userService.createOrUpdateFromAdfs(adfsUserId, loginId, displayName, email);
        if (permissionService.getPermissionMap(portalUser.getId()).values().stream().noneMatch(v -> v)) {
            permissionService.grantAllPermissions(portalUser.getId());
        }

        session.setAttribute(SESSION_USER, portalUser);
        applySessionTimeout(session);

        // HTML 응답으로 SPA 이동 (redirect 대신 — 세션 쿠키 확실히 설정)
        String redirectTarget = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl + "/" : "/";
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write(
                "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">" +
                "<script>window.location.replace('" + redirectTarget + "');</script>" +
                "</head><body>Redirecting...</body></html>");
    }
