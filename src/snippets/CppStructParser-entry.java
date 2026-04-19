// @source src/main/java/com/samsung/move/binmapper/parser/CppStructParser.java
// @lines 1-80
// @note parse 엔트리 + typedef/struct/union/enum 분기 + pragma pack 상태 유지
// @synced 2026-04-19T09:32:45.539Z

package com.samsung.move.binmapper.parser;

import com.samsung.move.binmapper.model.CppType;
import com.samsung.move.binmapper.model.EnumDefinition;
import com.samsung.move.binmapper.model.StructDefinition;
import com.samsung.move.binmapper.model.StructField;

import java.util.*;

public class CppStructParser {

    private static final Set<String> QUALIFIERS = Set.of(
            "volatile", "static", "extern", "inline", "register",
            "__inline", "__volatile", "__restrict", "restrict",
            "_Atomic", "thread_local", "_Thread_local"
    );

    private List<Token> tokens;
    private int pos;
    private final Map<String, StructDefinition> typedefMap = new LinkedHashMap<>();
    private final Map<String, EnumDefinition> enumMap = new LinkedHashMap<>();
    private int pragmaPackValue = 0; // 0 means default alignment

    public List<StructDefinition> parse(String input) {
        this.tokens = new CppStructLexer(input).tokenize();
        this.pos = 0;
        List<StructDefinition> structs = new ArrayList<>();

        while (!isAtEnd()) {
            if (check(TokenType.PRAGMA_PACK)) {
                handlePragmaPack();
            } else if (check(TokenType.TYPEDEF)) {
                advance(); // skip typedef
                if (check(TokenType.STRUCT)) {
                    StructDefinition sd = parseStruct();
                    if (sd != null) {
                        if (check(TokenType.IDENT)) {
                            String alias = advance().getValue();
                            sd.setName(alias);
                            typedefMap.put(alias, sd);
                        }
                        expect(TokenType.SEMICOLON);
                        structs.add(sd);
                    }
                } else if (check(TokenType.UNION)) {
                    StructDefinition sd = parseUnion();
                    if (sd != null) {
                        if (check(TokenType.IDENT)) {
                            String alias = advance().getValue();
                            sd.setName(alias);
                            typedefMap.put(alias, sd);
                        }
                        expect(TokenType.SEMICOLON);
                        structs.add(sd);
                    }
                } else if (check(TokenType.ENUM)) {
                    EnumDefinition ed = parseEnum();
                    // typedef enum name: "typedef enum { ... } Color;"
                    if (ed != null && check(TokenType.IDENT)) {
                        String alias = advance().getValue();
                        if (ed.getName() == null) {
                            ed.setName(alias);
                        }
                        enumMap.put(alias, ed);
                    }
                    expect(TokenType.SEMICOLON);
                } else {
                    skipToSemicolon();
                }
            } else if (check(TokenType.STRUCT)) {
                StructDefinition sd = parseStruct();
                if (sd != null) {
                    expect(TokenType.SEMICOLON);
                    structs.add(sd);
                    if (sd.getName() != null) {
                        typedefMap.put(sd.getName(), sd);
                    }
                }
            } else if (check(TokenType.UNION)) {
                StructDefinition sd = parseUnion();
