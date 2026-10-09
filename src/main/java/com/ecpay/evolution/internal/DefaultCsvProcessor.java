package com.ecpay.evolution.internal;

import com.ecpay.evolution.CsvTable;
import com.ecpay.evolution.internal.parser.CsvParser;
import com.ecpay.evolution.internal.parser.CsvTableReader;

import java.util.List;

public class DefaultCsvProcessor {

    private final Base64CsvDecoder decoder;
    private final CsvParser        parser;
    private final CsvReflectionMapper mapper;
    private final CsvTableReader   tableReader;

    public DefaultCsvProcessor() {
        this.decoder     = new Base64CsvDecoder();
        this.parser      = new CsvParser();
        this.mapper      = new CsvReflectionMapper();
        this.tableReader = new CsvTableReader();
    }

    public <T> List<T> process(String base64Csv, Class<T> targetClass) {
        String csvText = decoder.decode(base64Csv);
        var rows = parser.parse(csvText);
        return mapper.map(rows, targetClass);
    }

    public CsvTable readTable(String base64Csv) {
        return tableReader.read(decoder.decodeStrict(base64Csv));
    }
}
