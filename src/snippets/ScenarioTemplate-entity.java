// @source src/main/java/com/samsung/move/agent/entity/ScenarioTemplate.java
// @lines 1-58
// @note portal_scenario_templates — name/description/repeatCount + stepsJson/loopsJson(TEXT) 이원 JSON
// @synced 2026-05-01T01:05:23.648Z

package com.samsung.move.agent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "portal_scenario_templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioTemplate implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "repeat_count", nullable = false)
    @Builder.Default
    private int repeatCount = 1;

    @Lob
    @Column(name = "steps_json", nullable = false, columnDefinition = "TEXT")
    private String stepsJson;

    @Lob
    @Column(name = "loops_json", columnDefinition = "TEXT")
    private String loopsJson;

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
