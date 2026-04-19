// @source src/main/java/com/samsung/move/minio/config/MinioProperties.java
// @lines 1-17
// @note @ConfigurationProperties minio — endpoint / port / credentials
// @synced 2026-04-19T08:33:48.686Z

package com.samsung.move.minio.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "minio")
@Getter
@Setter
public class MinioProperties {
    private String endpoint = "http://127.0.0.1";
    private int port = 9000;
    private boolean useSsl = false;
    private String accessKey = "admin";
    private String secretKey = "changeme";
}

