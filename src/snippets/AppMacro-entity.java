// @source src/main/java/com/samsung/move/agent/entity/AppMacro.java
// @note portal_app_macros — eventsJson MEDIUMTEXT (단일 컬럼 JSON 저장 패턴 반복)
// @synced 2026-05-01T01:10:31.193Z

package com.samsung.move.agent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "portal_app_macros")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppMacro implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "package_name", length = 200)
    private String packageName;

    @Lob
    @Column(name = "events_json", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String eventsJson;

    @Column(name = "device_width")
    private Integer deviceWidth;

    @Column(name = "device_height")
    private Integer deviceHeight;

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

