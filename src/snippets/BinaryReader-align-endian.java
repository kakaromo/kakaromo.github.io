// @source src/main/java/com/samsung/move/binmapper/service/BinaryReaderService.java
// @lines 267-322
// @note getFieldSize/Alignment + align (padding 계산) + resolveEndianness (ELF magic) + readCharArray
// @synced 2026-05-01T01:05:23.642Z

    private int getFieldSize(StructField field) {
        if (field.getNestedStruct() != null) {
            return calculateStructSize(field.getNestedStruct());
        }
        if (field.getType() != null) {
            return field.getType().getSize();
        }
        return 4; // default for unknown types
    }

    private int getFieldAlignment(StructField field) {
        if (field.getNestedStruct() != null) {
            // alignment of a struct = max alignment of its fields
            int maxAlign = 1;
            for (StructField f : field.getNestedStruct().getFields()) {
                maxAlign = Math.max(maxAlign, getFieldAlignment(f));
            }
            return maxAlign;
        }
        return getFieldSize(field);
    }

    private int align(int offset, int alignment) {
        if (alignment <= 1) return offset;
        int remainder = offset % alignment;
        return remainder == 0 ? offset : offset + (alignment - remainder);
    }

    private ByteOrder resolveEndianness(Endianness endianness, byte[] data) {
        if (endianness == Endianness.BIG) return ByteOrder.BIG_ENDIAN;
        if (endianness == Endianness.LITTLE) return ByteOrder.LITTLE_ENDIAN;

        // AUTO: try magic number detection
        if (data.length >= 4) {
            // ELF: 0x7F 'E' 'L' 'F'
            if (data[0] == 0x7F && data[1] == 'E' && data[2] == 'L' && data[3] == 'F') {
                if (data.length > 5) {
                    return data[5] == 1 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
                }
            }
            // UFS descriptor: check if big-endian interpretation gives sensible values
            // Default: little-endian (x86/ARM)
        }
        return ByteOrder.LITTLE_ENDIAN;
    }

    private String readCharArray(byte[] data, int offset, int length) {
        int end = Math.min(offset + length, data.length);
        StringBuilder sb = new StringBuilder();
        for (int i = offset; i < end; i++) {
            byte b = data[i];
            if (b == 0) break;
            sb.append(b >= 32 && b < 127 ? (char) b : '.');
        }
        return sb.toString();
    }
