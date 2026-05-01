// @source src/main/java/com/samsung/move/bitbucket/entity/BitbucketRepo.java
// @lines 1-67
// @note portal_bitbucket_repos — serverUrl/projectKey/repoSlug/PAT(평문)/autoDownload/controller
// @synced 2026-05-01T01:05:23.630Z

package com.samsung.move.bitbucket.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "portal_bitbucket_repos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BitbucketRepo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 500)
    private String serverUrl;

    @Column(nullable = false, length = 100)
    private String projectKey;

    @Column(nullable = false, length = 100)
    private String repoSlug;

    @Column(nullable = false, length = 500)
    private String pat;

    @Column(length = 100)
    private String controller;

    @Column(nullable = false, length = 500)
    @Builder.Default
    private String targetPath = "/appdata/samsung/OCTO_HEAD/FW_Code";

    @Column(nullable = false)
    @Builder.Default
    private boolean autoDownload = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastPolledAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
