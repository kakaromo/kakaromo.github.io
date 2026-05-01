// @source src/main/java/com/samsung/move/admin/controller/AdminController.java
// @lines 1-58
// @note AdminController 의존 15개 (health/cache/info/menu/vm/user/server/head/debug/permission/session/request 등)
// @synced 2026-05-01T01:10:31.191Z

package com.samsung.move.admin.controller;

import com.samsung.move.admin.entity.PortalServer;
import com.samsung.move.admin.entity.ServerGroup;
import com.samsung.move.admin.repository.ServerGroupRepository;
import com.samsung.move.admin.service.AdminCacheService;
import com.samsung.move.admin.service.AdminHealthService;
import com.samsung.move.admin.service.AdminInfoService;
import com.samsung.move.admin.service.AdminMenuService;
import com.samsung.move.admin.service.AdminVmStatusService;
import com.samsung.move.admin.service.PortalServerService;
import com.samsung.move.auth.entity.PermissionRequest;
import com.samsung.move.auth.entity.PortalUser;
import com.samsung.move.auth.entity.UserHeadAccess;
import com.samsung.move.auth.repository.PermissionRequestRepository;
import com.samsung.move.auth.repository.UserHeadAccessRepository;
import com.samsung.move.auth.service.AdminNotificationService;
import com.samsung.move.auth.service.PortalUserService;
import com.samsung.move.auth.service.SessionConfigService;
import com.samsung.move.auth.service.UserPermissionService;
import com.samsung.move.debug.entity.DebugTool;
import com.samsung.move.debug.entity.DebugType;
import com.samsung.move.debug.service.DebugToolService;
import com.samsung.move.head.entity.HeadConnection;
import com.samsung.move.head.service.HeadConnectionService;
import com.samsung.move.head.tcp.HeadConnectionManager;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminHealthService healthService;
    private final AdminCacheService cacheService;
    private final AdminInfoService infoService;
    private final AdminMenuService menuService;
    private final AdminVmStatusService vmStatusService;
    private final PortalUserService userService;
    private final PortalServerService serverService;
    private final HeadConnectionService headConnectionService;
    private final HeadConnectionManager headConnectionManager;
    private final DebugToolService debugToolService;
    private final ServerGroupRepository serverGroupRepository;
    private final UserPermissionService permissionService;
    private final UserHeadAccessRepository userHeadAccessRepository;
    private final SessionConfigService sessionConfigService;
    private final PermissionRequestRepository permissionRequestRepository;
