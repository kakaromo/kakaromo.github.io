// @source src/main/java/com/samsung/move/head/precmd/entity/SlotPreCommand.java
// @lines 1-55
// @note portal_slot_pre_commands — setLocation(UK) + preCommand + tcPreCommandIds CSV
// @synced 2026-04-19T10:15:34.655Z

package com.samsung.move.head.precmd.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 슬롯별 사전 명령어 등록.
 * key = setLocation (예: "T3-0", "T4-1") — 슬롯을 유일하게 식별
 *
 * - preCommand: 슬롯 Pre-Command (init 시 항상 실행, TC Pre-Command가 없을 때)
 * - tcPreCommandIds: TC별 Pre-Command ID 목록 (testcaseIds와 1:1 대응, "0,3,0,5,0" 형식)
 *   0이면 미등록, 숫자면 portal_pre_commands.id
 *   testcaseIds 변경 시 동기화됨 (추가/삭제/순서 변경)
 *
 * 우선순위: TC Pre-Command > 슬롯 Pre-Command
 */
@Entity
@Table(name = "portal_slot_pre_commands",
        uniqueConstraints = @UniqueConstraint(columnNames = {"set_location"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotPreCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "set_location", nullable = false, length = 100)
    private String setLocation;

    /** 슬롯 Pre-Command (nullable — TC Pre-Command만 사용할 수도 있음) */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "pre_command_id")
    private PreCommand preCommand;

    /** TC별 Pre-Command ID 목록. testcaseIds와 1:1 대응. "0,3,0,5,0" 형식 (0=미등록) */
    @Column(name = "tc_pre_command_ids", columnDefinition = "TEXT")
    private String tcPreCommandIds;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

