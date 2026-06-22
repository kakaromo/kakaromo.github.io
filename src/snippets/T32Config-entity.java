// @source src/main/java/com/samsung/move/t32/entity/T32Config.java
// @note portal_t32_configs — 19 컬럼: 2 서버 ID(JTAG·T32PC) + 전용 계정 + 4 명령 + 4 경로 baseline
// @synced 2026-06-22T22:22:10.942Z

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

    /** T32 PowerView 자동 실행 명령 (예: C:\T32\bin\windows64\t32mp64.exe -c C:\T32\config.t32).
     *  Step2 에서 프로세스가 없으면 SSH 로 이 명령을 실행한 뒤 attach 를 재시도한다. */
    @Column(name = "t32_start_command", length = 500)
    private String t32StartCommand;

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

    @Column(name = "t32_remote_host", length = 255)
    private String t32RemoteHost;

    @Column(name = "t32_remote_port")
    private Integer t32RemotePort;

    @Column(name = "core_scripts_json", columnDefinition = "TEXT")
    private String coreScriptsJson;

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

