// com.samsung.move.metadata.service.MetadataCommandExecutor#resolveUserdataBlock
// {userdata} placeholder 를 실제 f2fs 파티션 block 이름("sda10", "sdc77" 등) 으로 치환하기 위한 조회.
// startMonitoring 시점에 1회만 호출 → SlotMonitorContext.placeholders 에 저장.
public String resolveUserdataBlock(String tentacleName, String serial) {
    Session session = null;
    try {
        session = createSession(tentacleName);

        // 1차: readlink -f 로 symlink 추적
        //   /dev/block/by-name/userdata → /dev/block/sda10
        String readlinkCmd = String.format(
                "adb -s %s shell 'readlink -f /dev/block/by-name/userdata'",
                shellEscape(serial));
        String target = execCommandWithTimeout(session, readlinkCmd, ADB_TIMEOUT_SECONDS).trim();

        // 2차 fallback: ls -al 출력에서 "-> " 이후 경로 추출
        if (target.isEmpty() || !target.startsWith("/")) {
            String lsCmd = String.format(
                    "adb -s %s shell 'ls -al /dev/block/by-name/userdata'",
                    shellEscape(serial));
            String lsOut = execCommandWithTimeout(session, lsCmd, ADB_TIMEOUT_SECONDS).trim();
            int arrow = lsOut.lastIndexOf("-> ");
            if (arrow >= 0) target = lsOut.substring(arrow + 3).trim();
        }

        if (target.isEmpty()) {
            log.warn("userdata block not found on [{}]", tentacleName);
            return null;
        }
        // basename: /dev/block/sda10 → sda10
        String basename = target.substring(target.lastIndexOf('/') + 1);
        log.info("Resolved userdata [{}]: {} (target={})", tentacleName, basename, target);
        return basename;
    } catch (Exception e) {
        log.warn("Failed to resolve userdata on [{}]: {}", tentacleName, e.getMessage());
        return null;
    } finally {
        if (session != null) session.disconnect();
    }
}

// ---- 치환 — MetadataMonitorService.resolvePlaceholders ----
// "/sys/fs/f2fs/{userdata}/iostat_info" → "/sys/fs/f2fs/sda10/iostat_info"
private String resolvePlaceholders(String template, SlotMonitorContext ctx) {
    if (template == null || template.isEmpty() || !template.contains("{")) return template;
    String out = template;
    for (Map.Entry<String, String> e : ctx.getPlaceholders().entrySet()) {
        out = out.replace("{" + e.getKey() + "}", e.getValue());
    }
    if (out.contains("{userdata}")) {
        log.warn("Unresolved {{userdata}} placeholder for slot [{}] — mount 조회 실패", ctx.getSlotKey());
    }
    return out;
}
