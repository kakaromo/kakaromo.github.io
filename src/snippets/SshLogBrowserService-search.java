// @source src/main/java/com/samsung/move/logbrowser/service/SshLogBrowserService.java
// @lines 191-211
// @note SSH searchInFile — rg -n --no-heading --encoding auto + shellEscape + iconv pipe
// @synced 2026-04-19T09:04:03.503Z

    @Override
    public List<SearchResult> searchInFile(String tentacleName, String path, String pattern) {
        String remotePath = resolvePath(path);
        String safePath = shellEscape(remotePath);
        String safePattern = shellEscape(pattern);
        String iconvPipe = buildIconvPipe(tentacleName, path);

        try {
            Session session = getOrCreateCachedSession(tentacleName);

            String output = execCommand(session,
                    "rg -n --no-heading --encoding auto " + safePattern + " " + safePath
                            + iconvPipe);

            return parseSearchOutput(output);
        } catch (Exception e) {
            log.error("SSH search failed [vm={}, path={}, pattern={}]: {}",
                    tentacleName, remotePath, pattern, e.getMessage());
            throw new RuntimeException("Failed to search file: " + e.getMessage(), e);
        }
    }
