// @source src/main/java/com/samsung/move/logbrowser/service/LocalLogBrowserService.java
// @lines 33-70
// @note Local listFiles — Files.list + 동일 정렬 + ".." 엔트리
// @synced 2026-04-19T09:49:20.690Z

    @Override
    public List<FileEntry> listFiles(String tentacleName, String path) {
        String vmName = findVm(tentacleName).getName();
        Path localPath = resolveLocalPath(vmName, path);

        if (!Files.isDirectory(localPath)) {
            throw new RuntimeException("Directory not found: " + localPath);
        }

        try (Stream<Path> stream = Files.list(localPath)) {
            List<FileEntry> result = stream.map(p -> {
                try {
                    String name = p.getFileName().toString();
                    boolean isDir = Files.isDirectory(p);
                    long size = isDir ? 0 : Files.size(p);
                    long lastModified = Files.getLastModifiedTime(p).toMillis();
                    return new FileEntry(name, isDir, size, lastModified);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read file attributes: " + p, e);
                }
            }).sorted(Comparator
                    .comparing((FileEntry f) -> !f.directory())
                    .thenComparing(FileEntry::name))
                    .toList();

            // Add ".." entry for navigation (matching SSH behavior)
            Path baseDir = Paths.get(mountPath, vmName).normalize();
            if (!localPath.equals(baseDir)) {
                var entries = new ArrayList<>(result);
                entries.add(0, new FileEntry("..", true, 0, 0));
                return entries;
            }
            return result;
        } catch (IOException e) {
            log.error("Local list failed [vm={}, path={}]: {}", tentacleName, localPath, e.getMessage());
            throw new RuntimeException("Failed to list files: " + e.getMessage(), e);
        }
    }
