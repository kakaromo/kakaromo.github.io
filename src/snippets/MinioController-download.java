// @source src/main/java/com/samsung/move/minio/controller/MinioController.java
// @lines 152-206
// @note /download-folder (ZipOutputStream recursive) + /download (InputStreamResource + UTF-8 filename)
// @synced 2026-04-19T09:49:20.696Z

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

    @GetMapping("/buckets/{bucket}/download")
    public ResponseEntity<?> downloadObject(
            @PathVariable String bucket,
            @RequestParam String objectName) throws Exception {
        long size = storageService.statObject(bucket, objectName).size();
        InputStream is = storageService.downloadObject(bucket, objectName);
        String fileName = objectName.contains("/")
                ? objectName.substring(objectName.lastIndexOf('/') + 1)
                : objectName;
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedName)
                .contentLength(size)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(is));
    }
}
