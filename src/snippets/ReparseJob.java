// @source src/main/java/com/samsung/move/testdb/reparse/ReparseJob.java
// @lines 1-56
// @note 비동기 리파싱 Job 상태 머신 — preparing/running/completed/failed
// @synced 2026-04-19T09:49:20.701Z

package com.samsung.move.testdb.reparse;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ReparseJob {

    private final String jobId;
    private final Long historyId;
    private final Long tcId;
    private final String tentacleName;
    private final String logDir;
    private final boolean nas;
    private final String parserName;

    private volatile String state = "preparing"; // preparing | running | completed | failed
    private volatile int totalFiles;
    private volatile int currentIndex;
    private volatile String currentFileName;
    private volatile String error;
    private final long startedAt;
    private volatile long updatedAt;

    public ReparseJob(Long historyId, Long tcId, String tentacleName, String logDir, boolean nas, String parserName) {
        this.jobId = UUID.randomUUID().toString();
        this.historyId = historyId;
        this.tcId = tcId;
        this.tentacleName = tentacleName;
        this.logDir = logDir;
        this.nas = nas;
        this.parserName = parserName;
        this.startedAt = System.currentTimeMillis();
        this.updatedAt = this.startedAt;
    }

    public void updateProgress(int index, String fileName) {
        this.currentIndex = index;
        this.currentFileName = fileName;
        this.updatedAt = System.currentTimeMillis();
    }

    public void complete() {
        this.state = "completed";
        this.updatedAt = System.currentTimeMillis();
    }

    public void fail(String error) {
        this.state = "failed";
        this.error = error;
        this.updatedAt = System.currentTimeMillis();
    }
}
