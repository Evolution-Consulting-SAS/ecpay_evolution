package com.ecpay.evolution.internal;

import com.ecpay.evolution.internal.parser.CsvParser;

import java.util.List;

public class DefaultCsvProcessor {

    private final Base64CsvDecoder decoder;
    private final CsvParser        parser;
    private final CsvReflectionMapper mapper;

    public DefaultCsvProcessor() {
        this.decoder = new Base64CsvDecoder();
        this.parser  = new CsvParser();
        this.mapper  = new CsvReflectionMapper();
    }

    public <T> List<T> process(String base64Csv, Class<T> targetClass) {
        String csvText = decoder.decode(base64Csv);
        var rows = parser.parse(csvText);
        return mapper.map(rows, targetClass);
    }
}
