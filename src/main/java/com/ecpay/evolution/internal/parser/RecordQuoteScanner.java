package com.ecpay.evolution.internal.parser;

import java.util.OptionalInt;

final class RecordQuoteScanner {

    private static final char QUOTE = '"';

    private enum State { FIELD_START, UNQUOTED, QUOTED, AFTER_CLOSING_QUOTE }

    private RecordQuoteScanner() {}

    static OptionalInt firstMalformedRecordStartLine(String csvText, char delimiter) {
        State state = State.FIELD_START;
        int line = 1;
        int recordStartLine = 1;
        int index = 0;
        while (index < csvText.length()) {
            char current = csvText.charAt(index);
            if (isLineBreak(current)) {
                index += lineBreakLength(csvText, index);
                line++;
                if (state != State.QUOTED) {
                    state = State.FIELD_START;
                    recordStartLine = line;
                }
                continue;
            }
            switch (state) {
                case FIELD_START -> state = current == QUOTE ? State.QUOTED
                        : current == delimiter ? State.FIELD_START : State.UNQUOTED;
                case UNQUOTED -> {
                    if (current == QUOTE) {
                        return OptionalInt.of(recordStartLine);
                    }
                    if (current == delimiter) {
                        state = State.FIELD_START;
                    }
                }
                case QUOTED -> {
                    if (current == QUOTE) {
                        if (index + 1 < csvText.length() && csvText.charAt(index + 1) == QUOTE) {
                            index++;
                        } else {
                            state = State.AFTER_CLOSING_QUOTE;
                        }
                    }
                }
                case AFTER_CLOSING_QUOTE -> {
                    if (current != delimiter) {
                        return OptionalInt.of(recordStartLine);
                    }
                    state = State.FIELD_START;
                }
            }
            index++;
        }
        return state == State.QUOTED ? OptionalInt.of(recordStartLine) : OptionalInt.empty();
    }

    private static int lineBreakLength(String csvText, int index) {
        return csvText.startsWith("\r\n", index) ? 2 : 1;
    }

    private static boolean isLineBreak(char character) {
        return character == '\n' || character == '\r';
    }
}
