// @source src/main/java/com/samsung/move/binmapper/entity/PredefinedStruct.java
// @lines 1-48
// @note predefined_structs — name/category/structText(TEXT) 저장, 재사용 가능한 struct 사전
// @synced 2026-04-19T10:15:34.670Z

package com.samsung.move.binmapper.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "predefined_structs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredefinedStruct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String category;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String structText;

    private String description;

    @Column(updatable = false)
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
