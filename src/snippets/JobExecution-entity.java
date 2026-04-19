// @source src/main/java/com/samsung/move/agent/entity/JobExecution.java
// @lines 1-81
// @note portal_job_executions — job 이력 (벤치마크/시나리오/Trace 공용)
// @synced 2026-04-19T07:53:32.282Z

package com.samsung.move.agent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "portal_job_executions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobExecution implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String jobId;

    @Column(nullable = false)
    private Long serverId;

    @Column(length = 100)
    private String serverName;

    @Column(nullable = false, length = 20)
    private String type; // benchmark, scenario, trace

    @Column(length = 20)
    private String tool; // FIO, IOZONE, TIOTEST

    @Column(length = 200)
    private String jobName;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String deviceIds; // JSON array

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String state = "running";

    @Lob
    @Column(columnDefinition = "TEXT")
    private String config; // JSON — 실행 파라미터 전체

    @Lob
    @Column(columnDefinition = "TEXT")
    private String resultSummary; // JSON — 주요 메트릭 요약

    private Long scheduledJobId; // nullable — 스케줄에 의한 실행이면 연결

    @Builder.Default
    private int retryAttempt = 0;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (startedAt == null) startedAt = createdAt;
    }
}
