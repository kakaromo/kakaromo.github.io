// @source src/main/java/com/samsung/move/t32/service/T32DumpService.java
// @lines 1107-1169
// @note Step 4 결과 업로드 — UPLOADING→COMPLETED/FAILED, MinIO objectKey, 결과 보존
// @synced 2026-06-22T22:22:10.909Z

    private void uploadResultArtifact(SseEmitter emitter, String resultLinuxPath, String resultDirName,
                                      String setLocation, String testToolName, String testTrName,
                                      Long historyId, String testType, String source) {
        if (resultLinuxPath == null || resultLinuxPath.isBlank()) {
            log.warn("[T32Dump] resultLinuxPath 없음 — 업로드 생략");
            return;
        }
        T32TestType type = parseTestType(testType);
        T32ResultSource src = parseSource(source);
        java.nio.file.Path zipPath = java.nio.file.Path.of(resultLinuxPath + ".zip");

        // object key: {testType}/{source}/{historyId 또는 nohistory}/{resultDirName}.zip
        String hist = historyId != null ? String.valueOf(historyId) : "nohistory";
        String objectKey = type.name() + "/" + src.name() + "/" + hist + "/" + resultDirName + ".zip";

        T32ResultArtifact artifact = T32ResultArtifact.builder()
                .testType(type)
                .source(src)
                .historyId(historyId)
                .bucket(resultBucket)
                .objectKey(objectKey)
                .resultDirName(resultDirName)
                .setLocation(setLocation)
                .testToolName(testToolName)
                .testTrName(testTrName)
                .status(T32ResultArtifactStatus.UPLOADING)
                .build();
        artifact = artifactRepository.save(artifact);

        try {
            if (!java.nio.file.Files.exists(zipPath)) {
                throw new java.io.FileNotFoundException("zip 파일 없음: " + zipPath);
            }
            long size = java.nio.file.Files.size(zipPath);
            // 버킷이 없으면 NoSuchBucket 으로 업로드가 실패하므로 먼저 보장(멱등).
            storageService.ensureBucket(resultBucket);
            sendEvent(emitter, "step-output", Map.of("step", 4,
                    "line", "S3 업로드 중: " + resultBucket + "/" + objectKey + " (" + size + " bytes)"));
            try (InputStream in = java.nio.file.Files.newInputStream(zipPath)) {
                storageService.uploadObject(resultBucket, objectKey, in, size, "application/zip");
            }
            artifact.setSizeBytes(size);
            artifact.setStatus(T32ResultArtifactStatus.COMPLETED);
            artifact.setUploadedAt(java.time.LocalDateTime.now());
            artifactRepository.save(artifact);
            sendEvent(emitter, "step-output", Map.of("step", 4,
                    "line", "S3 업로드 완료: " + resultBucket + "/" + objectKey));
            log.info("[T32Dump] result 업로드 완료: bucket={} key={} size={} historyId={}",
                    resultBucket, objectKey, size, historyId);
            // 업로드 후에도 result zip/폴더를 보존한다. dump 완료 직후 다이얼로그가
            // loadResultFiles 로 result 폴더를 listdir 해 스크린샷·Canary report·폴더
            // 다운로드를 보여주는데, 여기서 zip 을 지우면 그 폴더가 사라져(또는 비어)
            // 'Failed to list files: No such file' 가 났다. S3 는 백업/장기보관용이고,
            // 즉시 확인용 로컬 결과는 별도 보존 정책(60일 자동삭제 등)에 맡긴다.
        } catch (Exception e) {
            artifact.setStatus(T32ResultArtifactStatus.FAILED);
            artifact.setErrorMessage(e.getMessage());
            artifactRepository.save(artifact);
            log.warn("[T32Dump] result 업로드 실패(무시): {} → {}", objectKey, e.getMessage());
            sendEvent(emitter, "step-output", Map.of("step", 4,
                    "line", "S3 업로드 실패(결과 폴더는 보존): " + e.getMessage()));
        }
    }
