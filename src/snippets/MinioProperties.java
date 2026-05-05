// @source src/main/java/com/samsung/move/minio/config/MinioProperties.java
// @lines 1-31
// @note @ConfigurationProperties minio — 8 필드 (기본 5 + presigned/multipart 3 + region)
// @synced 2026-05-05

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

    /**
     * 브라우저가 presigned URL 로 접근할 때 사용할 호스트 (예: https://memo.samsungds.net/minio-upload).
     * null/blank 이면 내부 endpoint (브라우저에서 닿을 수 있을 때만 사용 가능).
     */
    private String publicEndpoint;

    /** Presigned URL 만료 시간(초). 큰 파일 업로드 시간 + 여유. */
    private long presignExpirySeconds = 3600;

    /** Multipart 업로드 part 크기(바이트). 64MB 기본. */
    private long partSizeBytes = 64L * 1024 * 1024;

    /** S3 region 더미 값 — MinIO 는 region 검증 안 하지만 SDK 가 요구. */
    private String region = "us-east-1";
}
