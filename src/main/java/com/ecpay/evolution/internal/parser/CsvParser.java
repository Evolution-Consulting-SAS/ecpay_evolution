package com.ecpay.evolution.internal.parser;

import com.ecpay.evolution.ErrorCode;
import com.ecpay.evolution.CsvProcessingException;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CsvParser {

    public List<List<String>> parse(String csvText) {
        String[] lines = splitIntoLines(csvText);
        int firstLineIndex = findFirstNonEmptyLineIndex(lines);

        if (firstLineIndex == -1) {
            throw new CsvProcessingException(ErrorCode.CSV_EMPTY_FILE, 0, null, null, null);
        }

        String firstLine = lines[firstLineIndex];
        char delimiter = DelimiterDetector.detect(firstLine);
        CSVParser openCsvParser = new CSVParserBuilder().withSeparator(delimiter).build();

        int dataStartIndex = isHeader(firstLine, openCsvParser) ? firstLineIndex + 1 : firstLineIndex;
        return parseDataRows(openCsvParser, lines, dataStartIndex);
    }

    private String[] splitIntoLines(String csvText) {
        return csvText.split("\\R", -1);
    }

    private int findFirstNonEmptyLineIndex(String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].isBlank()) return i;
        }
        return -1;
    }

    private boolean isHeader(String line, CSVParser parser) {
        try {
            String[] cells = parser.parseLine(line);
            String[] normalized = Arrays.stream(cells)
                    .map(this::normalizeFieldValue)
                    .toArray(String[]::new);
            return HeaderDetector.isHeader(normalized);
        } catch (IOException e) {
            return false;
        }
    }

    private List<List<String>> parseDataRows(CSVParser parser, String[] lines, int dataStartIndex) {
        List<List<String>> rows = new ArrayList<>();
        int dataRowNumber = 0;
        for (int i = dataStartIndex; i < lines.length; i++) {
            if (lines[i].isBlank()) continue;
            dataRowNumber++;
            rows.add(parseSingleDataRow(parser, lines[i], dataRowNumber));
        }
        return rows;
    }

    private List<String> parseSingleDataRow(CSVParser parser, String line, int dataRowNumber) {
        try {
            String[] values = parser.parseLine(line);
            List<String> row = new ArrayList<>(values.length);
            for (String v : values) row.add(normalizeFieldValue(v));
            return row;
        } catch (IOException e) {
            throw new CsvProcessingException(ErrorCode.CSV_FORMAT_INVALID, dataRowNumber, null, line, null);
        }
    }

    private String normalizeFieldValue(String value) {
        return stripSingleQuotes(value.trim());
    }

    private String stripSingleQuotes(String value) {
        while (value.length() >= 2 && value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'') {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }
}
