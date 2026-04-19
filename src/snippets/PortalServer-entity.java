// @source src/main/java/com/samsung/move/admin/entity/PortalServer.java
// @lines 1-79
// @note portal_servers — 접속 대상 서버 + ssh/rdp/vnc 포트 + guacd_host/port
// @synced 2026-04-19T10:15:34.650Z

package com.samsung.move.admin.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "portal_servers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalServer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "server_group_id")
    private Long serverGroupId;

    @Column(nullable = false, length = 100)
    private String ip;

    @Column(length = 100)
    private String username;

    private String password;

    @Column(name = "ssh_port")
    @Builder.Default
    private int sshPort = 22;

    @Column(name = "rdp_port")
    @Builder.Default
    private int rdpPort = 3389;

    @Column(name = "vnc_port")
    @Builder.Default
    private int vncPort = 5901;

    @Column(name = "connection_type", nullable = false)
    @Builder.Default
    private int connectionType = 0;

    @Column(name = "is_visible", nullable = false)
    @Builder.Default
    private boolean visible = true;

    @Column(name = "guacd_host", length = 100)
    private String guacdHost;

    @Column(name = "guacd_port")
    private Integer guacdPort;

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
