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
    private static final CsvRecord NO_HEADER = new CsvRecord(0, 0, 0, List.of());

    private RecordDelimiterDetector() {}

    static char detect(String csvText) {
        char best = DEFAULT_DELIMITER;
        CsvRecord bestHeader = NO_HEADER;
        boolean ambiguous = false;
        boolean someCandidateFailed = false;
        for (char candidate : DelimiterDetector.CANDIDATES) {
            Optional<CsvRecord> header = parseHeader(csvText, candidate);
            if (header.isEmpty()) {
                someCandidateFailed = true;
                continue;
            }
            int width = header.get().cells().size();
            if (width > Math.max(bestHeader.cells().size(), 1)) {
                best = candidate;
                bestHeader = header.get();
                ambiguous = false;
            } else if (width > 1 && width == bestHeader.cells().size()) {
                ambiguous = true;
            }
        }
        if (ambiguous || (someCandidateFailed && hasLiteralQuote(csvText, bestHeader, best))) {
            throw new CsvProcessingException(
                    ErrorCode.CSV_DELIMITER_AMBIGUOUS, 0, null, null, null);
        }
        return best;
    }

    private static boolean hasLiteralQuote(String csvText, CsvRecord header, char delimiter) {
        return header != NO_HEADER
                && HeaderQuoteScanner.hasQuoteInUnquotedField(csvText, header.startLine(), delimiter);
    }

    private static Optional<CsvRecord> parseHeader(String csvText, char candidate) {
        try (CSVReader reader = CsvTableReader.openReader(csvText, candidate)) {
            return Optional.of(CsvTableReader.readHeader(reader).orElse(NO_HEADER));
        } catch (IOException | CsvValidationException e) {
            return Optional.empty();
        }
    }
}
