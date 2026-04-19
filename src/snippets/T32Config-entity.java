// @source src/main/java/com/samsung/move/t32/entity/T32Config.java
// @lines 1-88
// @note portal_t32_configs — 서버 그룹·JTAG/T32PC·명령 템플릿·경로 매핑
// @synced 2026-04-19T09:32:45.519Z

package com.samsung.move.t32.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "portal_t32_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class T32Config {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long serverGroupId;

    @Column(nullable = false)
    private Long jtagServerId;

    @Column(length = 100)
    private String jtagUsername;

    @Column(length = 255)
    private String jtagPassword;

    @Column(nullable = false)
    private Long t32PcId;

    @Column(length = 100)
    private String t32PcUsername;

    @Column(length = 255)
    private String t32PcPassword;

    @Column(length = 500)
    private String jtagCommand;

    @Column(length = 500)
    private String jtagSuccessPattern;

    @Column(length = 500)
    private String t32PortCheckCommand;

    @Column(length = 500)
    private String dumpCommand;

    @Column(length = 500)
    private String fwCodeLinuxBase;

    @Column(length = 500)
    private String fwCodeWindowsBase;

    @Column(length = 500)
    private String resultBasePath;

    @Column(length = 500)
    private String resultWindowsBasePath;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    private LocalDateTime createdAt;
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
