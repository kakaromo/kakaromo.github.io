// com.samsung.move.metadata.service.MetadataCommandExecutor#executeKeyValue
// "keyvalue" commandType 처리 — f2fs status 같은 들여쓰기 기반 human-readable 출력 파싱.
//
// 예시 입력:
//   =====[ partition info(sda21). #0, RDWR, CP: Good ]=====
//   GC calls: 234 (BG: 189)
//   Hot data:
//       segment: 5
//       blocks: 1024
//
// 변환 결과:
//   { "sda21.gc_calls": 234, "sda21.gc_calls_BG": 189,
//     "sda21.hot_data.segment": 5, "sda21.hot_data.blocks": 1024 }
public String executeKeyValue(String tentacleName, String serial, String commandTemplate) {
    Session session = createSession(tentacleName);
    try {
        String[] paths = commandTemplate.split("\\n");
        StringBuilder jsonBuilder = new StringBuilder("{");
        boolean first = true;

        for (String path : paths) {
            String trimmedPath = path.trim();
            if (trimmedPath.isEmpty()) continue;

            String cmd = String.format("adb -s %s shell 'cat %s'", shellEscape(serial), trimmedPath);
            String output = execCommandWithTimeout(session, cmd, ADB_TIMEOUT_SECONDS).trim();
            String[] outputLines = output.split("\\n");

            // 들여쓰기 prefix 스택 — 하위 depth 로 내려가면 push, 올라오면 pop
            ArrayDeque<String> prefixStack = new ArrayDeque<>();
            ArrayDeque<Integer> indentStack = new ArrayDeque<>();
            indentStack.push(-1);

            // 섹션 prefix (partition info(sda21) → 최상위 prefix "sda21")
            String deviceSection = null;
            Pattern sectionPattern = Pattern.compile(
                    "[=\\-_]*\\[\\s*partition\\s+info\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);

            for (String ol : outputLines) {
                if (ol.trim().isEmpty()) continue;
                // 들여쓰기 계산 (space 1, tab 4)
                int indent = 0;
                for (char c : ol.toCharArray()) {
                    if (c == ' ') indent++;
                    else if (c == '\t') indent += 4;
                    else break;
                }
                String content = ol.trim();
                if (content.startsWith("- ")) content = content.substring(2).trim();

                // 섹션 헤더 감지 시 device 교체 + 스택 리셋
                Matcher secM = sectionPattern.matcher(content);
                if (secM.find()) {
                    deviceSection = secM.group(1).trim().replaceAll("[^a-zA-Z0-9_]", "_");
                    prefixStack.clear(); indentStack.clear(); indentStack.push(-1);
                    continue;
                }
                if (content.startsWith("#") || content.startsWith("=")) continue;
                if (content.startsWith("[") && content.endsWith("]")) continue;

                int colonIdx = content.indexOf(':');
                if (colonIdx <= 0) continue;
                String key = content.substring(0, colonIdx).trim()
                        .replaceAll("[^a-zA-Z0-9_.]", "_").replaceAll("_+", "_");
                String value = content.substring(colonIdx + 1).trim();

                // 들여쓰기가 작아지면 스택 pop
                while (!indentStack.isEmpty() && indentStack.peek() >= indent) {
                    indentStack.pop();
                    if (!prefixStack.isEmpty()) prefixStack.pop();
                }

                if (value.isEmpty()) {
                    // value 없음 → 하위 level 의 prefix 가 됨
                    prefixStack.push(key);
                    indentStack.push(indent);
                } else {
                    // deviceSection.prefix_stack.key 조합으로 fullKey 생성
                    List<String> parts = new ArrayList<>();
                    if (deviceSection != null) parts.add(deviceSection);
                    List<String> stackList = new ArrayList<>(prefixStack);
                    Collections.reverse(stackList);
                    parts.addAll(stackList);
                    parts.add(key);
                    String fullKey = String.join(".", parts);

                    // 숫자 추출 (단위 kB/ms 무시) + 괄호 안 값도 별도 필드로 (gc_calls_BG)
                    String numStr = extractNumber(value);
                    if (!first) jsonBuilder.append(",");
                    if (numStr != null)
                        jsonBuilder.append("\"").append(escapeJson(fullKey)).append("\":").append(numStr);
                    else
                        jsonBuilder.append("\"").append(escapeJson(fullKey))
                                .append("\":\"").append(escapeJson(value)).append("\"");
                    first = false;

                    Matcher paren = Pattern.compile("\\(([^)]+)\\)").matcher(value);
                    while (paren.find()) {
                        String inner = paren.group(1).trim();
                        int pColon = inner.indexOf(':');
                        if (pColon <= 0) continue;
                        String pKey = inner.substring(0, pColon).trim().replaceAll("[^a-zA-Z0-9_]", "_");
                        String pVal = inner.substring(pColon + 1).trim();
                        String pNum = extractNumber(pVal);
                        String pFullKey = fullKey + "_" + pKey;
                        jsonBuilder.append(",");
                        if (pNum != null)
                            jsonBuilder.append("\"").append(escapeJson(pFullKey)).append("\":").append(pNum);
                        else
                            jsonBuilder.append("\"").append(escapeJson(pFullKey))
                                    .append("\":\"").append(escapeJson(pVal)).append("\"");
                    }
                }
            }
        }
        jsonBuilder.append("}");
        return jsonBuilder.toString();
    } finally {
        if (session != null) session.disconnect();
    }
}
