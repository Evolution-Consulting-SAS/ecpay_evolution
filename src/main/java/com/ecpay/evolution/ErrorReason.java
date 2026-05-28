package com.ecpay.evolution;

import org.springframework.http.HttpStatus;

import java.util.Arrays;

public enum ErrorReason {

    CSV_DECODE_ERROR    (ErrorCode.CSV_DECODE_ERROR,     HttpStatus.BAD_REQUEST),
    CSV_FORMAT_INVALID  (ErrorCode.CSV_FORMAT_INVALID,   HttpStatus.UNPROCESSABLE_ENTITY),
    CSV_EMPTY_FILE      (ErrorCode.CSV_EMPTY_FILE,       HttpStatus.UNPROCESSABLE_ENTITY),
    CSV_TYPE_CONVERSION (ErrorCode.CSV_TYPE_CONVERSION,  HttpStatus.UNPROCESSABLE_ENTITY),
    CSV_MAPPING_ERROR   (ErrorCode.CSV_MAPPING_ERROR,    HttpStatus.INTERNAL_SERVER_ERROR),
    CSV_TYPE_MISMATCH   (ErrorCode.CSV_TYPE_MISMATCH,    HttpStatus.UNPROCESSABLE_ENTITY);

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;

    ErrorReason(ErrorCode errorCode, HttpStatus httpStatus) {
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public static ErrorReason fromErrorCode(ErrorCode errorCode) {
        return Arrays.stream(values())
                .filter(r -> r.errorCode == errorCode)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No ErrorReason for: " + errorCode));
    }
}
