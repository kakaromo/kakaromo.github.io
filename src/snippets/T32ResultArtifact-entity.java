// @source src/main/java/com/samsung/move/t32/entity/T32ResultArtifact.java
// @lines 1-97
// @note portal_t32_result_artifacts — (testType,source,historyId) 튜플 + status + objectKey
// @synced 2026-06-22T22:22:10.911Z

package com.samsung.move.t32.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * T32 dump 결과(zip)를 S3(MinIO)에 업로드한 뒤 그 위치를 기록하는 메타데이터.
 *
 * 호환성/성능 테스트의 history row 와 {@code (testType, source, historyId)} 튜플로 매칭한다.
 * History 테이블은 {@code testdb} 데이터소스에 있어 cross-datasource FK 를 걸 수 없고,
 * 향후 외주(OUTSOURCED) 성능/호환성이 추가될 수 있어 하드 FK 대신 튜플 참조를 쓴다.
 *
 * 60 일 보관: {@link com.samsung.move.t32.service.T32ResultCleanupTask} 가 {@code uploadedAt}
 * 기준으로 S3 객체와 이 row 를 함께 삭제한다.
 */
@Entity
@Table(name = "portal_t32_result_artifacts",
        indexes = {
                @Index(name = "idx_t32_artifact_history", columnList = "testType,source,historyId"),
                @Index(name = "idx_t32_artifact_uploaded_at", columnList = "uploadedAt")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class T32ResultArtifact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 호환성(COMPATIBILITY) / 성능(PERFORMANCE). */
    @Enumerated(EnumType.STRING)
    @Column(name = "testType", nullable = false, length = 16)
    private T32TestType testType;

    /** 내부(INTERNAL) / 외주(OUTSOURCED). 지금은 항상 INTERNAL, 외주 대비 컬럼만 선반영. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private T32ResultSource source;

    /**
     * 매칭 대상 history 의 ID (CompatibilityHistory / PerformanceHistory 의 ID).
     * dump 호출 시점에 history 컨텍스트가 없으면 NULL — 보조 컬럼으로 사후 매칭.
     */
    @Column(name = "historyId")
    private Long historyId;

    @Column(name = "bucket", nullable = false, length = 64)
    private String bucket;

    /** S3 object key (zip). 예: {@code COMPATIBILITY/INTERNAL/{historyId}/{resultDirName}.zip}. */
    @Column(name = "objectKey", nullable = false, length = 1024)
    private String objectKey;

    @Column(name = "sizeBytes")
    private Long sizeBytes;

    /** 원본 결과 폴더명({datetime}_{setLocation}_{testToolName}_{testTrName}) — 추적/디버그용. */
    @Column(name = "resultDirName", length = 512)
    private String resultDirName;

    /** 식별 보조(historyId NULL 일 때 사후 매칭, 디버그용). */
    @Column(name = "setLocation", length = 256)
    private String setLocation;

    @Column(name = "testToolName", length = 256)
    private String testToolName;

    @Column(name = "testTrName", length = 256)
    private String testTrName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private T32ResultArtifactStatus status;

    /** 에러 메시지(status=FAILED 일 때). */
    @Column(name = "errorMessage", columnDefinition = "TEXT")
    private String errorMessage;

    /** 업로드 완료 시각 — 60 일 보관 기준. */
    @Column(name = "uploadedAt")
    private LocalDateTime uploadedAt;

    @Column(name = "createdAt", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
