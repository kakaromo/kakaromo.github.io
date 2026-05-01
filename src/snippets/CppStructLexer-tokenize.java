// @source src/main/java/com/samsung/move/binmapper/parser/CppStructLexer.java
// @lines 1-80
// @note KEYWORDS Set + tokenize 메인 루프 (#pragma / __attribute__ / ident / number 분기)
// @synced 2026-05-01T01:10:31.183Z

package com.samsung.move.binmapper.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CppStructLexer {

    private static final Set<String> KEYWORDS = Set.of(
            "struct", "enum", "typedef", "union", "const", "signed", "unsigned"
    );

    private final String input;
    private int pos;
    private int line = 1;
    private int column = 1;

    public CppStructLexer(String input) {
        this.input = input;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (pos < input.length()) {
            skipWhitespaceAndComments();
            if (pos >= input.length()) break;

            char c = peek();
            int startLine = line;
            int startCol = column;

            if (c == '#') {
                Token pragmaToken = tryPragmaPack(startLine, startCol);
                if (pragmaToken != null) {
                    tokens.add(pragmaToken);
                } else if (isIfZero()) {
                    skipIfZeroBlock();
                } else {
                    skipLine(); // skip other preprocessor directives
                }
                continue;
            }

            if (c == '_' && lookAhead("__attribute__")) {
                tokens.add(parseAttribute(startLine, startCol));
                continue;
            }

            if (Character.isLetter(c) || c == '_') {
                String ident = readIdentifier();
                TokenType type = switch (ident) {
                    case "struct" -> TokenType.STRUCT;
                    case "enum" -> TokenType.ENUM;
                    case "typedef" -> TokenType.TYPEDEF;
                    case "union" -> TokenType.UNION;
                    case "const" -> TokenType.CONST;
                    case "signed" -> TokenType.SIGNED;
                    case "unsigned" -> TokenType.UNSIGNED;
                    default -> TokenType.IDENT;
                };
                tokens.add(new Token(type, ident, startLine, startCol));
                continue;
            }

            if (Character.isDigit(c)) {
                tokens.add(new Token(TokenType.NUMBER, readNumber(), startLine, startCol));
                continue;
            }

            if (c == '<' && pos + 1 < input.length() && input.charAt(pos + 1) == '<') {
                advance(); advance();
                tokens.add(new Token(TokenType.SHIFT_LEFT, "<<", startLine, startCol));
                continue;
            }

            advance();
            TokenType type = switch (c) {
                case '{' -> TokenType.LBRACE;
                case '}' -> TokenType.RBRACE;
                case '[' -> TokenType.LBRACKET;
