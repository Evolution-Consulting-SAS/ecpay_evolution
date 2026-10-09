package com.ecpay.evolution;

import java.util.List;

public record CsvRecord(int recordNumber, int startLine, int endLine, List<String> cells) {

    public CsvRecord {
        cells = List.copyOf(cells);
    }
}
