// @source src/main/java/com/samsung/move/binmapper/service/BinaryReaderService.java
// @lines 1-72
// @note map 엔트리 + resolveEndianness + calculateStructSize (union vs struct, pack 분기)
// @synced 2026-04-19T09:18:51.185Z

package com.samsung.move.binmapper.service;

import com.samsung.move.binmapper.model.*;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

@Service
public class BinaryReaderService {

    public MappedResult map(StructDefinition structDef, byte[] data, Endianness endianness,
                            boolean repeatAsArray) {
        ByteOrder byteOrder = resolveEndianness(endianness, data);
        String resolvedEndianness = byteOrder == ByteOrder.LITTLE_ENDIAN ? "LITTLE" : "BIG";

        int structSize = calculateStructSize(structDef);
        int instanceCount = repeatAsArray && structSize > 0 ? data.length / structSize : 1;

        List<MappedResult.MappedInstance> instances = new ArrayList<>();
        for (int i = 0; i < instanceCount; i++) {
            int baseOffset = i * structSize;
            if (baseOffset + structSize > data.length && i > 0) break;
            List<MappedField> fields = mapFields(structDef, data, baseOffset, byteOrder);
            instances.add(MappedResult.MappedInstance.builder()
                    .index(i)
                    .offset(baseOffset)
                    .fields(fields)
                    .build());
        }

        int rawLimit = Math.min(data.length, 64 * 1024);
        int[] rawBytes = new int[rawLimit];
        for (int i = 0; i < rawLimit; i++) {
            rawBytes[i] = Byte.toUnsignedInt(data[i]);
        }

        return MappedResult.builder()
                .structName(structDef.getName())
                .structSize(structSize)
                .totalBytes(data.length)
                .endianness(resolvedEndianness)
                .instanceCount(instances.size())
                .instances(instances)
                .hexDump(toHexDump(data, rawLimit))
                .rawBytes(rawBytes)
                .build();
    }

    public int calculateStructSize(StructDefinition structDef) {
        if (structDef.isUnion()) {
            // Union size = max of all member sizes
            int maxSize = 0;
            for (StructField field : structDef.getFields()) {
                int fieldSize = getFieldSize(field);
                int count = Math.max(field.getArraySize(), 1);
                maxSize = Math.max(maxSize, fieldSize * count);
            }
            return maxSize;
        }
        int offset = 0;
        for (StructField field : structDef.getFields()) {
            int fieldSize = getFieldSize(field);
            int fieldAlign = structDef.isPacked() ? 1 : getFieldAlignment(field);
            offset = align(offset, fieldAlign);
            int count = Math.max(field.getArraySize(), 1);
            offset += fieldSize * count;
        }
        return offset;
    }
