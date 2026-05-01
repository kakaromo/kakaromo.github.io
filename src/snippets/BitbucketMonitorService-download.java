// @source src/main/java/com/samsung/move/bitbucket/service/BitbucketMonitorService.java
// @lines 117-224
// @note downloadBranch 6단계 — 디렉토리 생성 → ZIP 스트리밍(1MB SSE 진행) → extractZip(Zip Slip 방지) → ZIP 삭제 → DB 업데이트
// @synced 2026-05-01T01:05:23.631Z

    public BitbucketBranch downloadBranch(BitbucketRepo repo, String branchName, String branchId, String commitId,
                                          org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter) {
        String safeName = branchName.replace("/", "_");
        // controller 결정: repo에 설정되어 있으면 사용, 없으면 브랜치명에서 자동 감지
        String controller = repo.getController();
        log.debug("[Bitbucket] 다운로드 시작 — repo={}, branch={}, repoController={}", repo.getName(), branchName, controller);
        if (controller == null || controller.isBlank()) {
            controller = detectControllerFromBranch(branchName);
            log.debug("[Bitbucket] Controller 자동 감지 결과: {}", controller);
        }
        String targetDir = repo.getTargetPath();
        if (controller != null && !controller.isBlank()) {
            targetDir = targetDir + "/" + controller;
        }
        String zipPath = targetDir + "/" + safeName + ".zip";
        log.debug("[Bitbucket] targetDir={}, zipPath={}", targetDir, zipPath);

        // 커밋 날짜 조회
        LocalDateTime commitDate = null;
        try {
            Long ts = apiClient.getCommitTimestamp(
                    repo.getServerUrl(), repo.getProjectKey(), repo.getRepoSlug(), branchId, repo.getPat());
            if (ts != null) {
                commitDate = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ts), java.time.ZoneId.systemDefault());
            }
        } catch (Exception e) {
            log.debug("[Bitbucket] commitDate 조회 실패: {}", e.getMessage());
        }

        BitbucketBranch record = BitbucketBranch.builder()
                .repoId(repo.getId())
                .branchName(branchName)
                .latestCommitId(commitId)
                .commitDate(commitDate)
                .status("DOWNLOADING")
                .filePath(zipPath)
                .build();
        record = branchRepository.save(record);

        try {
            // 대상 디렉토리 생성
            Path targetPath = Path.of(targetDir);
            log.debug("[Bitbucket] Step 1: 디렉토리 생성 — {}", targetPath);
            Files.createDirectories(targetPath);

            // ZIP 다운로드
            Path zipFile = Path.of(zipPath);
            log.debug("[Bitbucket] Step 2: ZIP 다운로드 시작 — server={}, project={}, repo={}, branchId={}",
                    repo.getServerUrl(), repo.getProjectKey(), repo.getRepoSlug(), branchId);
            try (InputStream is = apiClient.downloadArchive(
                    repo.getServerUrl(), repo.getProjectKey(), repo.getRepoSlug(), branchId, repo.getPat());
                 java.io.OutputStream out = Files.newOutputStream(zipFile)) {
                byte[] buf = new byte[8192];
                long totalBytes = 0;
                long lastReport = 0;
                int len;
                while ((len = is.read(buf)) != -1) {
                    out.write(buf, 0, len);
                    totalBytes += len;
                    // 1MB마다 진행 상황 보고
                    if (totalBytes - lastReport >= 1024 * 1024) {
                        lastReport = totalBytes;
                        log.debug("[Bitbucket] 다운로드 진행: {}MB", totalBytes / (1024 * 1024));
                        if (emitter != null) {
                            sendSseEvent(emitter, "download-progress", java.util.Map.of(
                                    "bytes", totalBytes, "mb", totalBytes / (1024 * 1024)));
                        }
                    }
                }
            }

            long fileSize = Files.size(zipFile);
            log.debug("[Bitbucket] Step 3: ZIP 저장 완료 — path={}, size={}bytes", zipFile, fileSize);
            if (emitter != null) {
                sendSseEvent(emitter, "download-done", java.util.Map.of("bytes", fileSize));
            }

            // ZIP 압축 해제
            Path extractDir = Path.of(targetDir, safeName);
            log.debug("[Bitbucket] Step 4: 압축 해제 시작 — {}", extractDir);
            if (emitter != null) {
                sendSseEvent(emitter, "extract-start", java.util.Map.of("path", extractDir.toString()));
            }
            extractZip(zipFile, extractDir);
            log.debug("[Bitbucket] Step 5: 압축 해제 완료");
            if (emitter != null) {
                sendSseEvent(emitter, "extract-done", java.util.Map.of("path", extractDir.toString()));
            }

            // ZIP 파일 삭제 (압축 해제 완료 후)
            Files.deleteIfExists(zipFile);
            log.debug("[Bitbucket] Step 6: ZIP 파일 삭제 완료");

            record.setStatus("DOWNLOADED");
            record.setFilePath(extractDir.toString()); // ZIP이 아닌 폴더 경로로 변경
            record.setFileSizeBytes(fileSize);
            record.setDownloadedAt(LocalDateTime.now());
            log.info("[Bitbucket] {} 브랜치 '{}' 다운로드 완료 ({}bytes)", repo.getName(), branchName, fileSize);

        } catch (Exception e) {
            record.setStatus("FAILED");
            record.setErrorMessage(e.getMessage());
            record.setDownloadedAt(LocalDateTime.now());
            log.error("[Bitbucket] {} 브랜치 '{}' 다운로드 실패: {}", repo.getName(), branchName, e.getMessage());
        }

        return branchRepository.save(record);
    }
