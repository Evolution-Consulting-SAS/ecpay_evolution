package com.ecpay.evolution.domain;

public class CsvProcessingException extends RuntimeException {

    private final ErrorCode errorCode;
    private final int row;
    private final String column;
    private final String rawValue;
    private final String expectedType;

    public CsvProcessingException(ErrorCode errorCode, int row, String column, String rawValue, String expectedType) {
        super(buildMessage(errorCode, row, column, rawValue, expectedType));
        this.errorCode = errorCode;
        this.row = row;
        this.column = column;
        this.rawValue = rawValue;
        this.expectedType = expectedType;
    }

    private static String buildMessage(ErrorCode errorCode, int row, String column, String rawValue, String expectedType) {
        StringBuilder sb = new StringBuilder();
        sb.append(errorCode.getDescription());
        if (row > 0) sb.append(" — fila ").append(row);
        if (column != null) sb.append(", columna '").append(column).append("'");
        if (rawValue != null) sb.append(", valor '").append(rawValue).append("'");
        if (expectedType != null) sb.append(", tipo esperado: ").append(expectedType);
        return sb.toString();
    }

    public ErrorCode getErrorCode() { return errorCode; }
    public int getRow() { return row; }
    public String getColumn() { return column; }
    public String getRawValue() { return rawValue; }
    public String getExpectedType() { return expectedType; }
}
