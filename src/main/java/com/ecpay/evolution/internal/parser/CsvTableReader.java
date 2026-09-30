package com.ecpay.evolution.internal.parser;

import com.ecpay.evolution.CsvProcessingException;
import com.ecpay.evolution.CsvRecord;
import com.ecpay.evolution.CsvTable;
import com.ecpay.evolution.ErrorCode;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.RFC4180ParserBuilder;
import com.opencsv.exceptions.CsvMalformedLineException;
import com.opencsv.exceptions.CsvValidationException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class CsvTableReader {

    public CsvTable read(String csvText) {
        char delimiter = RecordDelimiterDetector.detect(csvText);
        List<CsvRecord> records = readNonBlankRecords(csvText, delimiter);
        if (records.isEmpty()) {
            throw new CsvProcessingException(ErrorCode.CSV_EMPTY_FILE, 0, null, null, null);
        }
        return new CsvTable(delimiter, records.get(0), records.subList(1, records.size()));
    }

    static CSVReader openReader(String csvText, char delimiter) {
        return new CSVReaderBuilder(new StringReader(csvText))
                .withCSVParser(new RFC4180ParserBuilder().withSeparator(delimiter).build())
                .withKeepCarriageReturn(false)
                .build();
    }

    static boolean isBlankRecord(String[] cells) {
        return cells.length == 1 && cells[0].isBlank();
    }

    private List<CsvRecord> readNonBlankRecords(String csvText, char delimiter) {
        List<CsvRecord> records = new ArrayList<>();
        try (CSVReader reader = openReader(csvText, delimiter)) {
            while (true) {
                int startLine = Math.toIntExact(reader.getLinesRead()) + 1;
                String[] cells = reader.readNext();
                if (cells == null) {
                    return records;
                }
                if (!isBlankRecord(cells)) {
                    records.add(new CsvRecord(
                            Math.toIntExact(reader.getRecordsRead()),
                            startLine,
                            Math.toIntExact(reader.getLinesRead()),
                            List.of(cells)));
                }
            }
        } catch (CsvMalformedLineException e) {
            int startLine = Math.toIntExact(e.getLineNumber());
            throw (CsvProcessingException) new CsvProcessingException(
                    ErrorCode.CSV_FORMAT_INVALID, startLine, null, null, null).initCause(e);
        } catch (IOException | CsvValidationException e) {
            throw (CsvProcessingException) new CsvProcessingException(
                    ErrorCode.CSV_FORMAT_INVALID, 0, null, null, null).initCause(e);
        }
    }
}
