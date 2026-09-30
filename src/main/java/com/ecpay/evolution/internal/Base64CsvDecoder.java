package com.ecpay.evolution.internal;

import com.ecpay.evolution.CsvProcessingException;
import com.ecpay.evolution.ErrorCode;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
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

    public String decodeStrict(String base64Data) {
        if (base64Data == null || base64Data.isBlank()) {
            throw new CsvProcessingException(ErrorCode.CSV_EMPTY_FILE, 0, null, null, null);
        }
        return removeBomIfPresent(decodeUtf8Strictly(decodeBase64Strictly(base64Data)));
    }

    private byte[] decodeBase64Strictly(String base64Data) {
        try {
            return decodeBase64ToBytes(extractBase64Payload(base64Data));
        } catch (IllegalArgumentException e) {
            throw (CsvProcessingException) new CsvProcessingException(
                    ErrorCode.CSV_DECODE_ERROR, 0, null, null, null).initCause(e);
        }
    }

    private String decodeUtf8Strictly(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw (CsvProcessingException) new CsvProcessingException(
                    ErrorCode.CSV_ENCODING_INVALID, 0, null, null, null).initCause(e);
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
