package com.ecpay.evolution.internal;

import com.ecpay.evolution.CsvProcessingException;
import com.ecpay.evolution.ErrorCode;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

public class Base64CsvDecoder {

    private static final char UTF8_BOM = 0xFEFF;
    private static final char NUL = 0x0000;
    private static final Pattern BASE64_DATA_URL_PREFIX =
            Pattern.compile("data:[^,]*;base64", Pattern.CASE_INSENSITIVE);

    public String decode(String base64Data) {
        validateNotEmpty(base64Data);
        try {
            String payload = extractBase64Payload(base64Data);
            byte[] bytes = decodeBase64ToBytes(payload);
            String text = convertBytesToUtf8String(bytes);
            return removeBomIfPresent(text);
        } catch (IllegalArgumentException e) {
            throw decodeError(e);
        }
    }

    public String decodeStrict(String base64Data) {
        validateNotEmpty(base64Data);
        return removeBomIfPresent(decodeUtf8Strictly(decodeBase64Strictly(base64Data)));
    }

    private byte[] decodeBase64Strictly(String base64Data) {
        try {
            return decodeBase64ToBytes(extractBase64PayloadStrictly(base64Data));
        } catch (IllegalArgumentException e) {
            throw decodeError(e);
        }
    }

    private String extractBase64PayloadStrictly(String base64Data) {
        int comma = base64Data.indexOf(',');
        if (comma < 0) {
            return base64Data;
        }
        if (!BASE64_DATA_URL_PREFIX.matcher(base64Data.substring(0, comma).strip()).matches()) {
            throw new IllegalArgumentException("Prefijo de data URL base64 no válido");
        }
        return base64Data.substring(comma + 1);
    }

    private String decodeUtf8Strictly(byte[] bytes) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw encodingInvalid(e);
        }
        if (text.indexOf(NUL) >= 0) {
            throw encodingInvalid(null);
        }
        return text;
    }

    private void validateNotEmpty(String base64Data) {
        if (base64Data == null || base64Data.isBlank()) {
            throw emptyFile();
        }
    }

    private static CsvProcessingException emptyFile() {
        return new CsvProcessingException(ErrorCode.CSV_EMPTY_FILE, 0, null, null, null);
    }

    private static CsvProcessingException decodeError(Throwable cause) {
        return (CsvProcessingException) new CsvProcessingException(
                ErrorCode.CSV_DECODE_ERROR, 0, null, null, null).initCause(cause);
    }

    private static CsvProcessingException encodingInvalid(Throwable cause) {
        return (CsvProcessingException) new CsvProcessingException(
                ErrorCode.CSV_ENCODING_INVALID, 0, null, null, null).initCause(cause);
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
