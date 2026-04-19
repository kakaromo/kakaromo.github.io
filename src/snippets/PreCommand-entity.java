// @source src/main/java/com/samsung/move/head/precmd/entity/PreCommand.java
// @lines 1-50
// @note portal_pre_commands — 명령 템플릿 (name + commands JSON array)
// @synced 2026-04-19T08:19:17.630Z

package com.samsung.move.head.precmd.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "portal_pre_commands")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    /** JSON array of command strings, e.g. ["adb push file /dev", "adb shell chmod +x /dev/file"] */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String commands;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

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

