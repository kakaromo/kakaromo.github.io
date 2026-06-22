// @source src/main/java/com/samsung/move/minio/controller/MinioUploadController.java
// @lines 1-53
// @note POST /upload — MultipartFile + 2GB 압축 강제 검증 + prefix 경로 조립
// @synced 2026-06-22T22:22:10.922Z

package com.samsung.move.minio.controller;

import com.samsung.move.minio.config.MinioProperties;
import com.samsung.move.minio.dto.CompletedPart;
import com.samsung.move.minio.dto.PresignedPart;
import com.samsung.move.minio.service.MinioStorageService;
import com.samsung.move.minio.service.S3PresignService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Storage 페이지의 업로드 컨트롤러.
 *
 * 큰 파일은 presigned multipart 로 브라우저가 nginx → MinIO 에 직접 PUT 한다 (Spring JVM 통과 X).
 * /minio-upload/ 가 막혀 있는 환경(방화벽/프록시 차단)에서는 프론트가 자동으로
 * 기존 /upload (Spring 경유 multipart/form) 로 fallback 한다.
 *
 * Endpoints:
 *  - POST /buckets/{b}/upload                  : 기존 Spring 경유 (fallback 용)
 *  - POST /buckets/{b}/upload/init             : presigned multipart 시작 + part URL 발급
 *  - POST /buckets/{b}/upload/complete         : multipart 완료
 *  - POST /buckets/{b}/upload/abort            : multipart 취소
 *
 * trace 의 /api/trace/upload/* 패턴과 동일.
 */
@RestController
@RequestMapping("/api/minio")
@RequiredArgsConstructor
public class MinioUploadController {

    private final MinioStorageService storageService;
    private final S3PresignService presign;
    private final MinioProperties minioProps;

    private static final long COMPRESS_ONLY_THRESHOLD = 2L * 1024 * 1024 * 1024; // 2GB
    private static final Set<String> COMPRESSED_EXTENSIONS = Set.of(
            ".zip", ".gz", ".tar", ".tgz", ".tar.gz", ".bz2", ".xz", ".7z", ".rar", ".zst"
    );

    @PostMapping("/buckets/{bucket}/upload")
    public ResponseEntity<?> upload(
            @PathVariable String bucket,
            @RequestParam(required = false, defaultValue = "") String prefix,
            @RequestParam("file") MultipartFile file) throws Exception {

