package com.ecpay.evolution.internal.parser;

import com.ecpay.evolution.CsvProcessingException;
import com.ecpay.evolution.CsvRecord;
import com.ecpay.evolution.ErrorCode;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

final class RecordDelimiterDetector {

    private static final char DEFAULT_DELIMITER = ',';

    private RecordDelimiterDetector() {}

    static char detect(String csvText) {
        char best = DEFAULT_DELIMITER;
        List<String> bestHeader = List.of();
        boolean ambiguous = false;
        boolean someCandidateFailed = false;
        for (char candidate : DelimiterDetector.CANDIDATES) {
            Optional<List<String>> header = parseHeader(csvText, candidate);
            if (header.isEmpty()) {
                someCandidateFailed = true;
                continue;
            }
            int width = header.get().size();
            if (width > Math.max(bestHeader.size(), 1)) {
                best = candidate;
                bestHeader = header.get();
                ambiguous = false;
            } else if (width > 1 && width == bestHeader.size()) {
                ambiguous = true;
            }
        }
        if (ambiguous || (someCandidateFailed && containsQuote(bestHeader))) {
            throw new CsvProcessingException(
                    ErrorCode.CSV_DELIMITER_AMBIGUOUS, 0, null, null, null);
        }
        return best;
    }

    private static Optional<List<String>> parseHeader(String csvText, char candidate) {
        try (CSVReader reader = CsvTableReader.openReader(csvText, candidate)) {
            return Optional.of(CsvTableReader.readHeader(reader)
                    .map(CsvRecord::cells)
                    .orElse(List.of()));
        } catch (IOException | CsvValidationException e) {
            return Optional.empty();
        }
    }

    private static boolean containsQuote(List<String> cells) {
        return cells.stream().anyMatch(cell -> cell.indexOf('"') >= 0);
    }
}
