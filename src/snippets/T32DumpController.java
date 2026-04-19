// @source src/main/java/com/samsung/move/t32/controller/T32DumpController.java
// @lines 1-52
// @note POST /api/t32/dump/execute — SSE 반환 + DumpRequest record
// @synced 2026-04-19T09:18:51.165Z

package com.samsung.move.t32.controller;

import com.samsung.move.t32.service.T32DumpService;
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

    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkAvailability(@RequestParam Long serverId) {
        T32DumpService.T32Availability result = dumpService.checkAvailability(serverId);
        return ResponseEntity.ok(Map.of(
                "available", result.available(),
                "configId", result.configId() != null ? result.configId() : 0
        ));
    }

    @PostMapping(value = "/execute", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeDump(@RequestBody DumpRequest request) {
        return dumpService.executeDump(
                request.serverId(),
                request.tentacleName(),
                request.slotNumber(),
                request.fw(),
                request.branchPath(),
                request.setLocation(),
                request.testToolName(),
                request.testTrName()
        );
    }

    record DumpRequest(
            Long serverId,
            String tentacleName,
            int slotNumber,
            String fw,
            String branchPath,
            String setLocation,
            String testToolName,
            String testTrName
    ) {}
}
