// @source src/main/java/com/samsung/move/binmapper/controller/BinMapperController.java
// @lines 1-54
// @note /api/binmapper/parse (MultipartFile + 6가지 struct 소스) + parse-struct + parse-header
// @synced 2026-05-01T01:05:23.643Z

package com.samsung.move.binmapper.controller;

import com.samsung.move.binmapper.model.Endianness;
import com.samsung.move.binmapper.model.MappedResult;
import com.samsung.move.binmapper.model.StructDefinition;
import com.samsung.move.binmapper.service.BinMapperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/binmapper")
@RequiredArgsConstructor
public class BinMapperController {

    private final BinMapperService binMapperService;

    @PostMapping("/parse")
    public ResponseEntity<MappedResult> parse(
            @RequestParam(required = false) MultipartFile binaryFile,
            @RequestParam(required = false) String serverPath,
            @RequestParam(required = false) String serverName,
            @RequestParam(required = false) String structText,
            @RequestParam(required = false) MultipartFile structFile,
            @RequestParam(required = false) Long predefinedStructId,
            @RequestParam(required = false) String structName,
            @RequestParam(defaultValue = "AUTO") String endianness,
            @RequestParam(defaultValue = "false") boolean repeatAsArray) throws Exception {
        Endianness e = Endianness.valueOf(endianness.toUpperCase());
        MappedResult result = binMapperService.parse(
                binaryFile, serverPath, serverName, structText, structFile,
                predefinedStructId, structName, e, repeatAsArray);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/parse-struct")
    public ResponseEntity<List<StructDefinition>> parseStruct(@RequestBody Map<String, String> body) {
        String structText = body.get("structText");
        List<StructDefinition> structs = binMapperService.parseStructOnly(structText);
        return ResponseEntity.ok(structs);
    }

    @PostMapping("/parse-header")
    public ResponseEntity<List<StructDefinition>> parseHeader(@RequestParam("file") MultipartFile headerFile) throws Exception {
        List<StructDefinition> structs = binMapperService.parseHeader(headerFile);
        return ResponseEntity.ok(structs);
    }
}
