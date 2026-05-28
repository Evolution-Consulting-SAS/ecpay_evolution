# ecpay-evolution

Librería Java para procesar archivos CSV codificados en Base64 y convertirlos en listas de objetos tipados.

## Requisitos

- Java 17+
- Maven

## Uso básico

```java
List<MiClase> rows = CsvProcessor.process(base64Csv, MiClase.class);
```

Los campos del POJO deben coincidir con los nombres de las columnas del CSV. La librería detecta automáticamente el delimitador (`,` o `;`) y los encabezados.

## Errores

`CsvProcessor.process` lanza `CsvProcessingException` si el contenido es inválido, vacío o tiene un error de tipo.
