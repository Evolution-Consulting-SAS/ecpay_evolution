package com.ecpay.evolution.domain.port.out;

public interface CsvDecoderPort {
    String decode(String base64Data);
}
