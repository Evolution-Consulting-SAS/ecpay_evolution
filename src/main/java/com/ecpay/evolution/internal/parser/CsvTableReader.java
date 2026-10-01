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
import java.util.Optional;

public class CsvTableReader {

    public CsvTable read(String csvText) {
        char delimiter = RecordDelimiterDetector.detect(csvText);
        RecordQuoteScanner.firstMalformedRecordStartLine(csvText, delimiter).ifPresent(startLine -> {
            throw new CsvProcessingException(ErrorCode.CSV_FORMAT_INVALID, startLine, null, null, null);
        });
        try (CSVReader reader = openReader(csvText, delimiter)) {
            CsvRecord header = readHeader(reader).orElseThrow(() ->
                    new CsvProcessingException(ErrorCode.CSV_EMPTY_FILE, 0, null, null, null));
            List<CsvRecord> records = new ArrayList<>();
            CsvRecord record;
            while ((record = readRecord(reader)) != null) {
                records.add(record);
            }
            return new CsvTable(delimiter, header, records);
        } catch (CsvMalformedLineException e) {
            int startLine = Math.toIntExact(e.getLineNumber());
            throw (CsvProcessingException) new CsvProcessingException(
                    ErrorCode.CSV_FORMAT_INVALID, startLine, null, null, null).initCause(e);
        } catch (IOException | CsvValidationException e) {
            throw (CsvProcessingException) new CsvProcessingException(
                    ErrorCode.CSV_FORMAT_INVALID, 0, null, null, null).initCause(e);
        }
    }

    static CSVReader openReader(String csvText, char delimiter) {
        return new CSVReaderBuilder(new StringReader(csvText))
                .withCSVParser(new RFC4180ParserBuilder().withSeparator(delimiter).build())
                .withKeepCarriageReturn(false)
                .build();
    }

    static boolean isBlankRecord(List<String> cells) {
        return cells.size() == 1 && cells.get(0).isBlank();
    }

    static Optional<CsvRecord> readHeader(CSVReader reader)
            throws IOException, CsvValidationException {
        for (CsvRecord record = readRecord(reader); record != null; record = readRecord(reader)) {
            if (!isBlankRecord(record.cells())) {
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }

    private static CsvRecord readRecord(CSVReader reader)
            throws IOException, CsvValidationException {
        int startLine = Math.toIntExact(reader.getLinesRead()) + 1;
        String[] cells = reader.readNext();
        if (cells == null) {
            return null;
        }
        return new CsvRecord(
                Math.toIntExact(reader.getRecordsRead()),
                startLine,
                Math.toIntExact(reader.getLinesRead()),
                List.of(cells));
    }
}
