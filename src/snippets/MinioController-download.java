// @source src/main/java/com/samsung/move/minio/controller/MinioController.java
// @lines 152-206
// @note /download-folder (ZipOutputStream recursive) + /download (InputStreamResource + UTF-8 filename)
// @synced 2026-06-22T22:22:10.923Z

    }

    @GetMapping("/buckets/{bucket}/download-folder")
    public void downloadFolder(
            @PathVariable String bucket,
            @RequestParam String prefix,
            HttpServletResponse response) throws Exception {
        // 폴더명 추출 (prefix "a/b/c/" → "c")
        String trimmed = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        String folderName = trimmed.contains("/")
                ? trimmed.substring(trimmed.lastIndexOf('/') + 1) : trimmed;
        String zipName = URLEncoder.encode(folderName + ".zip", StandardCharsets.UTF_8);

        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + zipName);

        List<Map<String, Object>> files = storageService.listObjectsRecursive(bucket, prefix);

        try (OutputStream os = response.getOutputStream();
             ZipOutputStream zos = new ZipOutputStream(os)) {
            for (Map<String, Object> file : files) {
                String objectName = (String) file.get("name");
                // prefix 이전 부분을 제거하되, 폴더명은 포함
                // e.g. prefix="a/b/", objectName="a/b/c/d.txt" → entry="b/c/d.txt"
                String parentPrefix = trimmed.contains("/")
                        ? trimmed.substring(0, trimmed.lastIndexOf('/') + 1) : "";
                String entryName = objectName.substring(parentPrefix.length());

                zos.putNextEntry(new ZipEntry(entryName));
                try (InputStream is = storageService.downloadObject(bucket, objectName)) {
                    is.transferTo(zos);
                }
                zos.closeEntry();
            }
        }
    }

    /**
     * Presigned GET URL 발급. 브라우저가 nginx → MinIO 직행으로 다운로드.
     * Content-Disposition 을 presigned URL 의 응답에 강제하므로 파일명 보존.
     * 폴더 다운로드(downloadFolder)는 ZIP 묶기가 필요해 spring 경유 유지.
     * /minio-upload/ 가 막힌 환경에서는 프론트가 fallback 으로 /download 를 사용.
     */
    @GetMapping("/buckets/{bucket}/download-url")
    public ResponseEntity<?> downloadUrl(
            @PathVariable String bucket,
            @RequestParam String objectName) {
        String fileName = objectName.contains("/")
                ? objectName.substring(objectName.lastIndexOf('/') + 1)
                : objectName;
        String url = presignService.presignSingleGet(bucket, objectName, fileName);
        return ResponseEntity.ok(java.util.Map.of("url", url, "fileName", fileName));
    }

