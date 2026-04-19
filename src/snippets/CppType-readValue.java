// @source src/main/java/com/samsung/move/binmapper/model/CppType.java
// @lines 160-197
// @note enum 13종 + fromString(alias map 100+) + readValue(ByteBuffer) switch 분기
// @synced 2026-04-19T09:49:20.703Z

    CppType(String cName, int size) {
        this.cName = cName;
        this.size = size;
    }

    public String getCName() {
        return cName;
    }

    public int getSize() {
        return size;
    }

    public static CppType fromString(String typeName) {
        return ALIAS_MAP.get(typeName.trim());
    }

    public Object readValue(ByteBuffer buffer) {
        return switch (this) {
            case INT8 -> buffer.get();
            case UINT8 -> Byte.toUnsignedInt(buffer.get());
            case INT16 -> buffer.getShort();
            case UINT16 -> Short.toUnsignedInt(buffer.getShort());
            case INT32 -> buffer.getInt();
            case UINT32 -> Integer.toUnsignedLong(buffer.getInt());
            case INT64 -> buffer.getLong();
            case UINT64 -> Long.toUnsignedString(buffer.getLong());
            case FLOAT -> buffer.getFloat();
            case DOUBLE -> buffer.getDouble();
            case CHAR -> {
                byte b = buffer.get();
                yield (b >= 32 && b < 127) ? String.valueOf((char) b) : String.format("\\x%02x", b);
            }
            case BOOL -> buffer.get() != 0;
            case BOOL32 -> buffer.getInt() != 0;
        };
    }
}
