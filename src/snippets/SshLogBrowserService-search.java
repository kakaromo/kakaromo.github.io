// @source src/main/java/com/samsung/move/logbrowser/service/SshLogBrowserService.java
// @lines 191-211
// @note SSH searchInFile — rg -n --no-heading --encoding auto + shellEscape + iconv pipe
// @synced 2026-06-22T22:22:10.916Z

        } catch (Exception e) {
            log.error("SSH readLastLines failed [vm={}, path={}]: {}", tentacleName, remotePath, e.getMessage());
            throw new RuntimeException("Failed to read last lines: " + e.getMessage(), e);
        }
    }

    @Override
    public List<SearchResult> searchInFile(String tentacleName, String path, String pattern, boolean regex) {
        String remotePath = resolvePath(path);
        String safePath = shellEscape(remotePath);
        String safePattern = shellEscape(pattern);
        String iconvPipe = buildIconvPipe(tentacleName, path);

        try {
            Session session = getOrCreateCachedSession(tentacleName);

            // -e 로 패턴을 지정해야 "-110" 같은 dash-prefix 패턴이 옵션으로 오인되지 않음.
            // 리터럴 모드(기본)에서는 -F 추가하여 정규식 메타 문자(. * + ? 등)를 그대로 매칭.
            String literalFlag = regex ? "" : "-F ";
            String output = execCommand(session,
                    "rg -n --no-heading --encoding auto " + literalFlag + "-e " + safePattern + " " + safePath
