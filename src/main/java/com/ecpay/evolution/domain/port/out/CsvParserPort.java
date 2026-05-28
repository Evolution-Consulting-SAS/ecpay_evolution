package com.ecpay.evolution.domain.port.out;

import java.util.List;

public interface CsvParserPort {
    List<List<String>> parse(String csvText);
}
