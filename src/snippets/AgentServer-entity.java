// @source src/main/java/com/samsung/move/agent/entity/AgentServer.java
// @lines 1-57
// @note portal_agent_servers — Agent gRPC 서버 등록
// @synced 2026-05-01T01:05:23.612Z

package com.samsung.move.agent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "portal_agent_servers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentServer implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String host;

    @Column(nullable = false)
    @Builder.Default
    private int port = 50051;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(length = 500)
    private String description;

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
