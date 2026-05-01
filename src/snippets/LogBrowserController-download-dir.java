// @source src/main/java/com/samsung/move/logbrowser/controller/LogBrowserController.java
// @lines 82-144
// @note GET /download-dir — Local ZipOutputStream / SSH 원격 zip 후 SFTP
// @synced 2026-05-01T01:05:23.627Z

    @GetMapping("/download-dir")
    public ResponseEntity<byte[]> downloadDir(
            @RequestParam String tentacleName,
            @RequestParam String path) throws IOException {

        String dirName = Paths.get(path).getFileName().toString();
        String zipName = dirName + ".zip";

        if (logBrowserService instanceof LocalLogBrowserService) {
            // Local 모드
            Path dirPath = Path.of(path);
            if (!java.nio.file.Files.isDirectory(dirPath)) {
                return ResponseEntity.notFound().build();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                java.nio.file.Files.walkFileTree(dirPath, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String entryName = dirPath.relativize(file).toString();
                        zos.putNextEntry(new ZipEntry(entryName));
                        java.nio.file.Files.copy(file, zos);
                        zos.closeEntry();
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + URLEncoder.encode(zipName, StandardCharsets.UTF_8))
                    .contentType(MediaType.valueOf("application/zip"))
                    .body(baos.toByteArray());
        } else {
            // SSH 모드 — 원격에서 zip 후 SFTP로 가져오기
            String parentDir = Paths.get(path).getParent().toString();
            String remoteTmpZip = path + ".zip";
            // zip -r 명령어로 원격 압축
            try {
                // 원격에서 zip 생성 (cleanup은 finally에서)
                logBrowserService.readFileContent(tentacleName,
                        "$(cd " + parentDir + " && zip -r " + remoteTmpZip + " " + dirName + " >/dev/null 2>&1 && echo OK)");
            } catch (Exception ignored) {
                // readFileContent는 zip 결과와 무관 — 명령어 실행 트릭
            }
            try {
                InputStream is = logBrowserService.downloadFile(tentacleName, remoteTmpZip);
                byte[] content = is.readAllBytes();
                is.close();
                // 원격 tmp zip 정리
                try {
                    logBrowserService.readFileContent(tentacleName, "$(rm -f " + remoteTmpZip + " && echo OK)");
                } catch (Exception ignored) {}
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename*=UTF-8''" + URLEncoder.encode(zipName, StandardCharsets.UTF_8))
                        .contentType(MediaType.valueOf("application/zip"))
                        .body(content);
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(("ZIP 생성 실패: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
            }
        }
    }
