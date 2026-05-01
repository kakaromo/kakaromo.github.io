// @source src/main/java/com/samsung/move/agent/entity/IOTestPreset.java
// @lines 1-38
// @note portal_iotest_presets — name/description/category/configJson(MEDIUMTEXT) 단일 컬럼 DSL 저장
// @synced 2026-05-01T01:05:23.646Z

package com.samsung.move.agent.entity;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "portal_iotest_presets")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class IOTestPreset implements Serializable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 50)
    private String category; // "Basic I/O", "Random/Stress", "Data Integrity", "File Management", "Concurrent", "Device Control"

    @Lob @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
    private String configJson; // IOTestConfig JSON

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
