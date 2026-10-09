package com.ecpay.evolution;

import java.util.List;
import java.util.Objects;

public record CsvTable(char delimiter, CsvRecord header, List<CsvRecord> records) {

    public CsvTable {
        Objects.requireNonNull(header, "header must not be null");
        records = List.copyOf(records);
    }
}
