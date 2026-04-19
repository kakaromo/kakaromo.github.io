// @source src/main/java/com/samsung/move/auth/service/UserPermissionService.java
// @lines 1-55
// @note ALL_PERMISSIONS 17개 정의 (메뉴 8 + 액션 9)
// @synced 2026-04-19T09:18:51.179Z

package com.samsung.move.auth.service;

import com.samsung.move.auth.entity.UserPermission;
import com.samsung.move.auth.repository.UserPermissionRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserPermissionService {

    private final UserPermissionRepository repository;

    /**
     * 전체 권한 키 정의 (key → 설명)
     */
    public static final LinkedHashMap<String, String> ALL_PERMISSIONS = new LinkedHashMap<>();

    static {
        // 메뉴 권한
        ALL_PERMISSIONS.put("menu:dashboard", "Dashboard 메뉴");
        ALL_PERMISSIONS.put("menu:compatibility", "Compatibility 메뉴");
        ALL_PERMISSIONS.put("menu:performance", "Performance 메뉴");
        ALL_PERMISSIONS.put("menu:slots", "Slots 메뉴");
        ALL_PERMISSIONS.put("menu:remote", "Remote 메뉴");
        ALL_PERMISSIONS.put("menu:binMapper", "Bin Mapper 메뉴");
        ALL_PERMISSIONS.put("menu:storage", "Storage 메뉴");
        ALL_PERMISSIONS.put("menu:agent", "Agent 메뉴");

        // 세부 기능 권한
        ALL_PERMISSIONS.put("action:performance:excel_export", "Performance Excel 내보내기");
        ALL_PERMISSIONS.put("action:performance:compare", "Performance 비교");
        ALL_PERMISSIONS.put("action:remote:connect", "원격 접속");
        ALL_PERMISSIONS.put("action:slots:initslot", "Slot 초기화");
        ALL_PERMISSIONS.put("action:slots:pre_command", "Pre-Command 설정");
        ALL_PERMISSIONS.put("action:agent:benchmark", "벤치마크 실행");
        ALL_PERMISSIONS.put("action:agent:scenario", "시나리오 실행");
        ALL_PERMISSIONS.put("action:agent:trace", "Trace 수집");
        ALL_PERMISSIONS.put("action:storage:run", "Storage 실행");
    }

    /**
     * 사용자 권한 맵 조회 (key → granted)
     */
    public Map<String, Boolean> getPermissionMap(Long userId) {
        List<UserPermission> perms = repository.findByUserId(userId);
        Map<String, Boolean> map = new LinkedHashMap<>();
        // 기본값: 모든 권한 false
        for (String key : ALL_PERMISSIONS.keySet()) {
            map.put(key, false);
