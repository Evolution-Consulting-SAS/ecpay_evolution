package com.ecpay.evolution.internal.parser;

import com.ecpay.evolution.ErrorCode;
import com.ecpay.evolution.CsvProcessingException;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CsvParser {

    public List<List<String>> parse(String csvText) {
        String[] lines = splitIntoLines(csvText);
        int firstLineIndex = findFirstNonEmptyLineIndex(lines);

        if (firstLineIndex == -1) {
            throw new CsvProcessingException(ErrorCode.CSV_EMPTY_FILE, 0, null, null, null);
        }

        String firstLine = lines[firstLineIndex];
        CSVParser openCsvParser = buildParserWithAutoDetectedDelimiter(firstLine);

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

    private CSVParser buildParserWithAutoDetectedDelimiter(String firstLine) {
        char delimiter = DelimiterDetector.detect(firstLine);
        return new CSVParserBuilder().withSeparator(delimiter).build();
    }

    private boolean isHeader(String line, CSVParser parser) {
        try {
            String[] cells = parser.parseLine(line);
            return HeaderDetector.isHeader(cells);
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
            for (String v : values) row.add(v.trim());
            return row;
        } catch (IOException e) {
            throw new CsvProcessingException(ErrorCode.CSV_FORMAT_INVALID, dataRowNumber, null, line, null);
        }
    }
}
