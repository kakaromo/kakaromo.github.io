// @source src/main/java/com/samsung/move/auth/controller/AuthController.java
// @lines 158-180
// @note GET /adfs/login — nonce 생성 + authorize URL 조립 + redirect
// @synced 2026-05-01T01:05:23.635Z

        userService.changePassword(user.getId(), newPassword);
        return Map.of("success", true);
    }

    // ── ADFS SSO ──

    /**
     * ADFS 로그인 — authorize URL로 redirect
     */
    @GetMapping("/adfs/login")
    public void adfsLogin(HttpSession session, HttpServletResponse response) throws Exception {
        if (!adfsProperties.isEnabled()) {
            response.sendRedirect("/?adfs_error=disabled");
            return;
        }
        String nonce = UUID.randomUUID().toString();
        session.setAttribute(SESSION_NONCE, nonce);

        String url = adfsProperties.getAuthorizeUrl()
                + "?client_id=" + URLEncoder.encode(adfsProperties.getClientId(), StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(adfsProperties.getRedirectUrl(), StandardCharsets.UTF_8)
                + "&response_mode=form_post"
                + "&response_type=" + URLEncoder.encode("code id_token", StandardCharsets.UTF_8)
