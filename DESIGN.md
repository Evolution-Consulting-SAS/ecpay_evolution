# ecpay-evolution — Diseño de la librería

## ¿Qué problema resuelve?

Los microservicios reciben archivos CSV codificados en Base64. Sin esta librería,
cada MS tendría que escribir su propio código para decodificar, parsear y convertir
tipos — código duplicado que fallaría de formas distintas en cada servicio.

Esta librería centraliza todo ese proceso. El MS solo necesita:

```java
CsvProcessorPort processor = new CsvProcessorService(
    new Base64CsvDecoder(),
    new CsvParser(),
    new CsvReflectionMapper()
);

List<HistoricoVenta> lista = processor.process(base64, HistoricoVenta.class);
```

---

## Arquitectura: Hexagonal (Ports & Adapters)

```
┌─────────────────────────────────────────────────────────┐
│                        domain/                          │
│                                                         │
│   ErrorCode   CsvProcessingException                    │
│                                                         │
│   port/in/  CsvProcessorPort  ← contrato de entrada     │
│   port/out/ CsvDecoderPort    ← lo que el dominio       │
│             CsvParserPort       necesita del exterior   │
│             CsvMapperPort                               │
└─────────────────┬───────────────────────────────────────┘
                  │ implementa
┌─────────────────▼───────────────────────────────────────┐
│                     application/                        │
│                  CsvProcessorService                    │
│   (orquesta los puertos de salida, no conoce            │
│    ninguna implementación concreta)                     │
└─────────────────┬───────────────────────────────────────┘
                  │ inyectados por constructor
┌─────────────────▼───────────────────────────────────────┐
│                      adapter/                           │
│                                                         │
│  Base64CsvDecoder      implements CsvDecoderPort        │
│  CsvReflectionMapper   implements CsvMapperPort         │
│  ErrorReason           (utilidad para el MS)            │
│  parser/  CsvParser    implements CsvParserPort         │
│           DelimiterDetector (helper interno)            │
│           HeaderDetector    (helper interno)            │
└─────────────────────────────────────────────────────────┘
```

**Regla clave:** las dependencias solo fluyen hacia adentro.
`adapter` conoce a `domain`. `domain` no conoce a nadie.
Si mañana se reemplaza OpenCSV por otra librería, solo se toca `adapter/parser/` —
el dominio y la capa de aplicación no se tocan.

---

## Decisiones de diseño clave

### ¿Por qué arquitectura hexagonal en una librería?

Las librerías normalmente no usan hexagonal — es un patrón de microservicios.
Aquí se justifica por una razón concreta: el contrato `CsvProcessorPort` permite
al MS consumidor **mockear todo el procesamiento CSV en sus tests** con una sola línea:

```java
CsvProcessorPort processor = mock(CsvProcessorPort.class);
```

Sin el puerto de entrada, el MS tendría que instanciar `CsvProcessorService` con sus
tres dependencias reales (`Base64CsvDecoder`, `CsvParser`, `CsvReflectionMapper`) en
cada test — o tendría que usar un mock de la clase concreta, lo cual acopla los tests
a los detalles de implementación de la librería.

El puerto también fija un contrato estable: si la implementación interna cambia
(por ejemplo, se reemplaza OpenCSV), los tests del MS no se rompen.

### ¿Por qué OpenCSV y no Apache Commons CSV o Jackson CSV?

| Librería | Razón de descarte |
|---|---|
| **Apache Commons CSV** | Funcionalidad equivalente a OpenCSV para este caso de uso. Sin ventaja concreta que justifique el cambio. |
| **Jackson CSV** | Requiere `jackson-dataformat-csv` — dependencia más pesada e innecesaria si el MS ya tiene Jackson para JSON. Agregar un módulo Jackson solo para CSV introduce riesgo de conflictos de versión. |
| **OpenCSV** | Ya estaba en el stack del proyecto, maneja correctamente campos entre comillas con delimitadores internos (`"valor, con coma"`), y su `CSVParserBuilder` permite configurar el delimitador en runtime — esencial para la auto-detección. |

La decisión está completamente aislada en `adapter/parser/` — si en el futuro hay
una razón para cambiarla, solo se tocan esos tres archivos.

### ¿Por qué mapeo posicional por reflexión en vez de `@CsvBindByName` de OpenCSV?

OpenCSV tiene su propio sistema de mapeo con anotaciones (`@CsvBindByName`, `@CsvBindByPosition`).
No se usa porque **acoplaría los POJOs del MS a OpenCSV**:

```java
// Lo que el MS tendría que hacer con @CsvBindByName:
public class HistoricoVenta {
    @CsvBindByName(column = "companyId")   // ← dependencia de opencsv en el dominio del MS
    private String companyId;
}
```

Esto viola un principio clave: la librería no debe invadir el dominio del MS.
El MS no debería saber que existe OpenCSV — eso es un detalle de implementación de
`ecpay-evolution`.

Con reflexión pura, el POJO del MS es Java estándar sin ninguna anotación externa.
El trade-off explícito: **el orden de declaración de campos en el POJO debe coincidir
con el orden de columnas en el CSV**. Es una restricción conocida, no un bug.

### ¿Por qué `RuntimeException` y no checked exception?

Las checked exceptions obligan a cada método intermedio a declarar `throws CsvProcessingException`
en su firma hasta llegar al handler global — ruido puro en servicios que no pueden hacer
nada útil con el error excepto relanzarlo:

```java
// Con checked exception — contaminación en cadena:
public ResponseEntity<?> upload(String base64) throws CsvProcessingException { ... }
public List<Venta> importar(String base64) throws CsvProcessingException { ... }
public List<Venta> parsear(String base64) throws CsvProcessingException { ... }
```

El MS ya tiene un `@ExceptionHandler(CsvProcessingException.class)` global.
`RuntimeException` llega ahí directamente sin contaminar las firmas de ningún servicio.

---

## Estructura de archivos

```
src/main/java/com/ecpay/evolution/
├── domain/
│   ├── ErrorCode.java               — los 6 tipos de error posibles
│   ├── CsvProcessingException.java  — excepción con contexto completo
│   └── port/
│       ├── in/
│       │   └── CsvProcessorPort.java    — contrato de entrada (caso de uso)
│       └── out/
│           ├── CsvDecoderPort.java      — contrato: decodificar Base64
│           ├── CsvParserPort.java       — contrato: parsear CSV a filas
│           └── CsvMapperPort.java       — contrato: mapear filas a objetos
├── application/
│   └── CsvProcessorService.java        — orquesta los 3 pasos del proceso
└── adapter/
    ├── Base64CsvDecoder.java        — paso 1: Base64 → texto CSV
    ├── CsvReflectionMapper.java     — paso 3: filas → instancias del POJO
    ├── ErrorReason.java             — mapea ErrorCode → HttpStatus (para el MS)
    └── parser/
        ├── CsvParser.java               — paso 2: texto CSV → lista de filas
        ├── DelimiterDetector.java       — detecta ; , \t | automáticamente
        └── HeaderDetector.java          — detecta si la primera línea es header

src/test/java/com/ecpay/evolution/
├── CsvProcessorIntegrationTest.java     — 11 tests de integración end-to-end
└── fixture/
    ├── HistoricalSalaryRow.java         — POJO de prueba (String, Integer, Long, Double)
    ├── HistoricalContributorRow.java    — POJO de prueba (Boolean, Double)
    └── HistoricalOrgRow.java            — POJO de prueba (delimitador ;, sin header)
```

---

## Por qué existe cada carpeta

### `domain/`
Contiene todo lo que pertenece al negocio puro: los errores que pueden ocurrir,
la excepción que lanza la librería y los contratos que definen qué hace. No importa
ninguna librería externa — solo Java estándar. Es la capa más interna de la
arquitectura hexagonal.

### `domain/port/`
Los puertos son las interfaces que definen los contratos de la arquitectura hexagonal.
Se dividen en dos subgrupos:
- `in/` — puertos de **entrada**: cómo el mundo exterior llama a la librería.
- `out/` — puertos de **salida**: qué necesita la librería del exterior para funcionar.

### `application/`
La capa de aplicación contiene el servicio que orquesta el caso de uso completo.
No sabe nada de Base64, de OpenCSV, ni de reflexión — solo habla con los puertos.
Es el punto donde los tres pasos (decodificar, parsear, mapear) se unen.

### `adapter/`
Las implementaciones concretas de los puertos de salida. `Base64CsvDecoder`,
`CsvReflectionMapper` y `ErrorReason` viven directamente aquí — un archivo por
clase no justifica subcarpeta propia. La única subcarpeta es `parser/` porque
agrupa tres clases cohesivas: el parser y sus dos helpers de detección.

---

## Archivos en detalle

---

### `domain/ErrorCode.java`

Define los 6 tipos de error que puede lanzar la librería.

```java
public enum ErrorCode {
```
`enum` y no clase con constantes porque Java garantiza unicidad de cada valor en
toda la JVM. Nadie puede crear un `ErrorCode` nuevo en runtime ni pisar uno existente.

```java
    CSV_DECODE_ERROR("Error al decodificar el base64 del CSV"),
```
El string Base64 que llegó al método está vacío o mal formado.
Es el primer paso, así que si falla aquí ni se llega a ver el CSV.

```java
    CSV_FORMAT_INVALID("El contenido decodificado no es un CSV válido"),
```
El Base64 se decodificó sin problema, pero el contenido no es un CSV válido
(línea malformada, comillas sin cerrar, etc.).

```java
    CSV_EMPTY_FILE("El archivo CSV está vacío"),
```
El CSV existe y se decodificó, pero no tiene ninguna línea con contenido.
Distinto a `CSV_FORMAT_INVALID`: el archivo sí llegó, simplemente está vacío.

```java
    CSV_TYPE_CONVERSION("Error al convertir el valor al tipo esperado"),
```
El valor del CSV no se puede convertir al tipo del campo del POJO.
Ejemplo: el campo es `Integer` y el CSV trae `"abc"`.

```java
    CSV_MAPPING_ERROR("Error al mapear la fila al objeto destino"),
```
Error al crear la instancia del POJO por reflexión. Normalmente significa que
la clase no tiene constructor sin argumentos. Es un error interno, no del CSV.

```java
    CSV_TYPE_MISMATCH("El valor no es compatible con el tipo del campo (ej. entero en campo Double)");
```
El valor se puede parsear numéricamente, pero el formato no corresponde al tipo.
Ejemplos: `"3.5"` en un campo `Integer` (tiene decimal) o `"42"` en un campo
`Double` (no tiene decimal). Es diferente a `CSV_TYPE_CONVERSION`: acá el número
es válido, pero el tipo no coincide con lo que se declaró en el POJO.

```java
    private final String description;

    ErrorCode(String description) { this.description = description; }

    public String getDescription() { return description; }
}
```
`final` garantiza que la descripción no cambia después de crearse.
`getDescription()` lo usa `CsvProcessingException` para construir el mensaje de error.

---

### `domain/CsvProcessingException.java`

Excepción que lleva el contexto completo de dónde y por qué falló el procesamiento.

```java
public class CsvProcessingException extends RuntimeException {
```
`RuntimeException` (unchecked) para que el MS pueda capturarla solo donde le
convenga (su handler global) sin tener que declararla en cada método con `throws`.

```java
    private final ErrorCode errorCode;
    private final int row;
    private final String column;
    private final String rawValue;
    private final String expectedType;
```
Los cinco datos que identifican exactamente dónde falló:
- `errorCode` — qué tipo de error fue.
- `row` — en qué fila ocurrió (1-based). `0` significa error antes de los datos.
- `column` — nombre del campo del POJO que causó el problema.
- `rawValue` — valor exacto que estaba en el CSV y no se pudo procesar.
- `expectedType` — tipo Java al que se intentaba convertir.

Todos `final` porque el contexto de un error no debería mutar después de crearse.

```java
    public CsvProcessingException(ErrorCode errorCode, int row, String column,
                                  String rawValue, String expectedType) {
        super(buildMessage(errorCode, row, column, rawValue, expectedType));
```
`super(...)` tiene que ser la primera instrucción del constructor en Java.
Por eso `buildMessage` es `static` — para poder llamarse antes de que el objeto exista.

```java
    private static String buildMessage(ErrorCode errorCode, int row, String column,
                                       String rawValue, String expectedType) {
        StringBuilder sb = new StringBuilder();
        sb.append(errorCode.getDescription());
        if (row > 0) sb.append(" — fila ").append(row);
        if (column != null) sb.append(", columna '").append(column).append("'");
        if (rawValue != null) sb.append(", valor '").append(rawValue).append("'");
        if (expectedType != null) sb.append(", tipo esperado: ").append(expectedType);
        return sb.toString();
    }
```
`StringBuilder` en lugar de concatenar con `+` porque cada `+` crea un objeto
`String` nuevo en heap. Con `StringBuilder` se escribe en el mismo buffer.

Cada campo se agrega solo si tiene valor. Errores como `CSV_EMPTY_FILE` no tienen
columna ni valor, así que el mensaje queda limpio: `"El archivo CSV está vacío"`.

```java
    public ErrorCode getErrorCode() { return errorCode; }
    public int getRow()             { return row; }
    public String getColumn()       { return column; }
    public String getRawValue()     { return rawValue; }
    public String getExpectedType() { return expectedType; }
```
Getters para que el MS pueda leer cada campo y construir su propia respuesta
estructurada (por ejemplo, un JSON con campo `row` y campo `column`).

---

### `domain/port/in/CsvProcessorPort.java`

Puerto de entrada: el contrato que el MS importa para invocar la librería.

```java
public interface CsvProcessorPort {
    <T> List<T> process(String base64Csv, Class<T> targetClass);
}
```
`<T>` es el tipo genérico. El MS pasa `MiClase.class` y recibe `List<MiClase>`
sin ningún casteo. Java infiere el tipo en tiempo de compilación.

El MS nunca instancia `CsvProcessorService` directamente en producción; trabaja
con esta interfaz. Eso le permite en tests reemplazar la implementación por un mock.

---

### `domain/port/out/CsvDecoderPort.java`

Puerto de salida: lo que el dominio necesita para decodificar Base64.

```java
public interface CsvDecoderPort {
    String decode(String base64Data);
}
```
Al definir esto como interfaz en el dominio, la implementación concreta
(`Base64CsvDecoder`) puede reemplazarse sin tocar nada del dominio ni de la
capa de aplicación. Si en el futuro se necesita un decoder que soporte URL-safe
Base64, solo se crea una clase nueva que implemente este contrato.

---

### `domain/port/out/CsvParserPort.java`

Puerto de salida: lo que el dominio necesita para parsear texto CSV.

```java
public interface CsvParserPort {
    List<List<String>> parse(String csvText);
}
```
`List<List<String>>`: la lista exterior representa las filas, la lista interior
representa los valores de cada columna en esa fila (en orden de posición).
Este tipo es Java puro — no depende de OpenCSV ni de ninguna librería externa.

---

### `domain/port/out/CsvMapperPort.java`

Puerto de salida: lo que el dominio necesita para convertir filas a objetos.

```java
public interface CsvMapperPort {
    <T> List<T> map(List<List<String>> rows, Class<T> clazz);
}
```
Recibe las filas que produjo el parser y las convierte en instancias de `clazz`.
El genérico `<T>` garantiza tipado en tiempo de compilación sin casteos.

---

### `application/CsvProcessorService.java`

Implementa el caso de uso completo: recibe Base64, devuelve una lista de objetos.
No sabe cómo funciona ninguno de los tres pasos — solo los orquesta en orden.

```java
public class CsvProcessorService implements CsvProcessorPort {
```
Implementa el puerto de entrada. Es la única clase de la capa de aplicación.

```java
    private final CsvDecoderPort decoder;
    private final CsvParserPort  parser;
    private final CsvMapperPort  mapper;
```
Los tres colaboradores son interfaces, no clases concretas. Esto es lo que hace
posible la arquitectura hexagonal: `CsvProcessorService` no importa ni conoce
`Base64CsvDecoder`, `CsvParser` ni `CsvReflectionMapper`.

```java
    public CsvProcessorService(CsvDecoderPort decoder, CsvParserPort parser, CsvMapperPort mapper) {
        this.decoder = Objects.requireNonNull(decoder, "decoder");
        this.parser  = Objects.requireNonNull(parser,  "parser");
        this.mapper  = Objects.requireNonNull(mapper,  "mapper");
    }
```
Inyección por constructor: el que crea el servicio decide qué implementaciones usar.
`Objects.requireNonNull` falla inmediatamente con un mensaje claro si alguien pasa
`null` — mejor fallar en construcción que obtener un `NullPointerException` en el
primer procesamiento.

```java
    @Override
    public <T> List<T> process(String base64Csv, Class<T> targetClass) {
        String csvText = decoder.decode(base64Csv);
        var rows = parser.parse(csvText);
        return mapper.map(rows, targetClass);
    }
```
Los tres pasos en orden. `var` infiere `List<List<String>>` — Java 10+.
Si cualquier paso lanza `CsvProcessingException`, sube directamente al MS
sin que este método la intercepte.

---

### `adapter/Base64CsvDecoder.java`

Implementa `CsvDecoderPort`. Convierte un string Base64 en texto CSV listo para parsear.

```java
public class Base64CsvDecoder implements CsvDecoderPort {

    private static final char UTF8_BOM = 0xFEFF;
```
`0xFEFF` es el BOM (Byte Order Mark) que Excel agrega al inicio de los CSV que
exporta en UTF-8. Es invisible en cualquier editor de texto. Se guarda en una
constante con nombre porque pegar el carácter invisible en el código es peligroso:
cualquier editor puede borrarlo sin aviso y el método dejaría de funcionar.

```java
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
```
El método público solo orquesta — cada paso tiene su propio método privado.
El `try` envuelve solo los pasos que pueden lanzar `IllegalArgumentException`
desde el decodificador de Java. `validateNotEmpty` tiene su propia excepción
antes del `try` porque falla por un motivo diferente (input vacío, no Base64 inválido).
El segundo parámetro `e` en el `throw` preserva el stack trace original.

```java
    private void validateNotEmpty(String base64Data) {
        if (base64Data == null || base64Data.isBlank()) {
            throw new IllegalArgumentException("El dato Base64 está vacío");
        }
    }
```
`null` primero porque `isBlank()` lanzaría `NullPointerException` si el string
fuera null. `isBlank()` cubre strings como `"   "` que no son null pero tampoco
son Base64 válido.

```java
    private String extractBase64Payload(String base64Data) {
        return base64Data.contains(",") ? base64Data.split(",", 2)[1] : base64Data;
    }
```
Algunos sistemas envían Base64 con prefijo data-URI: `"data:text/csv;base64,SGVs..."`.
El payload real siempre empieza después de la primera coma. El `2` en `split(",", 2)`
limita la división a 2 partes — sin ese límite, si el payload tuviera una coma
interna se cortaría incorrectamente.

```java
    private byte[] decodeBase64ToBytes(String payload) {
        return Base64.getDecoder().decode(payload.trim());
    }
```
`Base64.getDecoder()` usa el estándar RFC 4648. `.trim()` elimina espacios o saltos
de línea que algunos sistemas agregan al final del string Base64 — sin el trim esos
caracteres extra harían fallar la decodificación.

```java
    private String convertBytesToUtf8String(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
```
Se especifica `UTF_8` explícitamente para evitar que Java use el charset del sistema
operativo donde corre el servidor. Sin esto, el mismo código produce resultados
distintos en un servidor en español y uno en inglés (las tildes y la ñ aparecen
como caracteres extraños). Con `UTF_8` fijo, el comportamiento es idéntico en
desarrollo, QA y producción.

```java
    private String removeBomIfPresent(String text) {
        return !text.isEmpty() && text.charAt(0) == UTF8_BOM ? text.substring(1) : text;
    }
```
El BOM viaja dentro del Base64 (se codificó junto con el CSV). Al decodificar,
aparece como primer carácter del string. Sin este método, el header del CSV quedaría
como `"[invisible]companyId"` y el mapeo fallaría silenciosamente porque el nombre
de la columna no coincidiría con el campo del POJO — un bug muy difícil de diagnosticar.
Solo se revisa `charAt(0)` porque el BOM siempre va al inicio, nunca en el medio.

---

### `adapter/parser/CsvParser.java`

Implementa `CsvParserPort`. Convierte texto CSV en una lista de filas donde cada
fila es una lista de strings (los valores de cada columna, en orden de posición).

Usa OpenCSV para manejar casos complejos como campos entre comillas:
`"valor, con coma"` debe ser un solo campo aunque tenga coma adentro.

```java
    public List<List<String>> parse(String csvText) {
        String[] lines = splitIntoLines(csvText);
        int firstLineIndex = findFirstNonEmptyLineIndex(lines);

        if (firstLineIndex == -1) {
            throw new CsvProcessingException(ErrorCode.CSV_EMPTY_FILE, 0, null, null, null);
        }
```
Si no hay ninguna línea con contenido, se lanza la excepción antes de intentar
cualquier procesamiento. `row = 0` porque el error es del archivo completo,
no de una fila específica.

```java
        String firstLine = lines[firstLineIndex];
        CSVParser openCsvParser = buildParserWithAutoDetectedDelimiter(firstLine);
        int dataStartIndex = isHeader(firstLine, openCsvParser) ? firstLineIndex + 1 : firstLineIndex;
        return parseDataRows(openCsvParser, lines, dataStartIndex);
    }
```
Se detecta el delimitador mirando la primera línea real (la que tiene contenido).
Si esa primera línea es un header, `dataStartIndex` apunta a la siguiente.
Si no hay header (el CSV viene directamente con datos), `dataStartIndex` apunta
a la misma primera línea.

```java
    private String[] splitIntoLines(String csvText) {
        return csvText.split("\\R", -1);
    }
```
`\\R` es la expresión regular de Java para cualquier salto de línea:
`\n` (Unix), `\r\n` (Windows), `\r` (Mac antiguo). Sin esto, un CSV exportado
desde Windows fallaría en un servidor Linux. El `-1` conserva las líneas vacías
al final del arreglo — sin él, Java las eliminaría y se perdería la última fila
si el archivo termina en línea en blanco.

```java
    private int findFirstNonEmptyLineIndex(String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].isBlank()) return i;
        }
        return -1;
    }
```
Devuelve el índice (no el contenido) para poder calcular dónde empiezan los datos
(`i + 1`) sin escanear el arreglo dos veces. Devuelve `-1` y no `0` porque `0`
es un índice válido — si devolviera `0` no habría forma de distinguir entre
"el header está en la línea 0" y "no hay nada".

```java
    private CSVParser buildParserWithAutoDetectedDelimiter(String firstLine) {
        char delimiter = DelimiterDetector.detect(firstLine);
        return new CSVParserBuilder().withSeparator(delimiter).build();
    }
```
OpenCSV no detecta el delimitador automáticamente — hay que indicárselo.
`CSVParserBuilder` es el patrón Builder de OpenCSV: se configura paso a paso
y `.build()` devuelve el parser listo.

```java
    private boolean isHeader(String line, CSVParser parser) {
        try {
            String[] cells = parser.parseLine(line);
            return HeaderDetector.isHeader(cells);
        } catch (IOException e) {
            return false;
        }
    }
```
Si la línea no se puede parsear (malformada), se asume que no es header y se
intenta procesar como dato. El `IOException` de OpenCSV se tragaría acá y aparecería
después como `CSV_FORMAT_INVALID` cuando se intente parsear esa fila como dato.

```java
    private List<List<String>> parseDataRows(CSVParser parser, String[] lines, int dataStartIndex) {
        List<List<String>> rows = new ArrayList<>();
        int dataRowNumber = 0;
        for (int i = dataStartIndex; i < lines.length; i++) {
            if (lines[i].isBlank()) continue;
            dataRowNumber++;
            rows.add(parseSingleDataRow(parser, lines[i], dataRowNumber));
        }
        return rows;
    }
```
`dataRowNumber` es un contador separado de `i`. `i` cuenta todas las líneas
incluyendo las vacías. `dataRowNumber` cuenta solo las filas de datos reales —
ese es el número que tiene significado para el usuario cuando se reporta un error.
Las líneas en blanco dentro del CSV se saltan sin error.

```java
    private List<String> parseSingleDataRow(CSVParser parser, String line, int dataRowNumber) {
        try {
            String[] values = parser.parseLine(line);
            List<String> row = new ArrayList<>(values.length);
            for (String v : values) row.add(v.trim());
            return row;
        } catch (IOException e) {
            throw new CsvProcessingException(ErrorCode.CSV_FORMAT_INVALID, dataRowNumber, null, line, null);
        }
    }
```
`.trim()` en cada valor elimina espacios que algunos sistemas agregan alrededor
de los datos. `new ArrayList<>(values.length)` preasigna la capacidad exacta para
evitar que la lista se redimensione durante el llenado.

---

### `adapter/parser/DelimiterDetector.java`

Helper interno (visibilidad de paquete, sin `public`) que detecta el delimitador
leyendo la primera línea del CSV.

```java
class DelimiterDetector {

    private static final char[] CANDIDATES = {';', ',', '\t', '|'};
```
Los cuatro delimitadores más comunes. `static final` porque son constantes de la
clase, no del objeto. El orden importa: si dos candidatos tienen el mismo número
de apariciones, gana el primero en la lista (`;` sobre `,`).

```java
    static char detect(String sampleLine) {
        return findMostFrequentCandidate(sampleLine);
    }
```
Sin `public` — solo `CsvParser` puede llamar a este método. No es necesario
exponerlo fuera del paquete.

```java
    private static char findMostFrequentCandidate(String line) {
        char best = ',';
        long max = 0;
        for (char candidate : CANDIDATES) {
            long count = countOccurrencesInLine(line, candidate);
            if (count > max) { max = count; best = candidate; }
        }
        return best;
    }
```
`best` arranca con `,` como defecto por si ningún candidato aparece.
`max` arranca en `0` para que cualquier candidato que aparezca al menos
una vez sea elegido. El que más veces aparece en la primera línea es el delimitador.

```java
    private static long countOccurrencesInLine(String line, char character) {
        return line.chars().filter(ch -> ch == character).count();
    }
```
`line.chars()` convierte el String en `IntStream` de valores Unicode.
`.filter` conserva solo los iguales al candidato. `.count()` los cuenta.
Se extrae en su propio método para que `findMostFrequentCandidate` sea legible.

---

### `adapter/parser/HeaderDetector.java`

Helper interno que determina si una línea es header o datos mediante heurística.

```java
class HeaderDetector {

    static boolean isHeader(String[] cells) {
        return Arrays.stream(cells)
                .map(String::trim)
                .noneMatch(HeaderDetector::cellLooksLikeData);
    }
```
Una fila es header si **ninguna** de sus celdas parece un dato real.
La lógica inversa: si al menos una celda parece un dato (número, fecha),
entonces la fila completa se trata como datos, no como header.

```java
    private static boolean cellLooksLikeData(String cell) {
        return isInteger(cell) || isDecimal(cell) || isDate(cell);
    }

    private static boolean isInteger(String cell) {
        return cell.matches("-?\\d+");
    }
```
`-?` acepta negativos. `\\d+` uno o más dígitos. Cubre: `"3"`, `"-42"`, `"1000"`.

```java
    private static boolean isDecimal(String cell) {
        return cell.matches("-?\\d+\\.\\d+([eE]-?\\d+)?");
    }
```
Cubre decimales simples (`"3.14"`) y notación científica (`"1.5e-3"`).

```java
    private static boolean isDate(String cell) {
        return cell.matches("\\d{4}-\\d{2}-\\d{2}.*");
    }
```
Cubre fechas ISO 8601: `"2024-01-15"`, `"2024-12-31T00:00:00"`.
El `.*` al final acepta timestamps con hora sin necesidad de validar el formato completo.

---

### `adapter/CsvReflectionMapper.java`

Implementa `CsvMapperPort`. Convierte las filas del parser en instancias de la
clase del MS usando reflexión Java.

**Reflexión:** Java permite examinar y modificar la estructura de una clase en
tiempo de ejecución. `field.set(instance, value)` asigna un valor a un campo
privado sin necesitar getter o setter. Es lo que hace posible que la librería
funcione con cualquier POJO del MS sin modificarlo.

**Mapeo por posición:** el campo 0 del POJO recibe el valor 0 del CSV, el campo 1
recibe el valor 1, etc. El orden de declaración de campos en el POJO debe coincidir
con el orden de columnas en el CSV.

**Tipos soportados:**

| Tipo Java       | Regla                                                      |
|-----------------|------------------------------------------------------------|
| `String`        | Se asigna directamente, siempre válido.                    |
| `Integer`/`int` | Sin punto decimal. `"42"` ✓ — `"42.0"` ✗                 |
| `Long`/`long`   | Sin punto decimal, o fecha ISO `"yyyy-MM-dd"` → epoch ms. |
| `Double`/`double`| Cualquier número parseable por `Double.parseDouble`.      |
| `Boolean`/`boolean`| `true/false`, `si/no/sí`, `s/n`, `yes/no`, `y/n`, `1/0`.|

```java
    private static final Map<Class<?>, Function<String, Object>> TYPE_CONVERTERS = buildTypeConverters();
```
En lugar de una cadena de `if/else`, se usa un `Map` donde cada clave es un tipo
Java y cada valor es una función que sabe cómo convertir un String a ese tipo.
`static final`: el mapa es el mismo para todas las instancias y no cambia en runtime —
se construye una sola vez cuando la JVM carga la clase.

```java
    private static Map<Class<?>, Function<String, Object>> buildTypeConverters() {
        Map<Class<?>, Function<String, Object>> converters = new LinkedHashMap<>();
        converters.put(String.class,   v -> v);
        converters.put(Integer.class,  CsvReflectionMapper::parseIntegerValue);
        converters.put(int.class,      CsvReflectionMapper::parseIntegerValue);
        // ... etc
        return Collections.unmodifiableMap(converters);
    }
```
Cada tipo tiene dos entradas: el tipo objeto (`Integer.class`) y el primitivo
(`int.class`). Sin ambas entradas, un campo declarado como `private int x`
(devuelve `int.class` por reflexión) no encontraría conversor y lanzaría error.
`Collections.unmodifiableMap` evita que alguien agregue o quite conversores en runtime.
`CsvReflectionMapper::parseIntegerValue` es referencia a método — forma corta de
`v -> CsvReflectionMapper.parseIntegerValue(v)`. Los métodos son `static` para
poder usarse como referencias dentro de un método estático.

```java
    public <T> List<T> map(List<List<String>> rows, Class<T> clazz) {
        Field[] fields = resolveAccessibleFields(clazz);
        List<T> result = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            result.add(mapRowToInstance(rows.get(rowIndex), clazz, fields, rowIndex + 1));
        }
        return result;
    }
```
`resolveAccessibleFields` se llama una sola vez antes del loop porque los campos
de la clase no cambian entre fila y fila. `rowIndex + 1` convierte el índice
0-based del loop en número de fila 1-based (lo que ve el usuario en el CSV).

```java
    private Field[] resolveAccessibleFields(Class<?> clazz) {
        Field[] fields = Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .toArray(Field[]::new);
        Arrays.stream(fields).forEach(f -> f.setAccessible(true));
        return fields;
    }
```
`getDeclaredFields()` devuelve todos los campos incluyendo los privados.
Se excluyen los `static` porque pertenecen a la clase entera, no a cada instancia.
`f.setAccessible(true)` permite escribir en campos `private` — sin esto,
`field.set(instance, value)` lanzaría `IllegalAccessException`.

```java
    private void populateFieldsFromRow(Object instance, Field[] fields,
                                       List<String> values, int rowNumber) {
        for (int i = 0; i < fields.length; i++) {
            String rawValue = i < values.size() ? values.get(i) : null;
            if (rawValue != null) {
                assignFieldValue(instance, fields[i], rawValue, rowNumber);
            }
        }
    }
```
`i < values.size()` maneja filas con menos columnas que campos en el POJO:
si el CSV trae 5 columnas y el POJO tiene 8 campos, los 3 campos restantes
quedan en `null` sin lanzar `ArrayIndexOutOfBoundsException`.
`if (rawValue != null)` omite asignaciones de valores ausentes.

```java
    private <T> T instantiateClass(Class<T> clazz, int rowNumber) {
        try {
            var constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new CsvProcessingException(ErrorCode.CSV_MAPPING_ERROR, rowNumber, null, null, null);
        }
    }
```
`getDeclaredConstructor()` sin argumentos obtiene el constructor vacío.
`setAccessible(true)` es necesario por si el constructor es `private` (Lombok
con `@Builder` genera constructores privados). `catch (Exception e)` y no un tipo
específico porque la reflexión puede lanzar varios tipos distintos de excepción
(`NoSuchMethodException`, `InstantiationException`, `InvocationTargetException`).

```java
    private void assignFieldValue(Object instance, Field field,
                                  String rawValue, int rowNumber) {
        try {
            field.set(instance, convertToFieldType(rawValue, field.getType()));
        } catch (CsvProcessingException e) {
            throw new CsvProcessingException(
                    e.getErrorCode(), rowNumber, field.getName(),
                    e.getRawValue() != null ? e.getRawValue() : rawValue,
                    e.getExpectedType()
            );
        } catch (Exception e) {
            throw new CsvProcessingException(
                    ErrorCode.CSV_TYPE_CONVERSION, rowNumber,
                    field.getName(), rawValue, field.getType().getSimpleName()
            );
        }
    }
```
`convertToFieldType` lanza `CsvProcessingException` con `row = 0` porque en ese
momento no sabe en qué fila está. Acá se captura y se re-lanza con `rowNumber` y
`field.getName()` correctos — ese es el enriquecimiento de contexto.
El segundo `catch (Exception)` atrapa cualquier excepción inesperada de `field.set()`
y siempre la convierte en `CsvProcessingException` con contexto útil.

```java
    private Object convertToFieldType(String rawValue, Class<?> targetType) {
        if (rawValue == null || rawValue.isBlank()) {
            return PRIMITIVE_DEFAULTS.getOrDefault(targetType, null);
        }
        String value = rawValue.trim();
        Function<String, Object> converter = TYPE_CONVERTERS.get(targetType);
        if (converter == null) {
            throw new CsvProcessingException(ErrorCode.CSV_TYPE_CONVERSION, 0, null, value, targetType.getName());
        }
        return converter.apply(value);
    }
```
Guardia temprana: celda vacía → `null` para objetos o el default del primitivo
(`0`, `0L`, `0.0`, `false`). Sin esto, `Integer.parseInt("")` lanzaría excepción.
Si el tipo no está en el mapa, `get()` devuelve `null` → se lanza error de tipo
no soportado.

```java
    private static Object parseLongValue(String value) {
        if (looksLikeIsoDate(value)) return isoDateToEpoch(value);
        rejectIfContainsDecimalPoint(value, "Long");
        return Long.parseLong(value);
    }
```
Los campos `Long` en los POJOs del MS representan fechas como epoch milliseconds.
Si el CSV trae `"2024-01-15"`, se convierte automáticamente a epoch.
Si trae un número, se parsea como `Long` normal rechazando decimales.

```java
    private static Long isoDateToEpoch(String value) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            sdf.setLenient(false);
            return sdf.parse(value).getTime();
        } catch (ParseException e) {
            throw new CsvProcessingException(ErrorCode.CSV_TYPE_CONVERSION, 0, null, value, "Long");
        }
    }
```
`setLenient(false)` rechaza fechas inválidas como `"2024-13-45"` que Java por
defecto aceptaría desbordando al mes siguiente. Con `false`, `"2024-13-45"`
lanza `ParseException` y se convierte en `CSV_TYPE_CONVERSION`.

```java
    private static Object parseBooleanValue(String value) {
        return switch (value.toLowerCase()) {
            case "true", "si", "sí", "s", "yes", "y", "1" -> true;
            case "false", "no", "n", "0"                   -> false;
            default -> throw new CsvProcessingException(
                    ErrorCode.CSV_TYPE_MISMATCH, 0, null, value, "Boolean");
        };
    }
```
`Boolean.parseBoolean()` de Java solo acepta `"true"` y trata todo lo demás como
`false` — eso haría pasar silenciosamente un `"si"` como `false`, un bug muy
difícil de detectar. Con este switch, cualquier valor no reconocido lanza error
en lugar de asumir `false`.

---

### `adapter/ErrorReason.java`

Utilidad para el MS consumidor. Mapea cada `ErrorCode` al `HttpStatus` correcto
para que el MS no tenga que escribir esa lógica en su propio handler.

**Uso típico en el MS:**
```java
@ExceptionHandler(CsvProcessingException.class)
public ResponseEntity<ErrorResponse> handleCsvError(CsvProcessingException e) {
    ErrorReason reason = ErrorReason.fromErrorCode(e.getErrorCode());
    return ResponseEntity.status(reason.getHttpStatus()).body(...);
}
```

```java
    CSV_DECODE_ERROR    (ErrorCode.CSV_DECODE_ERROR,     HttpStatus.BAD_REQUEST),
```
`400` porque el cliente mandó un Base64 que no es válido. El error es del cliente.

```java
    CSV_FORMAT_INVALID  (ErrorCode.CSV_FORMAT_INVALID,   HttpStatus.UNPROCESSABLE_ENTITY),
    CSV_EMPTY_FILE      (ErrorCode.CSV_EMPTY_FILE,       HttpStatus.UNPROCESSABLE_ENTITY),
    CSV_TYPE_CONVERSION (ErrorCode.CSV_TYPE_CONVERSION,  HttpStatus.UNPROCESSABLE_ENTITY),
    CSV_TYPE_MISMATCH   (ErrorCode.CSV_TYPE_MISMATCH,    HttpStatus.UNPROCESSABLE_ENTITY),
```
`422` para todos los errores de contenido: el request llegó bien formado, pero
lo que hay dentro del CSV no se puede procesar.

```java
    CSV_MAPPING_ERROR   (ErrorCode.CSV_MAPPING_ERROR,    HttpStatus.INTERNAL_SERVER_ERROR);
```
`500` porque este error no es culpa del CSV que mandó el cliente — es un fallo
interno al instanciar la clase del MS por reflexión.

```java
    public static ErrorReason fromErrorCode(ErrorCode errorCode) {
        return Arrays.stream(values())
                .filter(r -> r.errorCode == errorCode)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No ErrorReason for: " + errorCode));
    }
```
Búsqueda inversa: dado un `ErrorCode`, devuelve el `ErrorReason` correspondiente.
`== errorCode` y no `.equals()` porque los valores de un enum son singletons —
siempre hay exactamente una instancia de cada uno en la JVM.
`orElseThrow` con mensaje descriptivo para el caso imposible donde un `ErrorCode`
no tenga `ErrorReason` asociado (solo pasaría si se agrega un código sin actualizar este enum).

---

## Limitaciones conocidas

| Limitación | Detalle |
|---|---|
| **Mapeo posicional** | Los campos del POJO deben declararse en el mismo orden que las columnas del CSV. Si el CSV cambia el orden de columnas, el POJO también debe cambiar. No hay forma de mapear por nombre de columna sin modificar la librería. |
| **Solo POJOs planos** | No soporta campos que sean otros objetos (`private Address address`). Solo tipos primitivos, sus wrappers y `String`. Objetos anidados lanzarían `CSV_TYPE_CONVERSION`. |
| **Thread safety** | `CSVParser` de OpenCSV no es thread-safe. La librería lo maneja creando una instancia nueva por cada llamada a `parse()`, lo que es correcto para microservicios con el modelo request-per-thread de Spring MVC. Si se usa en un contexto reactivo o con hilos compartidos, considerar revisión. |
| **Tipos soportados** | Solo los 5 tipos descritos en `CsvReflectionMapper` (`String`, `Integer/int`, `Long/long`, `Double/double`, `Boolean/boolean`). Un tipo no soportado lanza `CSV_TYPE_CONVERSION` en runtime — no hay validación en tiempo de compilación. |
| **Header sin nombre** | Si el CSV no tiene header, el mapeo es puramente posicional. No hay forma de indicar qué columna va a qué campo usando nombres — solo por posición. |
| **Fechas como `Long`** | Las fechas ISO `"yyyy-MM-dd"` solo se convierten automáticamente si el campo del POJO es `Long` (epoch milliseconds). No hay soporte nativo para `LocalDate` o `Date`. |

---

## Flujo completo de un procesamiento exitoso

```
MS llama a: processor.process("SGVsbG8...", MiClase.class)
      │
      ▼
CsvProcessorService.process()
      │
      ├─ decoder.decode("SGVsbG8...")
      │       │
      │       ├─ validateNotEmpty        → no es null ni vacío
      │       ├─ extractBase64Payload    → elimina "data:...," si existe
      │       ├─ decodeBase64ToBytes     → bytes crudos
      │       ├─ convertBytesToUtf8String → texto con charset correcto
      │       └─ removeBomIfPresent      → elimina U+FEFF si Excel lo puso
      │                                     resultado: "companyId,monto\n123,500.0"
      │
      ├─ parser.parse("companyId,monto\n123,500.0")
      │       │
      │       ├─ splitIntoLines          → ["companyId,monto", "123,500.0"]
      │       ├─ findFirstNonEmptyLineIndex → 0
      │       ├─ DelimiterDetector.detect → ','
      │       ├─ HeaderDetector.isHeader  → true (primera línea es header)
      │       └─ parseDataRows           → [["123", "500.0"]]
      │
      └─ mapper.map([["123", "500.0"]], MiClase.class)
              │
              ├─ resolveAccessibleFields  → [Field(companyId:String), Field(monto:Double)]
              ├─ instantiateClass         → new MiClase() vacío
              ├─ populateFieldsFromRow
              │       ├─ field[0] ← "123"   → convertToFieldType → "123" (String)
              │       └─ field[1] ← "500.0" → convertToFieldType → 500.0 (Double)
              └─ resultado: [MiClase{companyId="123", monto=500.0}]
```

---

## Cómo extender la librería

### Agregar un tipo nuevo (ej. `LocalDate`)

Agregar una entrada en `TYPE_CONVERTERS` dentro de `CsvReflectionMapper.buildTypeConverters()`:

```java
converters.put(LocalDate.class, v -> {
    try {
        return LocalDate.parse(v);
    } catch (DateTimeParseException e) {
        throw new CsvProcessingException(ErrorCode.CSV_TYPE_CONVERSION, 0, null, v, "LocalDate");
    }
});
```

No es necesario tocar ninguna otra clase. El mapper consulta el mapa por tipo en runtime.

### Agregar un delimitador candidato (ej. `:`)

Modificar el arreglo `CANDIDATES` en `DelimiterDetector`:

```java
private static final char[] CANDIDATES = {';', ',', '\t', '|', ':'};
```

El orden importa: si dos candidatos aparecen la misma cantidad de veces en la primera
línea, gana el que esté primero en el arreglo.

### Reemplazar OpenCSV por otra librería

Solo tocar los archivos en `adapter/parser/`:
- `CsvParser.java` — quitar las importaciones de `com.opencsv` y adaptar `parseSingleDataRow` y `buildParserWithAutoDetectedDelimiter` a la nueva API.
- `DelimiterDetector.java` — sin dependencia de OpenCSV; generalmente no requiere cambios.

El contrato que debe respetarse está en `domain/port/out/CsvParserPort`:
```java
List<List<String>> parse(String csvText);
```
Mientras `CsvParser` devuelva ese tipo, el dominio y la capa de aplicación no se tocan.
