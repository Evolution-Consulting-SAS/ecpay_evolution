package com.ecpay.evolution;

import com.ecpay.evolution.internal.DefaultCsvProcessor;

import java.util.List;

public final class CsvProcessor {

    private static final DefaultCsvProcessor INSTANCE = new DefaultCsvProcessor();

    private CsvProcessor() {}

    public static <T> List<T> process(String base64Csv, Class<T> targetClass) {
        return INSTANCE.process(base64Csv, targetClass);
    }
}
