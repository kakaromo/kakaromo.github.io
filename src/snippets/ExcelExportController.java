// @source src/main/java/com/samsung/move/testdb/controller/ExcelExportController.java
// @lines 1-34
// @note GET /{historyId}/excel — data fetch + gRPC call + ResponseEntity<byte[]>
// @synced 2026-04-19T08:33:48.684Z

package com.samsung.move.testdb.controller;

import com.samsung.move.testdb.excel.ExcelGrpcClient;
import com.samsung.move.testdb.service.PerformanceResultDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/performance-results")
@RequiredArgsConstructor
public class ExcelExportController {

    private final PerformanceResultDataService resultDataService;
    private final ExcelGrpcClient excelGrpcClient;

    @GetMapping("/{historyId}/excel")
    public ResponseEntity<byte[]> exportExcel(@PathVariable Long historyId) throws Exception {
        var result = resultDataService.fetchResultData(historyId);
        String dataJson = result.rawJson();

        var response = excelGrpcClient.generateExcel(
                result.parserId(), result.tcName(), result.fw(), result.setName(), result.fileSystem(), dataJson);

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + response.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(response.getXlsxData().toByteArray());
    }
}
