package com.ecpay.evolution.internal;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Base64CsvDecoder {

    private static final char UTF8_BOM = 0xFEFF;

    public String decode(String base64Data) {
        validateNotEmpty(base64Data);
        try {
            String payload = extractBase64Payload(base64Data);
            byte[] bytes = decodeBase64ToBytes(payload);
            String text = convertBytesToUtf8String(bytes);
            return removeBomIfPresent(text);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Base64 no válido: " + e.getMessage(), e);
        }
    }

    private void validateNotEmpty(String base64Data) {
        if (base64Data == null || base64Data.isBlank()) {
            throw new IllegalArgumentException("El dato Base64 está vacío");
        }
    }

    private String extractBase64Payload(String base64Data) {
        return base64Data.contains(",") ? base64Data.split(",", 2)[1] : base64Data;
    }

    private byte[] decodeBase64ToBytes(String payload) {
        return Base64.getDecoder().decode(payload.trim());
    }

    private String convertBytesToUtf8String(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String removeBomIfPresent(String text) {
        return !text.isEmpty() && text.charAt(0) == UTF8_BOM ? text.substring(1) : text;
    }
}
