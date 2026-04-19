// @source src/main/java/com/samsung/move/minio/controller/MinioUploadController.java
// @lines 1-53
// @note POST /upload — MultipartFile + 2GB 압축 강제 검증 + prefix 경로 조립
// @synced 2026-04-19T09:49:20.696Z

package com.samsung.move.minio.controller;

import com.samsung.move.minio.service.MinioStorageService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/minio")
@RequiredArgsConstructor
public class MinioUploadController {

    private final MinioStorageService storageService;

    private static final long COMPRESS_ONLY_THRESHOLD = 2L * 1024 * 1024 * 1024; // 2GB
    private static final Set<String> COMPRESSED_EXTENSIONS = Set.of(
            ".zip", ".gz", ".tar", ".tgz", ".tar.gz", ".bz2", ".xz", ".7z", ".rar", ".zst"
    );

    @PostMapping("/buckets/{bucket}/upload")
    public ResponseEntity<?> upload(
            @PathVariable String bucket,
            @RequestParam(required = false, defaultValue = "") String prefix,
            @RequestParam("file") MultipartFile file) throws Exception {

        if (file.getSize() >= COMPRESS_ONLY_THRESHOLD) {
            String fileName = (file.getOriginalFilename() != null ? file.getOriginalFilename() : "").toLowerCase();
            boolean isCompressed = COMPRESSED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
            if (!isCompressed) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "2GB 이상의 파일은 압축 파일만 업로드할 수 있습니다. (.zip, .gz, .tar, .7z, .rar 등)"
                ));
            }
        }

        String objectName = prefix.isEmpty()
                ? file.getOriginalFilename()
                : prefix + file.getOriginalFilename();

        storageService.uploadObject(bucket, objectName, file.getInputStream(), file.getSize(), file.getContentType());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Uploaded: " + objectName
        ));
    }
}
