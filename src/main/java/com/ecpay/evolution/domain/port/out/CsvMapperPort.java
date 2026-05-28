package com.ecpay.evolution.domain.port.out;

import java.util.List;

public interface CsvMapperPort {
    <T> List<T> map(List<List<String>> rows, Class<T> clazz);
}
