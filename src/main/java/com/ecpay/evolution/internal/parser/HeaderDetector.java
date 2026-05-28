package com.ecpay.evolution.internal.parser;

import java.util.Arrays;

class HeaderDetector {

    static boolean isHeader(String[] cells) {
        return Arrays.stream(cells)
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
        return cell.matches("-?\\d+\\.\\d+([eE]-?\\d+)?");
    }

    private static boolean isDate(String cell) {
        return cell.matches("\\d{4}-\\d{2}-\\d{2}.*");
    }
}
