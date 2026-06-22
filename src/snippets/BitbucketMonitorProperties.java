// @source src/main/java/com/samsung/move/bitbucket/config/BitbucketMonitorProperties.java
// @lines 1-13
// @note @ConfigurationProperties bitbucket.monitor — enabled + defaultTargetPath
// @synced 2026-06-22T22:22:10.919Z

package com.samsung.move.bitbucket.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "bitbucket.monitor")
public class BitbucketMonitorProperties {

    private boolean enabled = true;

    private String defaultTargetPath = "/appdata/samsung/OCTO_HEAD/FW_Code";
}
