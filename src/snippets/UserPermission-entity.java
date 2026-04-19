// @source src/main/java/com/samsung/move/auth/entity/UserPermission.java
// @lines 1-50
// @note user_permissions — (userId, permissionKey) unique + granted boolean (17 key per user)
// @synced 2026-04-19T10:15:34.678Z

package com.samsung.move.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "portal_user_permissions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "permission_key"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "permission_key", nullable = false, length = 100)
    private String permissionKey;

    @Column(nullable = false)
    @Builder.Default
    private boolean granted = true;

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
