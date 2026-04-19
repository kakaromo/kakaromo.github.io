// @source src/main/java/com/samsung/move/bitbucket/config/BitbucketMonitorProperties.java
// @lines 1-13
// @note @ConfigurationProperties bitbucket.monitor — enabled + defaultTargetPath
// @synced 2026-04-19T08:48:08.179Z

package com.samsung.move.bitbucket.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "bitbucket.monitor")
public class BitbucketMonitorProperties {

    private boolean enabled = true;

    private String defaultTargetPath = "/appdata/samsung/OCTO_HEAD/FW_Code";
}
