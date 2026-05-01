// @source src/main/java/com/samsung/move/binmapper/service/BinaryReaderService.java
// @lines 189-265
// @note readPrimitiveField (ASCII 힌트 + enum 라벨) + mapBitfield (storage unit + bit mask)
// @synced 2026-05-01T01:05:23.642Z

    private MappedField readPrimitiveField(String name, StructField field, byte[] data,
                                           int offset, ByteOrder byteOrder) {
        int size = field.getType().getSize();
        String hex = toHex(data, offset, size);
        ByteBuffer buf = ByteBuffer.wrap(data, offset, size).order(byteOrder);
        Object value = field.getType().readValue(buf);

        // Append ASCII representation for integer values if printable
        if (field.getEnumType() == null && value instanceof Number numVal) {
            String ascii = toAsciiHint(numVal, size);
            if (ascii != null) {
                value = value + " ('" + ascii + "')";
            }
        }

        // If this field has an enum type, resolve the enum label
        if (field.getEnumType() != null) {
            long longVal;
            if (value instanceof Number numVal) {
                longVal = numVal.longValue();
            } else if (value instanceof String strVal) {
                try { longVal = Long.parseUnsignedLong(strVal); } catch (NumberFormatException e) { longVal = Long.MIN_VALUE; }
            } else {
                longVal = Long.MIN_VALUE;
            }
            if (longVal != Long.MIN_VALUE) {
                String enumLabel = field.getEnumType().getValueToName().get(longVal);
                if (enumLabel != null) {
                    value = enumLabel + " (" + value + ")";
                }
            }
        }

        return MappedField.builder()
                .fieldName(name)
                .typeName(field.getCustomTypeName())
                .offset(offset)
                .size(size)
                .hexBytes(hex)
                .parsedValue(value)
                .build();
    }

    private MappedField mapBitfield(StructField field, byte[] data, int offset,
                                    ByteOrder byteOrder) {
        int storageSize = field.getType() != null ? field.getType().getSize() : 4;
        if (offset + storageSize > data.length) {
            return MappedField.builder()
                    .fieldName(field.getName())
                    .typeName(field.getCustomTypeName() + ":" + field.getBitfieldWidth())
                    .offset(offset)
                    .size(storageSize)
                    .hexBytes("??")
                    .parsedValue("<out of bounds>")
                    .build();
        }
        String hex = toHex(data, offset, storageSize);
        ByteBuffer buf = ByteBuffer.wrap(data, offset, storageSize).order(byteOrder);
        long rawValue = switch (storageSize) {
            case 1 -> Byte.toUnsignedLong(buf.get());
            case 2 -> Short.toUnsignedLong(buf.getShort());
            case 4 -> Integer.toUnsignedLong(buf.getInt());
            case 8 -> buf.getLong();
            default -> 0;
        };
        long mask = (1L << field.getBitfieldWidth()) - 1;
        long value = rawValue & mask; // simplified: doesn't track bit offset within storage

        return MappedField.builder()
                .fieldName(field.getName())
                .typeName(field.getCustomTypeName() + ":" + field.getBitfieldWidth())
                .offset(offset)
                .size(storageSize)
                .hexBytes(hex)
                .parsedValue(value)
                .build();
    }
