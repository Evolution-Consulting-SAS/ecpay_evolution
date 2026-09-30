package com.ecpay.evolution.internal.parser;

import com.ecpay.evolution.CsvProcessingException;
import com.ecpay.evolution.ErrorCode;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.IOException;

final class RecordDelimiterDetector {

    private static final char DEFAULT_DELIMITER = ',';

    private RecordDelimiterDetector() {}

    static char detect(String csvText) {
        char best = DEFAULT_DELIMITER;
        int bestWidth = 1;
        boolean ambiguous = false;
        for (char candidate : DelimiterDetector.CANDIDATES) {
            int width = firstRecordWidth(csvText, candidate);
            if (width > bestWidth) {
                best = candidate;
                bestWidth = width;
                ambiguous = false;
            } else if (width == bestWidth && width > 1) {
                ambiguous = true;
            }
        }
        if (ambiguous) {
            throw new CsvProcessingException(
                    ErrorCode.CSV_DELIMITER_AMBIGUOUS, 0, null, null, null);
        }
        return best;
    }

    private static int firstRecordWidth(String csvText, char candidate) {
        try (CSVReader reader = CsvTableReader.openReader(csvText, candidate)) {
            String[] cells;
            while ((cells = reader.readNext()) != null) {
                if (!CsvTableReader.isBlankRecord(cells)) {
                    return cells.length;
                }
            }
            return 0;
        } catch (IOException | CsvValidationException e) {
            return 0;
        }
    }
}
