package com.ecpay.evolution.application;

import com.ecpay.evolution.domain.port.in.CsvProcessorPort;
import com.ecpay.evolution.domain.port.out.CsvDecoderPort;
import com.ecpay.evolution.domain.port.out.CsvMapperPort;
import com.ecpay.evolution.domain.port.out.CsvParserPort;

import java.util.List;
import java.util.Objects;

public class CsvProcessorService implements CsvProcessorPort {

    private final CsvDecoderPort decoder;
    private final CsvParserPort  parser;
    private final CsvMapperPort  mapper;

    public CsvProcessorService(CsvDecoderPort decoder, CsvParserPort parser, CsvMapperPort mapper) {
        this.decoder = Objects.requireNonNull(decoder, "decoder");
        this.parser  = Objects.requireNonNull(parser,  "parser");
        this.mapper  = Objects.requireNonNull(mapper,  "mapper");
    }

    @Override
    public <T> List<T> process(String base64Csv, Class<T> targetClass) {
        String csvText = decoder.decode(base64Csv);
        var rows = parser.parse(csvText);
        return mapper.map(rows, targetClass);
    }
}
