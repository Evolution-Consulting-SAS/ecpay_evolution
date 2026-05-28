package com.ecpay.evolution;

public enum ErrorCode {

    CSV_DECODE_ERROR("Error al decodificar el base64 del CSV"),
    CSV_FORMAT_INVALID("El contenido decodificado no es un CSV válido"),
    CSV_EMPTY_FILE("El archivo CSV está vacío"),
    CSV_TYPE_CONVERSION("Error al convertir el valor al tipo esperado"),
    CSV_MAPPING_ERROR("Error al mapear la fila al objeto destino"),
    CSV_TYPE_MISMATCH("El valor no es compatible con el tipo del campo (ej. entero en campo Double)");

    private final String description;

    ErrorCode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
