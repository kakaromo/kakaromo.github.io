// @source src/main/java/com/samsung/move/minio/service/MinioStorageService.java
// @lines 1-150
// @note 버킷/오브젝트 CRUD — list/listRecursive/upload/download/stat/createFolder/delete
// @synced 2026-04-19T08:48:08.182Z

package com.samsung.move.minio.service;

import io.minio.*;
import io.minio.messages.Bucket;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {

    private final MinioClient minioClient;

    public List<String> listBuckets() throws Exception {
        return minioClient.listBuckets().stream()
                .map(Bucket::name)
                .toList();
    }

    public void createBucket(String bucketName) throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build());
        if (exists) {
            throw new IllegalArgumentException("Bucket already exists: " + bucketName);
        }
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
    }

    public void deleteBucket(String bucketName) throws Exception {
        minioClient.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
    }

    public List<Map<String, Object>> listObjects(String bucket, String prefix) throws Exception {
        List<Map<String, Object>> result = new ArrayList<>();
        String normalizedPrefix = (prefix == null || prefix.isEmpty()) ? "" : prefix;

        Iterable<Result<Item>> objects = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucket)
                        .prefix(normalizedPrefix)
                        .delimiter("/")
                        .build());

        for (Result<Item> itemResult : objects) {
            Item item = itemResult.get();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", item.objectName());
            entry.put("isDir", item.isDir());
            entry.put("size", item.size());
            String lastModified = null;
            try {
                ZonedDateTime lm = item.lastModified();
                if (lm != null) {
                    lastModified = lm.toString();
                }
            } catch (Exception e) {
                log.debug("Failed to parse lastModified for {}: {}", item.objectName(), e.getMessage());
            }
            entry.put("lastModified", lastModified);
            result.add(entry);
        }
        return result;
    }

    /**
     * 폴더 내 모든 파일을 재귀적으로 나열 (하위 폴더 포함)
     */
    public List<Map<String, Object>> listObjectsRecursive(String bucket, String prefix) throws Exception {
        List<Map<String, Object>> result = new ArrayList<>();
        String normalizedPrefix = (prefix == null || prefix.isEmpty()) ? "" : prefix;

        Iterable<Result<Item>> objects = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucket)
                        .prefix(normalizedPrefix)
                        .recursive(true)
                        .build());

        for (Result<Item> itemResult : objects) {
            Item item = itemResult.get();
            // 폴더 마커(빈 object) 제외, 실제 파일만
            if (item.isDir() || item.objectName().endsWith("/")) continue;

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", item.objectName());
            entry.put("size", item.size());
            String lastModified = null;
            try {
                ZonedDateTime lm = item.lastModified();
                if (lm != null) lastModified = lm.toString();
            } catch (Exception e) {
                log.debug("Failed to parse lastModified for {}: {}", item.objectName(), e.getMessage());
            }
            entry.put("lastModified", lastModified);
            result.add(entry);
        }
        return result;
    }

    public void deleteObject(String bucket, String objectName) throws Exception {
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .build());
    }

    public void createFolder(String bucket, String folderPath) throws Exception {
        String path = folderPath.endsWith("/") ? folderPath : folderPath + "/";
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(path)
                        .stream(InputStream.nullInputStream(), 0, -1)
                        .build());
    }

    public InputStream downloadObject(String bucket, String objectName) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .build());
    }

    public StatObjectResponse statObject(String bucket, String objectName) throws Exception {
        return minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .build());
    }

    public void uploadObject(String bucket, String objectName, InputStream stream, long size, String contentType) throws Exception {
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .stream(stream, size, -1)
                        .contentType(contentType != null ? contentType : "application/octet-stream")
                        .build());
    }
}
