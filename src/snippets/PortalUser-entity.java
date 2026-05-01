// @source src/main/java/com/samsung/move/auth/entity/PortalUser.java
// @lines 1-62
// @note portal_users — username / adfsUserId / role(USER/ADMIN) / enabled
// @synced 2026-05-01T01:05:23.635Z

package com.samsung.move.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "portal_users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    /** ADFS 고유 ID (변경 안 됨). AD 사용자 식별 키. */
    @Column(name = "adfs_user_id", unique = true, length = 100)
    private String adfsUserId;

    @Column(nullable = true)
    private String password;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(length = 200)
    private String email;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String role = "USER";

    @Builder.Default
    private boolean enabled = true;

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
