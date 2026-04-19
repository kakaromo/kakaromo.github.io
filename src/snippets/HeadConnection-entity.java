// @source src/main/java/com/samsung/move/head/entity/HeadConnection.java
// @lines 1-78
// @note portal_head_connections 엔티티 + 포트 계산 헬퍼
// @synced 2026-04-19T08:19:17.616Z

package com.samsung.move.head.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "portal_head_connections")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeadConnection {

    /** Head 연결 타입: 0=호환성, 1=성능, 2+=향후 확장 */
    public static final int TYPE_COMPATIBILITY = 0;
    public static final int TYPE_PERFORMANCE = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "head_type", nullable = false)
    @Builder.Default
    private int headType = 0;

    @Column(nullable = false, length = 100)
    private String host;

    @Column(name = "port_suffix", nullable = false, length = 10)
    private String portSuffix;

    @Column(name = "listen_port_suffix", nullable = false, length = 10)
    private String listenPortSuffix;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "test_mode", nullable = false)
    @Builder.Default
    private boolean testMode = false;

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

    public int getPort() {
        return 10000 + Integer.parseInt(portSuffix);
    }

    public int getListenPort() {
        return 10000 + Integer.parseInt(listenPortSuffix);
    }

    public boolean isCompatibility() { return headType == TYPE_COMPATIBILITY; }
    public boolean isPerformance() { return headType == TYPE_PERFORMANCE; }
}
