// @source src/main/java/com/samsung/move/minio/config/MinioConfig.java
// @lines 1-66
// @note 3 Bean — MinioClient (CRUD) + S3Client (multipart RPC) + S3Presigner (브라우저 직PUT URL)
// @synced 2026-05-05

package com.samsung.move.minio.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(MinioProperties props) {
        return MinioClient.builder()
                .endpoint(props.getEndpoint(), props.getPort(), props.isUseSsl())
                .credentials(props.getAccessKey(), props.getSecretKey())
                .build();
    }

    /** MinIO 와 직접 통신 (createMultipartUpload, completeMultipartUpload, abort). */
    @Bean
    public S3Client s3Client(MinioProperties props) {
        return S3Client.builder()
                .endpointOverride(URI.create(internalEndpoint(props)))
                .region(Region.of(props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    /**
     * Presigned URL 발급용. publicEndpoint 가 설정되어 있으면 그쪽으로 URL 생성
     * → 브라우저는 nginx 경유로 MinIO 에 접근 (옵션 A).
     * 설정 없으면 internal endpoint 그대로 사용 (브라우저 직접 접근 가능한 환경 전용).
     */
    @Bean
    public S3Presigner s3Presigner(MinioProperties props) {
        String endpoint = (props.getPublicEndpoint() != null && !props.getPublicEndpoint().isBlank())
                ? props.getPublicEndpoint()
                : internalEndpoint(props);
        return S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    private static String internalEndpoint(MinioProperties props) {
        String scheme = props.isUseSsl() ? "https" : "http";
        String host = props.getEndpoint().replaceFirst("^https?://", "");
        return scheme + "://" + host + ":" + props.getPort();
    }
}
