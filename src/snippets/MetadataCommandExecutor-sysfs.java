// com.samsung.move.metadata.service.MetadataCommandExecutor#executeSysfsRead
// "sysfs" commandType 처리 — sysfs/proc 파일을 adb shell cat 으로 읽고 regex 캡처로 JSON 생성.
//
// commandTemplate 포맷 (줄바꿈 구분, 3개 파이프 토큰):
//   /sys/block/sda/stat | regex:(\d+)\s+\d+\s+(\d+) | keys:read_ios,read_sectors
//   /sys/block/sda/size
//   /proc/meminfo | regex:MemTotal:\s+(\d+) | keys:mem_total_kb
public String executeSysfsRead(String tentacleName, String serial, String commandTemplate) {
    Session session = createSession(tentacleName);
    try {
        String[] lines = commandTemplate.split("\\n");
        StringBuilder jsonBuilder = new StringBuilder("{");
        boolean first = true;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // 파이프 분리: 경로 | regex:... | keys:...
            String[] parts = trimmed.split("\\s*\\|\\s*");
            String sysfsPath = parts[0].trim();
            String regexPattern = null;
            String[] keys = null;
            for (int i = 1; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.startsWith("regex:")) regexPattern = part.substring(6);
                else if (part.startsWith("keys:")) keys = part.substring(5).split(",");
            }
            // 기본 key: 경로 마지막 세그먼트 ("/sys/block/sda/size" → "size")
            if (keys == null) {
                String defaultKey = sysfsPath.substring(sysfsPath.lastIndexOf('/') + 1);
                keys = new String[]{defaultKey};
            }

            String cmd = String.format("adb -s %s shell 'cat %s'", shellEscape(serial), sysfsPath);
            String rawOutput = execCommandWithTimeout(session, cmd, ADB_TIMEOUT_SECONDS).trim();

            if (regexPattern != null) {
                // 정규식 캡처 그룹별 key 매핑 → JSON 필드 생성
                Matcher matcher = Pattern.compile(regexPattern).matcher(rawOutput);
                if (matcher.find()) {
                    int groupCount = matcher.groupCount();
                    for (int g = 1; g <= groupCount && g <= keys.length; g++) {
                        if (!first) jsonBuilder.append(",");
                        jsonBuilder.append("\"").append(escapeJson(keys[g - 1].trim()))
                                .append("\":\"").append(escapeJson(matcher.group(g))).append("\"");
                        first = false;
                    }
                } else {
                    log.warn("regex no match for {} pattern {}", sysfsPath, regexPattern);
                }
            } else {
                // regex 없음 → 전체 출력을 key 에 할당
                if (!first) jsonBuilder.append(",");
                jsonBuilder.append("\"").append(escapeJson(keys[0].trim()))
                        .append("\":\"").append(escapeJson(rawOutput)).append("\"");
                first = false;
            }
        }
        jsonBuilder.append("}");
        return jsonBuilder.toString();
    } finally {
        if (session != null) session.disconnect();
    }
}
