// com.samsung.move.metadata.service.MetadataCommandExecutor#executeBinary
// "binary" commandType 처리 — binmapper 모듈을 "재사용" 해서 C struct 텍스트로 바이너리를 매핑.
//
// 흐름: adb shell tool 실행 → 디바이스 /dev/debug_xxx.bin → adb pull → tentacle /tmp → SFTP read →
//       binMapperService.parseFromBytes → MappedResultFlattener → flat JSON
public String executeBinary(String tentacleName, String serial, String commandTemplate,
                            String binaryOutputPath, Long predefinedStructId, String endiannessStr) {
    if (binaryOutputPath == null || binaryOutputPath.isBlank())
        throw new IllegalArgumentException("binaryOutputPath required for binary command_type");
    if (predefinedStructId == null)
        throw new IllegalArgumentException("predefinedStructId required for binary command_type");

    Session session = null;
    ChannelSftp sftp = null;
    // tentacle 임시 경로 — 충돌 방지용 nanoTime
    String tentacleTmpPath = "/tmp/metadata-binary-" + System.nanoTime() + ".bin";

    try {
        session = createSession(tentacleName);

        // 1. adb shell 로 tool 실행 → 디바이스가 /dev/debug_xxx.bin 생성
        String shellCmd = String.format("adb -s %s shell '%s'",
                shellEscape(serial), commandTemplate);
        execCommandWithTimeout(session, shellCmd, ADB_TIMEOUT_SECONDS);

        // 2. adb pull → tentacle 임시 파일로 복사
        String pullCmd = String.format("adb -s %s pull %s %s",
                shellEscape(serial), shellEscape(binaryOutputPath), shellEscape(tentacleTmpPath));
        execCommandWithTimeout(session, pullCmd, ADB_PUSH_TIMEOUT_SECONDS);

        // 3. SFTP 로 tentacle 에서 바이트 읽기
        sftp = (ChannelSftp) session.openChannel("sftp");
        sftp.connect(30000);
        byte[] data;
        try (InputStream is = sftp.get(tentacleTmpPath)) {
            data = is.readAllBytes();
        }

        // 4. binmapper 로 struct 매핑 — predefined_structs 테이블의 kind='metadata' 레코드 참조
        Endianness endianness = parseEndianness(endiannessStr);   // LITTLE/BIG/AUTO (null → LITTLE)
        MappedResult mapped = binMapperService.parseFromBytes(
                data, predefinedStructId, /* overrideStructText */ null,
                endianness, /* preview */ false);

        // 5. flatten — 중첩 struct/배열을 dot-notation 단일 Map 으로 (차트·테이블 친화적)
        Map<String, Object> flat = MappedResultFlattener.flatten(mapped);

        // 6. tentacle 임시파일 cleanup (best-effort)
        try { sftp.rm(tentacleTmpPath); } catch (Exception ignored) {}

        return MAPPER.writeValueAsString(flat);
    } catch (Exception e) {
        throw new RuntimeException("Binary command failed: " + e.getMessage(), e);
    } finally {
        if (sftp != null) sftp.disconnect();
        if (session != null) session.disconnect();
    }
}

private static Endianness parseEndianness(String s) {
    if (s == null || s.isBlank()) return Endianness.LITTLE;
    try { return Endianness.valueOf(s.trim().toUpperCase()); }
    catch (IllegalArgumentException e) { return Endianness.LITTLE; }
}
