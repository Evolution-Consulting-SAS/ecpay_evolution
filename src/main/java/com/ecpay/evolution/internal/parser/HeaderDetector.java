package com.ecpay.evolution.internal.parser;

import java.util.Arrays;
import java.util.Objects;

class HeaderDetector {

    static boolean isHeader(String[] cells) {
        if (cells == null || cells.length == 0) return false;
        return Arrays.stream(cells)
                .filter(Objects::nonNull)
                .map(String::trim)
                .noneMatch(HeaderDetector::cellLooksLikeData);
    }

    private static boolean cellLooksLikeData(String cell) {
        return isInteger(cell) || isDecimal(cell) || isDate(cell);
    }

    private static boolean isInteger(String cell) {
        return cell.matches("-?\\d+");
    }

    private static boolean isDecimal(String cell) {
        return cell.matches("-?\\d+[.,]\\d+([eE][+-]?\\d+)?");
    }

    private static boolean isDate(String cell) {
        return cell.matches("\\d{4}[-/]\\d{2}[-/]\\d{2}.*")
            || cell.matches("\\d{2}[-/]\\d{2}[-/]\\d{4}.*");
    }
}
