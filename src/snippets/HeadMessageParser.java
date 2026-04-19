// @source src/main/java/com/samsung/move/head/service/HeadMessageParser.java
// @lines 15-170
// @note 정규식 패턴 + parseMessage + parseSlotMatch 32필드
// @synced 2026-04-19T08:19:17.617Z

public class HeadMessageParser {

    private static final Logger log = LoggerFactory.getLogger(HeadMessageParser.class);

    // Pattern: initslots[N]{backtick-separated-fields}^
    private static final Pattern INIT_SLOT_PATTERN =
            Pattern.compile("initslots\\[(\\d+)]([^\\^]*)\\^");

    // Pattern: update[N]{backtick-separated-fields}^
    private static final Pattern UPDATE_SLOT_PATTERN =
            Pattern.compile("update\\[(\\d+)]([^\\^]*)\\^");

    // Slot count: initslot[-1]slotcount;N^
    private static final Pattern SLOT_COUNT_PATTERN =
            Pattern.compile("slotcount;(\\d+)");

    private static final String DELIMITER = "`";

    public static List<HeadSlotData> parseMessage(String message, String source) {
        return parseMessage(message, source, 0);
    }

    public static List<HeadSlotData> parseMessage(String message, String source, int headType) {
        if (message == null || message.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Integer, HeadSlotData> slotMap = new LinkedHashMap<>();

        Matcher initMatcher = INIT_SLOT_PATTERN.matcher(message);
        while (initMatcher.find()) {
            HeadSlotData data = parseSlotMatch(initMatcher, source, headType);
            if (data != null) {
                slotMap.put(data.getSlotIndex(), data);
            }
        }

        Matcher updateMatcher = UPDATE_SLOT_PATTERN.matcher(message);
        while (updateMatcher.find()) {
            HeadSlotData data = parseSlotMatch(updateMatcher, source, headType);
            if (data != null) {
                slotMap.put(data.getSlotIndex(), data);
            }
        }

        return new ArrayList<>(slotMap.values());
    }

    private static HeadSlotData parseSlotMatch(Matcher matcher, String source, int headType) {
        try {
            int slotIndex = Integer.parseInt(matcher.group(1));
            String fieldData = matcher.group(2);

            String[] fields = fieldData.split(DELIMITER, -1);

            HeadSlotData data = new HeadSlotData();
            data.setSource(source);
            data.setHeadType(headType);
            data.setSlotIndex(slotIndex);

            // 32 backtick-separated fields
            if (fields.length >= 1) data.setRunningTime(fields[0]);
            if (fields.length >= 2) data.setSetModelName(fields[1]);
            if (fields.length >= 3) data.setRemainBattery(fields[2]);
            if (fields.length >= 4) data.setFreeArea(fields[3]);
            if (fields.length >= 5) data.setTestToolName(fields[4]);
            if (fields.length >= 6) {
                String trName = fields[5];
                data.setTestTrName(trName == null || trName.trim().isEmpty() ? "NONE" : trName);
            }
            if (fields.length >= 7) data.setState(fields[6]);
            if (fields.length >= 8) data.setTestState(fields[7]);
            if (fields.length >= 9) data.setTestArea(fields[8]);
            if (fields.length >= 10) data.setSetLocation(fields[9]);
            if (fields.length >= 11) data.setRunningState(fields[10]);
            if (fields.length >= 12) data.setIsinstalled(fields[11]);
            if (fields.length >= 13) data.setMinBattery(fields[12]);
            if (fields.length >= 14) data.setMaxBattery(fields[13]);
            if (fields.length >= 15) data.setTestHistoryIds(fields[14]);
            if (fields.length >= 16) {
                String[] sub = fields[15].split(";", -1);
                if (sub.length >= 1) data.setPreIdGlobalCxt(sub[0]);
                if (sub.length >= 2) data.setBootCxt(sub[1]);
                if (sub.length >= 3) data.setSmartReport(sub[2]);
                if (sub.length >= 4) data.setOsv(sub[3]);
            }
            if (fields.length >= 17) data.setUsbId(fields[16]);
            if (fields.length >= 18) data.setNpoCount(fields[17]);
            if (fields.length >= 19) data.setProductName(fields[18]);
            if (fields.length >= 20) data.setDeviceName(fields[19]);
            if (fields.length >= 21) data.setFileSystem(fields[20]);
            if (fields.length >= 22) data.setFwVer(fields[21]);
            if (fields.length >= 23) data.setFwDate(fields[22]);
            if (fields.length >= 24) data.setController(fields[23]);
            if (fields.length >= 25) data.setNandType(fields[24]);
            if (fields.length >= 26) data.setNandSize(fields[25]);
            if (fields.length >= 27) data.setCellType(fields[26]);
            if (fields.length >= 28) data.setDensity(fields[27]);
            if (fields.length >= 29) data.setSpecver(fields[28]);
            if (fields.length >= 30) data.setBoard(fields[29]);

            // Derive connection from state: 1~4→1(connected), 7→2(upload possible), else→0(not connected)
            int stateNum = parseStateNum(data.getState());
            if (stateNum >= 1 && stateNum <= 4) {
                data.setConnection(1);
            } else if (stateNum == 7) {
                data.setConnection(2);
            } else {
                data.setConnection(0);
            }

            // Override testState for specific state values
            String testState = data.getTestState();
            if (testState == null || testState.isEmpty() || "none".equalsIgnoreCase(testState.trim())) {
                switch (stateNum) {
                    case 1 -> data.setTestState("Waiting");
                    case 4 -> data.setTestState("Inactive");
                    case 8 -> data.setTestState("Booting");
                    case 9 -> data.setTestState("Booting Fail");
                }
            }

            return data;
        } catch (Exception e) {
            log.warn("Failed to parse slot data: {}", e.getMessage());
            return null;
        }
    }

    private static int parseStateNum(String state) {
        if (state == null || state.trim().isEmpty()) return 0;
        try {
            return Integer.parseInt(state.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Checks if the message contains a complete chunk (ends with ^end^).
     */
    public static boolean isCompleteChunk(String message) {
        return message.contains("^end^");
    }

    /**
     * Extracts the slot count from "initslot[-1]slotcount;N^".
     */
    public static int parseSlotCount(String message) {
        Matcher m = SLOT_COUNT_PATTERN.matcher(message);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return -1;
    }
}
