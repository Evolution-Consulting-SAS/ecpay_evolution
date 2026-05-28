package com.ecpay.evolution.domain.port.in;

import java.util.List;

public interface CsvProcessorPort {
    <T> List<T> process(String base64Csv, Class<T> targetClass);
}
