// @source src/main/java/com/samsung/move/metadata/config/MetadataMonitorProperties.java
// @lines 1-21
// @note metadata.monitor.* — enabled / pollInterval / collectionInterval
// @synced 2026-04-19T10:15:34.652Z

package com.samsung.move.metadata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "metadata.monitor")
public class MetadataMonitorProperties {

    private boolean enabled = true;

    /** 슬롯 상태 체크 간격 (ms) */
    private long pollIntervalMs = 5000;

    /** 모니터링 간격 (분, 기본값) */
    private int collectionIntervalMin = 5;

    /** 모니터링 파일 저장 기본 경로 */
    private String outputBaseDir = "/home/octo/tentacle";
}

