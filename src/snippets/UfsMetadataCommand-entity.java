// @source src/main/java/com/samsung/move/metadata/entity/UfsMetadataCommand.java
// @lines 1-65
// @note ufs_metadata_commands — commandType 4가지(tool/sysfs/raw/keyvalue)
// @synced 2026-05-01T01:10:31.160Z

package com.samsung.move.metadata.entity;

import com.samsung.move.binmapper.entity.PredefinedStruct;
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
     *   commandTemplate에 줄바꿈으로 구분된 sysfs 경로 목록
     * - "keyvalue": "key: value" 들여쓰기 기반 파싱 (f2fs status 등, 장치 섹션 지원)
     * - "raw": 파일 내용을 key=basename 기준으로 그대로 저장
     * - "table": 3-column 섹션 표 파싱 (f2fs iostat_info 등)
     * - "bitmap": 대용량 비트맵/그리드 파일(segment_info, victim_bits, segment_bits) lines 배열로 저장
     * - "binary": adb shell로 tool 실행 → /dev/debug_xxx.bin 생성 → adb pull → struct 매핑 → flatten JSON
     */
    @Column(name = "command_type", nullable = false, length = 20)
    @Builder.Default
    private String commandType = "tool";

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "debug_tool_id")
    private DebugTool debugTool;

    @Column(length = 500)
    private String description;

    /**
     * "binary" command_type 전용: 바이너리를 파싱할 struct 정의 (C/C++ struct 텍스트).
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "predefined_struct_id")
    private PredefinedStruct predefinedStruct;

    /**
     * "binary" command_type 전용: 디바이스 내 바이너리 출력 경로.
     * 예: /dev/debug_ssr.bin — adb shell 내 tool이 이 경로에 저장해야 함.
     */
    @Column(name = "binary_output_path", length = 500)
