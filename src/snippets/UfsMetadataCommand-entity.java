// @source src/main/java/com/samsung/move/metadata/entity/UfsMetadataCommand.java
// @lines 1-65
// @note ufs_metadata_commands — commandType 4가지(tool/sysfs/raw/keyvalue)
// @synced 2026-04-19T09:49:20.681Z

package com.samsung.move.metadata.entity;

import com.samsung.move.debug.entity.DebugTool;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ufs_metadata_commands")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UfsMetadataCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "metadata_type_id", nullable = false)
    private UfsMetadataType metadataType;

    @Column(name = "command_template", nullable = false, length = 1000)
    private String commandTemplate;

    /**
     * 명령어 유형:
     * - "tool" (기본값): adb shell로 tool/명령어 실행, JSON 출력 기대
     * - "sysfs": adb shell cat으로 sysfs 경로 읽기, plaintext → JSON 변환
     *   commandTemplate에 줄바꿈으로 구분된 sysfs 경로 목록 (e.g. "/sys/block/sda/stat\n/sys/block/sda/size")
     */
    @Column(name = "command_type", nullable = false, length = 20)
    @Builder.Default
    private String commandType = "tool";

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "debug_tool_id")
    private DebugTool debugTool;

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

