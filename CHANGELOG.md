# Changelog

Todas las modificaciones notables de esta librería se documentan aquí.

El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)
y el versionado sigue [Semantic Versioning](https://semver.org/lang/es/).

## [1.0.0] - 2026-10-09

Primera versión pública.

### Added
- `CsvProcessor.process(base64, Class<T>)`: parsea un CSV en Base64 y mapea cada
  fila a un objeto tipado (camino tolerante, por posición).
- `CsvProcessor.readTable(base64)`: lector estricto RFC-4180 que devuelve un
  `CsvTable` sin pérdida, con posición física por registro (`CsvRecord`).
- Decodificación Base64 con validación estricta de UTF-8 (`CSV_ENCODING_INVALID`)
  y detección de delimitador ambiguo (`CSV_DELIMITER_AMBIGUOUS`).
- `ErrorReason` para mapear cada `ErrorCode` a un `HttpStatus` (requiere
  `spring-web`, declarada como *optional*).

### Fixed
- `process(...)` ahora lanza `CsvProcessingException` tipada ante Base64 inválido
  o vacío (antes escapaba un `IllegalArgumentException` sin tipar), unificando el
  contrato de errores con `readTable(...)`.

### Known limitations
- `process(...)` mapea por **posición** (orden de declaración de los campos), no
  por nombre de columna.
- Sin soporte para `BigDecimal`, `LocalDate`/`LocalDateTime` ni `enum` en el
  mapeo; sin soporte para `record` como destino.
- Procesamiento en memoria (sin streaming).

[1.0.0]: https://github.com/Evolution-Consulting-SAS/ecpay_evolution/releases/tag/v1.0.0
