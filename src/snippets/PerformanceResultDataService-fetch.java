// @source src/main/java/com/samsung/move/testdb/service/PerformanceResultDataService.java
// @lines 1-161
// @note fetchResultData — history→TC→parser 경로 해석 + COLLECTING + ResultData record
// @synced 2026-04-19T10:15:34.668Z

package com.samsung.move.testdb.service;

import com.samsung.move.logbrowser.service.LogBrowserService;
import com.samsung.move.testdb.entity.PerformanceHistory;
import com.samsung.move.testdb.entity.PerformanceParser;
import com.samsung.move.testdb.entity.PerformanceTestCase;
import com.samsung.move.testdb.entity.PerformanceTestRequest;
import com.samsung.move.testdb.entity.SetInfomation;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceResultDataService {

    private final PerformanceHistoryService historyService;
    private final PerformanceTestCaseService testCaseService;
    private final PerformanceParserService parserService;
    private final PerformanceTestRequestService testRequestService;
    private final SetInfomationService setInfomationService;
    private final LogBrowserService logBrowserService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${tentacle.log-prefix:/home/octo/tentacle}")
    private String logPrefix;

    @Value("${tentacle.head.log-path:/home/octo/nas}")
    private String headLogPath;

    public record ResultData(Long parserId, String parserName, String tcName, String fw, String setName, String fileSystem, String rawJson, boolean partial) {}

    public ResultData fetchResultData(Long historyId) throws Exception {
        PerformanceHistory history = historyService.findById(historyId);
        if (history == null) {
            throw new IllegalArgumentException("History not found: " + historyId);
        }

        Long tcId = history.getTcId();
        if (tcId == null) {
            throw new IllegalArgumentException("History has no TC ID");
        }

        PerformanceTestCase testCase = testCaseService.findById(tcId);
        if (testCase == null) {
            throw new IllegalArgumentException("TestCase not found");
        }
        if (testCase.getParserId() == null) {
            throw new IllegalArgumentException("TestCase has no parser ID");
        }

        PerformanceParser parser = parserService.findById(testCase.getParserId());
        if (parser == null) {
            throw new IllegalArgumentException("Parser not found");
        }

        String logPath = history.getLogPath();
        String slotLocation = history.getSlotLocation();
        if (slotLocation == null) {
            throw new IllegalArgumentException("History has no slot location");
        }

        String tentacleName;
        String remotePath;

        if (logPath != null && !logPath.isBlank()) {
            // 완료된 TC: logPath 기반 경로
            int lastSlash = logPath.lastIndexOf('/');
            String dirPath = lastSlash > 0 ? logPath.substring(0, lastSlash) : logPath;

            if (logPath.contains("UFS")) {
                tentacleName = "HEAD";
                remotePath = headLogPath + "/NAS/" + dirPath + "/" + parser.getName() + ".json";
            } else {
                tentacleName = slotLocation.replaceAll("^(T\\d+).*", "$1");
                remotePath = logPrefix + "/history/" + dirPath + "/" + parser.getName() + ".json";
            }
        } else {
            // Running 중인 TC: slotLocation 기반 경로
            // slotLocation = "T10-4" → tentacle "T10", slot "4"
            // 경로: /home/octo/tentacle/slot4/log/{parser}.json
            tentacleName = slotLocation.replaceAll("^(T\\d+).*", "$1");
            String slotNum = slotLocation.replaceAll("^T\\d+-", "");
            remotePath = logPrefix + "/slot" + slotNum + "/log/" + parser.getName() + ".json";
        }

        log.info("Reading JSON: tentacle={}, path={}", tentacleName, remotePath);
        String json = logBrowserService.readFileContent(tentacleName, remotePath);
        if (json == null || json.isBlank()) {
            String histResult = history.getResult();
            if ("RUNNING".equalsIgnoreCase(histResult)) {
                throw new IllegalArgumentException("COLLECTING:" + remotePath);
            }
            throw new IllegalArgumentException("JSON file is empty: " + remotePath);
        }
        String trimmed = json.trim();
        log.debug("JSON content length={}, first100={}, last100={}",
                trimmed.length(),
                trimmed.substring(0, Math.min(100, trimmed.length())),
                trimmed.substring(Math.max(0, trimmed.length() - 100)));
        // JSON 유효성 검증 — 실패 시 복구 시도 (RUNNING 중 불완전 JSON 허용)
        boolean partial = false;
        try {
            objectMapper.readTree(trimmed);
        } catch (Exception e) {
            log.warn("JSON parse failed, attempting repair [path={}]: {}", remotePath, e.getMessage());
            String repaired = tryRepairJson(trimmed);
            if (repaired != null) {
                trimmed = repaired;
                partial = true;
                log.info("JSON repaired successfully [path={}]", remotePath);
            } else {
                // RUNNING 상태이면 수집 중으로 판단
                String histResult = history.getResult();
                if ("RUNNING".equalsIgnoreCase(histResult)) {
                    throw new IllegalArgumentException("COLLECTING:" + remotePath);
                }
                log.error("JSON parse failed [path={}]: length={}, error={}", remotePath, trimmed.length(), e.getMessage());
                throw new IllegalArgumentException("JSON 파싱 실패 (" + remotePath + "): " + e.getMessage());
            }
        }

        // Look up FW from test request
        String fw = "";
        Long trId = history.getTrId();
        if (trId != null) {
            PerformanceTestRequest tr = testRequestService.findById(trId);
            if (tr != null) {
                fw = tr.getFw();
            }
        }

        // Look up set name (modelName) from SetInfomation
        String setName = "";
        Long setId = history.getSetId();
        if (setId != null) {
            try {
                SetInfomation setInfo = setInfomationService.findById(setId);
                if (setInfo != null && setInfo.getModelName() != null) {
                    setName = setInfo.getModelName();
                }
            } catch (Exception ignored) {
            }
        }

        String fileSystem = history.getFileSystem() != null ? history.getFileSystem() : "";

        return new ResultData(
                parser.getId(),
                parser.getName() != null ? parser.getName() : "",
                testCase.getName() != null ? testCase.getName() : "",
                fw != null ? fw : "",
                setName,
                fileSystem,
                trimmed,
                partial
        );
    }
