// @source src/main/java/com/samsung/move/bitbucket/entity/BitbucketBranch.java
// @lines 1-48
// @note portal_bitbucket_branches — status(DETECTED/DOWNLOADING/DOWNLOADED/FAILED) + filePath + commitDate
// @synced 2026-04-19T09:18:51.174Z

package com.samsung.move.bitbucket.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "portal_bitbucket_branches")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BitbucketBranch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long repoId;

    @Column(nullable = false, length = 500)
    private String branchName;

    @Column(length = 100)
    private String latestCommitId;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "DOWNLOADING";

    @Column(length = 500)
    private String filePath;

    @Builder.Default
    private long fileSizeBytes = 0;

    private LocalDateTime commitDate;

    private LocalDateTime downloadedAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;
}
