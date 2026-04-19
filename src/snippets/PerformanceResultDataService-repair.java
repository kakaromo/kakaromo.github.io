// @source src/main/java/com/samsung/move/testdb/service/PerformanceResultDataService.java
// @lines 163-218
// @note tryRepairJson — trailing comma + 닫히지 않은 bracket/문자열 복구
// @synced 2026-04-19T09:49:20.701Z

    /**
     * 불완전 JSON 복구 시도: trailing comma 제거 + 닫히지 않은 bracket 닫기
     */
    private String tryRepairJson(String json) {
        try {
            // trailing comma 제거 (,] 또는 ,} 패턴)
            String repaired = json.replaceAll(",\\s*([\\]\\}])", "$1");

            // 닫히지 않은 bracket/문자열 카운트
            int braces = 0, brackets = 0;
            boolean inString = false;
            boolean escape = false;
            for (int i = 0; i < repaired.length(); i++) {
                char c = repaired.charAt(i);
                if (escape) { escape = false; continue; }
                if (c == '\\' && inString) { escape = true; continue; }
                if (c == '"') { inString = !inString; continue; }
                if (inString) continue;
                switch (c) {
                    case '{': braces++; break;
                    case '}': braces--; break;
                    case '[': brackets++; break;
                    case ']': brackets--; break;
                }
            }

            // 음수면 복구 불가로 판단 (닫기 bracket이 더 많은 경우)
            if (braces < 0 || brackets < 0) return null;

            StringBuilder sb = new StringBuilder(repaired);

            // 잘린 문자열 닫기 (HEAD가 쓰는 도중 "some valu 에서 끊긴 경우)
            if (inString) {
                sb.append('"');
            }

            // trailing comma 제거 (bracket 닫기 전에)
            String trimmed = sb.toString().replaceAll(",\\s*$", "");
            // key만 있고 value가 없는 경우 제거 ("key": 에서 끊긴 경우)
            trimmed = trimmed.replaceAll(",\\s*\"[^\"]*\"\\s*:\\s*$", "");
            // 불완전한 key-value 쌍 제거 ("key":"val" 다음에 쉼표 + 불완전)
            trimmed = trimmed.replaceAll(":\\s*$", ":null");

            sb = new StringBuilder(trimmed);
            for (int i = 0; i < brackets; i++) sb.append(']');
            for (int i = 0; i < braces; i++) sb.append('}');

            // 복구된 JSON 유효성 재검증
            objectMapper.readTree(sb.toString());
            return sb.toString();
        } catch (Exception e) {
            log.debug("JSON repair failed: {}", e.getMessage());
            return null;
        }
    }
}
