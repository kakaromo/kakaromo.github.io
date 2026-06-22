// @source src/main/java/com/samsung/move/t32/controller/T32DumpController.java
// @lines 1-80
// @note POST /execute(SSE) + /cancel + /check — DumpRequest record + 점유 lock 키 산출
// @synced 2026-06-22T22:22:10.906Z

package com.samsung.move.t32.controller;

import com.samsung.move.auth.entity.PortalUser;
import com.samsung.move.t32.service.T32DumpService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/t32/dump")
@RequiredArgsConstructor
public class T32DumpController {

    private final T32DumpService dumpService;

    // 사용자가 "중단"을 누르면 호출. 진행 중인 dump 워커를 interrupt 해 lock·세션을 즉시
    // 정리한다. SSE fetch abort 만으로는 백엔드가 끊김을 늦게(또는 못) 감지하므로 명시적 호출.
    @PostMapping("/cancel")
    public ResponseEntity<Map<String, Object>> cancelDump(@RequestParam Long serverId, HttpSession session) {
        PortalUser user = (PortalUser) session.getAttribute("portalUser");
        String userKey = user != null && user.getId() != null ? String.valueOf(user.getId()) : session.getId();
        boolean cancelled = dumpService.cancelDump(serverId, userKey, true);
        return ResponseEntity.ok(Map.of("cancelled", cancelled));
    }

    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkAvailability(@RequestParam Long serverId) {
        T32DumpService.T32Availability result = dumpService.checkAvailability(serverId);
        return ResponseEntity.ok(Map.of(
                "available", result.available(),
                "configId", result.configId() != null ? result.configId() : 0
        ));
    }

    // charset 을 명시하지 않으면 SSE 한글 메시지(Step 실패 사유 등)가 깨져서 전달된다.
    @PostMapping(value = "/execute", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter executeDump(@RequestBody DumpRequest request, HttpSession session) {
        // 누가 dump 하는지(단독 점유 lock 점유자/거부 안내에 사용). 세션 사용자(표준 패턴).
        PortalUser user = (PortalUser) session.getAttribute("portalUser");
        String userKey = user != null && user.getId() != null ? String.valueOf(user.getId()) : session.getId();
        String displayName = user == null ? "알 수 없음"
                : (user.getDisplayName() != null ? user.getDisplayName() : user.getUsername());
        return dumpService.executeDump(
                request.serverId(),
                request.tentacleName(),
                request.slotNumber(),
                request.fw(),
                request.branchPath(),
                request.setLocation(),
                request.testToolName(),
                request.testTrName(),
                request.historyId(),
                request.testType(),
                request.source(),
                userKey,
                displayName
        );
    }

    // historyId/testType/source: dump 결과를 호환성/성능 history 와 매칭해 S3 업로드·기록할 때 사용.
    // 셋 다 nullable — history 컨텍스트가 없는 호출에서는 업로드는 하되 historyId NULL 로 둔다.
    record DumpRequest(
            Long serverId,
            String tentacleName,
            int slotNumber,
            String fw,
            String branchPath,
            String setLocation,
            String testToolName,
            String testTrName,
            Long historyId,
            String testType,
            String source
    ) {}
}
