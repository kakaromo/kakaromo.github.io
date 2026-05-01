// @source src/main/java/com/samsung/move/auth/interceptor/ActionPermissionInterceptor.java
// @lines 1-101
// @note URL + HTTP method → DB ActionPermission 매칭 + 권한 체크 + 403 응답
// @synced 2026-05-01T01:05:23.636Z

package com.samsung.move.auth.interceptor;

import com.samsung.move.auth.entity.ActionPermission;
import com.samsung.move.auth.entity.PortalUser;
import com.samsung.move.auth.repository.ActionPermissionRepository;
import com.samsung.move.auth.service.AdminNotificationService;
import com.samsung.move.auth.service.UserPermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.annotation.PostConstruct;

import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActionPermissionInterceptor implements HandlerInterceptor {

    private final ActionPermissionRepository actionPermissionRepository;
    private final UserPermissionService userPermissionService;
    private final AdminNotificationService adminNotificationService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final CopyOnWriteArrayList<ActionPermission> rules = new CopyOnWriteArrayList<>();

    @Value("${portal.auth.disabled:false}")
    private boolean authDisabled;

    @PostConstruct
    public void loadRules() {
        try {
            rules.clear();
            rules.addAll(actionPermissionRepository.findByEnabledTrue());
            log.info("Loaded {} action permission rules", rules.size());
        } catch (Exception e) {
            log.warn("Failed to load action permission rules: {}", e.getMessage());
        }
    }

    /** Admin에서 매핑 변경 시 호출 */
    public void reloadRules() {
        loadRules();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (authDisabled) return true;

        String uri = request.getRequestURI();
        String method = request.getMethod();

        // disabled 사용자는 권한 룰 매칭 이전에 전면 차단 (관리자 승인 전)
        HttpSession session = request.getSession(false);
        PortalUser sessionUser = (session != null)
                ? (PortalUser) session.getAttribute("portalUser")
                : null;
        if (sessionUser != null && !sessionUser.isEnabled()) {
            sendDisabled(response);
            return false;
        }

        // 매칭되는 룰 찾기
        ActionPermission matched = null;
        for (ActionPermission rule : rules) {
            boolean methodMatch = "*".equals(rule.getHttpMethod())
                    || rule.getHttpMethod().equalsIgnoreCase(method);
            if (methodMatch && pathMatcher.match(rule.getUrlPattern(), uri)) {
                matched = rule;
                break;
            }
        }

        if (matched == null) return true;

        PortalUser user = sessionUser;
        if (user == null) {
            sendForbidden(response, "로그인이 필요합니다");
            return false;
        }

        // ADMIN은 모든 권한 통과
        if ("ADMIN".equals(user.getRole())) return true;

        // 권한 체크
        if (!userPermissionService.hasPermission(user.getId(), matched.getPermissionKey())) {
            log.info("Permission denied: user={}, action={}, uri={}",
                    user.getUsername(), matched.getPermissionKey(), uri);
            try {
                adminNotificationService.notifyPermissionDenied(
                        user.getUsername(), user.getDisplayName(), matched.getPermissionKey(), uri, method);
            } catch (Exception e) {
                log.warn("권한 거부 Admin 알림 실패: {}", e.getMessage());
            }
            sendForbidden(response, "권한이 없습니다");
