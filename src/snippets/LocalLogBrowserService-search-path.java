// @source src/main/java/com/samsung/move/logbrowser/service/LocalLogBrowserService.java
// @lines 206-312
// @note Local searchInFile (ProcessBuilder rg) + resolveLocalPath (path traversal 방어)
// @synced 2026-04-19T09:04:03.504Z

    public List<SearchResult> searchInFile(String tentacleName, String path, String pattern) {
        String vmName = findVm(tentacleName).getName();
        Path localPath = resolveLocalPath(vmName, path);

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "rg", "-n", "--no-heading", "--encoding", "auto",
                    pattern, localPath.toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();

            if (exitCode > 1) {
                throw new RuntimeException("rg command failed with exit code " + exitCode);
            }

            if (output.trim().isEmpty()) {
                return Collections.emptyList();
            }

            List<SearchResult> results = new ArrayList<>();
            for (String line : output.split("\n")) {
                if (line.isEmpty()) continue;
                int colonIdx = line.indexOf(':');
                if (colonIdx > 0) {
                    try {
                        int lineNumber = Integer.parseInt(line.substring(0, colonIdx));
                        String text = line.substring(colonIdx + 1);
                        results.add(new SearchResult(lineNumber, text));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return results;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Local search failed [vm={}, path={}, pattern={}]: {}",
                    tentacleName, localPath, pattern, e.getMessage());
            throw new RuntimeException("Failed to search file: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isBinaryFile(String tentacleName, String path) {
        String vmName = findVm(tentacleName).getName();
        Path localPath = resolveLocalPath(vmName, path);
        return "binary".equals(detectEncoding(localPath));
    }

    /**
     * 파일의 MIME 인코딩을 감지합니다.
     * @return 인코딩 이름 (예: "utf-8", "iso-8859-1", "euc-kr") 또는 "binary"
     */
    private String detectEncoding(Path localPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder("file", "--mime-encoding", localPath.toString());
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor();
            String lower = output.toLowerCase().trim();
            int colonIdx = lower.lastIndexOf(':');
            if (colonIdx >= 0) {
                return lower.substring(colonIdx + 1).trim();
            }
            return lower.contains("binary") ? "binary" : "utf-8";
        } catch (Exception e) {
            log.error("Local encoding detection failed [path={}]: {}", localPath, e.getMessage());
            return "binary";
        }
    }

    /**
     * 인코딩 이름으로 Charset을 반환합니다. 알 수 없는 인코딩이면 UTF-8 fallback.
     */
    private java.nio.charset.Charset resolveCharset(String encoding) {
        if ("binary".equals(encoding)) return StandardCharsets.UTF_8;
        try {
            return java.nio.charset.Charset.forName(encoding);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    private int countLines(Path path, java.nio.charset.Charset charset) throws IOException {
        try (Stream<String> stream = Files.lines(path, charset)) {
            return (int) stream.count();
        }
    }

    private Path resolveLocalPath(String vmName, String path) {
        Path baseDir;
        if ("HEAD".equalsIgnoreCase(vmName)) {
            // HEAD paths are resolved via headMountPath directly
            baseDir = Paths.get(headMountPath).normalize();
        } else {
            baseDir = Paths.get(mountPath, vmName).normalize();
        }
        Path resolved = baseDir.resolve(path).normalize();

        if (!resolved.startsWith(baseDir)) {
            throw new SecurityException("Path traversal detected: " + path);
        }
        return resolved;
    }
