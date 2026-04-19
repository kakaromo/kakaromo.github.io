// @source src/main/java/com/samsung/move/minio/config/MinioConfig.java
// @lines 1-18
// @note MinioClient.builder() — io.minio:minio 8.5.14 SDK 래퍼
// @synced 2026-04-19T08:48:08.181Z

package com.samsung.move.minio.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(MinioProperties props) {
        return MinioClient.builder()
                .endpoint(props.getEndpoint(), props.getPort(), props.isUseSsl())
                .credentials(props.getAccessKey(), props.getSecretKey())
                .build();
    }
}

