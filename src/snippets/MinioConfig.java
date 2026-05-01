// @source src/main/java/com/samsung/move/minio/config/MinioConfig.java
// @lines 1-18
// @note MinioClient.builder() — io.minio:minio 8.5.14 SDK 래퍼
// @synced 2026-05-01T01:05:23.632Z

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
