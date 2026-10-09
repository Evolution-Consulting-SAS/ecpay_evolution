package com.ecpay.evolution.internal.parser;

final class HeaderQuoteScanner {

    private static final char QUOTE = '"';

    private HeaderQuoteScanner() {}

    static boolean hasQuoteInUnquotedField(String csvText, int startLine, char delimiter) {
        int index = startOffsetOfLine(csvText, startLine);
        boolean fieldStart = true;
        boolean quoted = false;
        while (index < csvText.length()) {
            char current = csvText.charAt(index);
            if (quoted) {
                if (current == QUOTE) {
                    if (isQuoteAt(csvText, index + 1)) {
                        index += 2;
                        continue;
                    }
                    quoted = false;
                }
            } else if (isLineBreak(current)) {
                return false;
            } else if (current == delimiter) {
                fieldStart = true;
                index++;
                continue;
            } else if (current == QUOTE) {
                if (!fieldStart) {
                    return true;
                }
                quoted = true;
            }
            fieldStart = false;
            index++;
        }
        return false;
    }

    private static int startOffsetOfLine(String csvText, int line) {
        int offset = 0;
        for (int current = 1; current < line && offset < csvText.length(); current++) {
            while (offset < csvText.length() && !isLineBreak(csvText.charAt(offset))) {
                offset++;
            }
            if (csvText.startsWith("\r\n", offset)) {
                offset++;
            }
            offset++;
        }
        return offset;
    }

    private static boolean isQuoteAt(String csvText, int index) {
        return index < csvText.length() && csvText.charAt(index) == QUOTE;
    }

    private static boolean isLineBreak(char character) {
        return character == '\n' || character == '\r';
    }
}
