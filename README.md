# ecpay-evolution

Librería Java para procesar archivos CSV codificados en Base64. Ofrece **dos caminos**
con contratos distintos:

- **`process(...)`** — camino *tolerante*: decodifica, parsea y mapea cada fila a un
  objeto tipado (POJO). Pensado para datos "del mundo real" (Excel, comillas simples,
  BOM, etc.).
- **`readTable(...)`** — camino *estricto* (RFC-4180): devuelve la tabla cruda sin
  pérdida (`CsvTable`), conservando celdas y posición física de cada registro.

## Requisitos

- Java 17+
- Maven

## Instalación

> Publicada vía JitPack (ver sección *Publicación*). Reemplaza `VERSION` por el tag
> (ej. `v1.0.0`).

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.Evolution-Consulting-SAS</groupId>
    <artifactId>ecpay_evolution</artifactId>
    <version>VERSION</version>
</dependency>
```

## Uso

### `process` — CSV a objetos tipados

```java
List<MiClase> filas = CsvProcessor.process(base64Csv, MiClase.class);
```

Mapeo y comportamiento:

- **El mapeo es por POSICIÓN**, no por nombre: la primera columna del CSV se asigna al
  primer campo declarado del POJO, la segunda al segundo, y así sucesivamente. Los
  nombres del encabezado **no** se usan para emparejar.
- Detecta automáticamente el delimitador (`;`, `,`, tab o `|`) a partir de la primera
  línea.
- Detecta y salta el encabezado si la primera fila no parece contener datos; si no hay
  encabezado, la primera fila ya se mapea como datos.
- Tolerante con datos de Excel: elimina el BOM UTF-8, quita comillas dobles, simples y
  tipográficas (`« » “ ” ‘ ’`), e ignora líneas en blanco.
- Columnas de más se ignoran; columnas de menos dejan el campo en `null` (o en su valor
  por defecto si es primitivo).

Tipos soportados en los campos del POJO:

| Tipo | Notas |
|------|-------|
| `String` | — |
| `int` / `Integer` | rechaza valores con punto decimal |
| `long` / `Long` | si el valor es una fecha ISO (`yyyy-MM-dd`, `dd/MM/yyyy`, etc.) se convierte a epoch millis |
| `double` / `Double` | normaliza separador decimal/miles (`1.000,50` y `1,000.50`) |
| `boolean` / `Boolean` | `true/si/sí/s/yes/y/1` → `true`; `false/no/n/0` → `false` |

### `readTable` — CSV crudo sin pérdida

```java
CsvTable tabla = CsvProcessor.readTable(base64Csv);

char delimitador      = tabla.delimiter();
CsvRecord encabezado  = tabla.header();
List<CsvRecord> filas = tabla.records();

for (CsvRecord fila : filas) {
    int numero    = fila.recordNumber();   // 1, 2, 3...
    int lineaIni  = fila.startLine();       // línea física inicial
    int lineaFin  = fila.endLine();         // distinta si el registro ocupa varias líneas
    List<String> celdas = fila.cells();     // valores crudos (String)
}
```

A diferencia de `process`, `readTable`:

- Usa un parser RFC-4180 real: soporta **campos entrecomillados con saltos de línea**.
- Es **estricto**: rechaza base64/UTF-8 inválido, bytes nulos, comillas mal formadas y
  delimitadores ambiguos (ver tabla de errores).
- Es **sin pérdida**: conserva registros en blanco intermedios y la posición física de
  cada fila.

## Errores

Ambos métodos lanzan `CsvProcessingException` (es `RuntimeException`). Expone
`getErrorCode()`, `getRow()`, `getColumn()`, `getRawValue()` y `getExpectedType()`.

| `ErrorCode` | Significado | HTTP sugerido |
|-------------|-------------|---------------|
| `CSV_DECODE_ERROR` | Base64 inválido | 400 |
| `CSV_FORMAT_INVALID` | CSV mal formado | 422 |
| `CSV_EMPTY_FILE` | Archivo vacío | 422 |
| `CSV_TYPE_CONVERSION` | No se pudo convertir al tipo esperado | 422 |
| `CSV_MAPPING_ERROR` | Fallo al instanciar/mapear el POJO | 500 |
| `CSV_TYPE_MISMATCH` | Valor incompatible con el tipo del campo | 422 |
| `CSV_ENCODING_INVALID` | El contenido no es UTF-8 válido | 422 |
| `CSV_DELIMITER_AMBIGUOUS` | No se pudo determinar el delimitador | 422 |

El mapeo `ErrorCode` → `HttpStatus` está disponible en `ErrorReason.fromErrorCode(code)`.

> `ErrorReason` depende de `spring-web` (declarada como *optional*). Úsalo solo si tu
> proyecto ya tiene Spring en el classpath.

## Limitaciones conocidas (v1.0.0)

- `process` mapea por **posición**; depende del orden de declaración de los campos del
  POJO. Si reordenas los campos, cambias el mapeo. El mapeo por nombre de columna
  (`@CsvColumn`) está planeado para v1.1.
- No hay soporte para `BigDecimal`, `LocalDate`/`LocalDateTime` ni `enum` en `process`
  (planeado para v1.1).
- El POJO destino debe tener un constructor sin argumentos; no se soportan `record`.
- El archivo se procesa completo en memoria (sin streaming).

## Licencia

Apache License 2.0 — ver [LICENSE](LICENSE).
