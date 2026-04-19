// @source src/main/java/com/samsung/move/testdb/controller/DashboardController.java
// @lines 1-119
// @note /api/dashboard/stats — 두 도메인 공통 집계 (trCount/passFail/byFw/byTc/recent)
// @synced 2026-04-19T09:18:51.183Z

package com.samsung.move.testdb.controller;

import com.samsung.move.testdb.entity.*;
import com.samsung.move.testdb.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final CompatibilityHistoryRepository compatHistoryRepo;
    private final CompatibilityTestRequestRepository compatTrRepo;
    private final CompatibilityTestCaseRepository compatTcRepo;
    private final PerformanceHistoryRepository perfHistoryRepo;
    private final PerformanceTestRequestRepository perfTrRepo;
    private final PerformanceTestCaseRepository perfTcRepo;
    private final SetInfomationRepository setRepo;

    @GetMapping("/stats")
    @Transactional(readOnly = true, transactionManager = "testdbTransactionManager")
    public Map<String, Object> getStats() {
        Map<String, Object> result = new LinkedHashMap<>();

        // --- Compatibility ---
        Map<String, Object> compat = new LinkedHashMap<>();

        // TR/TC 목록 (소량 데이터)
        List<CompatibilityTestRequest> compatTrs = compatTrRepo.findAll();
        List<CompatibilityTestCase> compatTcs = compatTcRepo.findAll();
        compat.put("trCount", compatTrs.size());
        compat.put("tcCount", compatTcs.size());

        // 결과별 카운트 (DB 집계)
        Map<String, Long> compatResultCounts = aggregateResultCounts(compatHistoryRepo.countByResult());
        compat.put("totalCount", compatResultCounts.values().stream().mapToLong(Long::longValue).sum());
        compat.put("passCount", compatResultCounts.getOrDefault("PASS", 0L));
        compat.put("failCount", compatResultCounts.getOrDefault("FAIL", 0L));

        // TR별 pass/fail 집계
        Map<Long, CompatibilityTestRequest> compatTrMap = new HashMap<>();
        compatTrs.forEach(tr -> compatTrMap.put(tr.getId(), tr));
        compat.put("byFw", buildTrSummary(compatHistoryRepo.countByTrAndResult(), compatTrMap));

        // TC별 pass/fail 집계
        Map<Long, String> compatTcNameMap = new HashMap<>();
        compatTcs.forEach(tc -> compatTcNameMap.put(tc.getId(), tc.getName()));
        compat.put("byTc", buildTcSummary(compatHistoryRepo.countByTcAndResult(), compatTcNameMap));

        // 최근 10건
        List<Map<String, Object>> compatRecent = new ArrayList<>();
        for (CompatibilityHistory h : compatHistoryRepo.findTop10ByOrderByIdDesc()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", h.getId());
            row.put("result", h.getResult());
            row.put("trId", h.getTrId());
            row.put("tcId", h.getTcId());
            row.put("setProductName", h.getSetProductName());
            CompatibilityTestRequest tr = h.getTrId() != null ? compatTrMap.get(h.getTrId()) : null;
            row.put("trName", tr != null ? tr.getName() : null);
            row.put("trFw", tr != null ? tr.getFw() : null);
            row.put("tcName", h.getTcId() != null ? compatTcNameMap.get(h.getTcId()) : null);
            compatRecent.add(row);
        }
        compat.put("recent", compatRecent);

        result.put("compatibility", compat);

        // --- Performance ---
        Map<String, Object> perf = new LinkedHashMap<>();

        List<PerformanceTestRequest> perfTrs = perfTrRepo.findAll();
        List<PerformanceTestCase> perfTcs = perfTcRepo.findAll();
        perf.put("trCount", perfTrs.size());
        perf.put("tcCount", perfTcs.size());

        Map<String, Long> perfResultCounts = aggregateResultCounts(perfHistoryRepo.countByResult());
        perf.put("totalCount", perfResultCounts.values().stream().mapToLong(Long::longValue).sum());
        perf.put("passCount", perfResultCounts.getOrDefault("PASS", 0L));
        perf.put("failCount", perfResultCounts.getOrDefault("FAIL", 0L));

        Map<Long, PerformanceTestRequest> perfTrMap = new HashMap<>();
        perfTrs.forEach(tr -> perfTrMap.put(tr.getId(), tr));
        perf.put("byFw", buildTrSummary(perfHistoryRepo.countByTrAndResult(), perfTrMap));

        Map<Long, String> perfTcNameMap = new HashMap<>();
        perfTcs.forEach(tc -> perfTcNameMap.put(tc.getId(), tc.getName()));
        perf.put("byTc", buildTcSummary(perfHistoryRepo.countByTcAndResult(), perfTcNameMap));

        // 최근 10건
        List<Map<String, Object>> perfRecent = new ArrayList<>();
        Map<Long, SetInfomation> setMap = new HashMap<>();
        setRepo.findAll().forEach(s -> setMap.put(s.getId(), s));
        for (PerformanceHistory h : perfHistoryRepo.findTop10ByOrderByIdDesc()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", h.getId());
            row.put("result", h.getResult());
            row.put("trId", h.getTrId());
            row.put("tcId", h.getTcId());
            row.put("setId", h.getSetId());
            PerformanceTestRequest tr = h.getTrId() != null ? perfTrMap.get(h.getTrId()) : null;
            row.put("trFw", tr != null ? tr.getFw() : null);
            row.put("tcName", h.getTcId() != null ? perfTcNameMap.get(h.getTcId()) : null);
            SetInfomation set = h.getSetId() != null ? setMap.get(h.getSetId()) : null;
            row.put("setName", set != null ? set.getProductName() : null);
            perfRecent.add(row);
        }
        perf.put("recent", perfRecent);

        result.put("performance", perf);

        return result;
    }
