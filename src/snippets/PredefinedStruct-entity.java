// @source src/main/java/com/samsung/move/binmapper/entity/PredefinedStruct.java
// @lines 1-48
// @note predefined_structs — name/category/structText(TEXT) 저장, 재사용 가능한 struct 사전
// @synced 2026-05-01T01:05:23.640Z

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

    /**
     * 용도 구분:
     *   "metadata" — Metadata binary command_type 전용
     *   "dlm"      — DLM 바이너리 파싱 전용
     *   "general"  — 범용 / devtools 수동 사용
     */
    @Column(length = 20, nullable = false)
    @Builder.Default
    private String kind = "general";

    @Column(columnDefinition = "TEXT", nullable = false)
    private String structText;

    private String description;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
