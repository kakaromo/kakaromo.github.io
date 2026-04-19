// @source src/main/java/com/samsung/move/binmapper/service/BinaryReaderService.java
// @lines 74-187
// @note mapFields 재귀 — union/bitfield/char 배열/배열/nested struct/primitive 6 분기
// @synced 2026-04-19T09:18:51.185Z

    private List<MappedField> mapFields(StructDefinition structDef, byte[] data,
                                        int baseOffset, ByteOrder byteOrder) {
        List<MappedField> result = new ArrayList<>();
        int offset = baseOffset;

        boolean isUnion = structDef.isUnion();
        int unionStartOffset = baseOffset;

        for (StructField field : structDef.getFields()) {
            if (isUnion) {
                // All union members start at the same offset
                offset = unionStartOffset;
            }

            int fieldSize = getFieldSize(field);
            int fieldAlign = structDef.isPacked() ? 1 : getFieldAlignment(field);
            offset = align(offset, fieldAlign);

            int count = Math.max(field.getArraySize(), 1);

            if (field.getBitfieldWidth() > 0) {
                // Bitfield: read the storage unit, extract bits
                result.add(mapBitfield(field, data, offset, byteOrder));
                // Note: simplified - each bitfield reads from its aligned position
                // A full implementation would track bit position within storage units
                offset += fieldSize;
                continue;
            }

            if (count > 1 && field.getNestedStruct() == null && field.getType() == CppType.CHAR) {
                // char array -> read as string
                String hex = toHex(data, offset, fieldSize * count);
                String value = readCharArray(data, offset, count);
                result.add(MappedField.builder()
                        .fieldName(field.getName())
                        .typeName("char[" + count + "]")
                        .offset(offset)
                        .size(fieldSize * count)
                        .hexBytes(hex)
                        .parsedValue(value)
                        .build());
                offset += fieldSize * count;
            } else if (count > 1) {
                // Array of primitives or structs
                List<MappedField> children = new ArrayList<>();
                int arrayStart = offset;
                for (int i = 0; i < count; i++) {
                    if (offset + fieldSize > data.length) break;
                    if (field.getNestedStruct() != null) {
                        List<MappedField> nested = mapFields(field.getNestedStruct(), data, offset, byteOrder);
                        children.add(MappedField.builder()
                                .fieldName("[" + i + "]")
                                .typeName(field.getCustomTypeName())
                                .offset(offset)
                                .size(fieldSize)
                                .hexBytes(toHex(data, offset, fieldSize))
                                .children(nested)
                                .build());
                    } else {
                        children.add(readPrimitiveField("[" + i + "]", field, data, offset, byteOrder));
                    }
                    offset += fieldSize;
                }
                result.add(MappedField.builder()
                        .fieldName(field.getName())
                        .typeName(field.getCustomTypeName() + "[" + count + "]")
                        .offset(arrayStart)
                        .size(fieldSize * count)
                        .hexBytes(toHex(data, arrayStart, Math.min(fieldSize * count, 32)))
                        .children(children)
                        .build());
            } else if (field.getNestedStruct() != null) {
                // Nested struct
                List<MappedField> children = mapFields(field.getNestedStruct(), data, offset, byteOrder);
                int nestedSize = calculateStructSize(field.getNestedStruct());
                result.add(MappedField.builder()
                        .fieldName(field.getName())
                        .typeName(field.getCustomTypeName())
                        .offset(offset)
                        .size(nestedSize)
                        .hexBytes(toHex(data, offset, Math.min(nestedSize, 32)))
                        .children(children)
                        .build());
                offset += nestedSize;
            } else if (field.getType() != null) {
                // Primitive field
                if (offset + fieldSize <= data.length) {
                    result.add(readPrimitiveField(field.getName(), field, data, offset, byteOrder));
                } else {
                    result.add(MappedField.builder()
                            .fieldName(field.getName())
                            .typeName(field.getCustomTypeName())
                            .offset(offset)
                            .size(fieldSize)
                            .hexBytes("??")
                            .parsedValue("<out of bounds>")
                            .build());
                }
                offset += fieldSize;
            } else {
                // Unknown type - use same size as getFieldSize (4 bytes default)
                result.add(MappedField.builder()
                        .fieldName(field.getName())
                        .typeName(field.getCustomTypeName())
                        .offset(offset)
                        .size(fieldSize)
                        .hexBytes(toHex(data, offset, Math.min(fieldSize, data.length - offset)))
                        .parsedValue("<unknown type>")
                        .build());
                offset += fieldSize;
            }
        }
        return result;
    }
