package com.ecpay.evolution.adapter;

import com.ecpay.evolution.domain.ErrorCode;
import com.ecpay.evolution.domain.CsvProcessingException;
import com.ecpay.evolution.domain.port.out.CsvMapperPort;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;

public class CsvReflectionMapper implements CsvMapperPort {

    private static final Map<Class<?>, Function<String, Object>> TYPE_CONVERTERS = buildTypeConverters();

    private static Map<Class<?>, Function<String, Object>> buildTypeConverters() {
        Map<Class<?>, Function<String, Object>> converters = new LinkedHashMap<>();
        converters.put(String.class,   v -> v);
        converters.put(Integer.class,  CsvReflectionMapper::parseIntegerValue);
        converters.put(int.class,      CsvReflectionMapper::parseIntegerValue);
        converters.put(Long.class,     CsvReflectionMapper::parseLongValue);
        converters.put(long.class,     CsvReflectionMapper::parseLongValue);
        converters.put(Double.class,   CsvReflectionMapper::parseDoubleValue);
        converters.put(double.class,   CsvReflectionMapper::parseDoubleValue);
        converters.put(Boolean.class,  CsvReflectionMapper::parseBooleanValue);
        converters.put(boolean.class,  CsvReflectionMapper::parseBooleanValue);
        return Collections.unmodifiableMap(converters);
    }

    public <T> List<T> map(List<List<String>> rows, Class<T> clazz) {
        Field[] fields = resolveAccessibleFields(clazz);
        List<T> result = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            result.add(mapRowToInstance(rows.get(rowIndex), clazz, fields, rowIndex + 1));
        }
        return result;
    }

    private Field[] resolveAccessibleFields(Class<?> clazz) {
        Field[] fields = Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .toArray(Field[]::new);
        Arrays.stream(fields).forEach(f -> f.setAccessible(true));
        return fields;
    }

    private <T> T mapRowToInstance(List<String> values, Class<T> clazz, Field[] fields, int rowNumber) {
        T instance = instantiateClass(clazz, rowNumber);
        populateFieldsFromRow(instance, fields, values, rowNumber);
        return instance;
    }

    // Mapeo por posición: fields[0] ← values.get(0), fields[1] ← values.get(1), etc.
    // El orden de declaración de campos en el POJO debe coincidir con el orden de columnas del CSV.
    private void populateFieldsFromRow(Object instance, Field[] fields, List<String> values, int rowNumber) {
        for (int i = 0; i < fields.length; i++) {
            String rawValue = i < values.size() ? values.get(i) : null;
            if (rawValue != null) {
                assignFieldValue(instance, fields[i], rawValue, rowNumber);
            }
        }
    }

    private <T> T instantiateClass(Class<T> clazz, int rowNumber) {
        try {
            var constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new CsvProcessingException(ErrorCode.CSV_MAPPING_ERROR, rowNumber, null, null, null);
        }
    }

    private void assignFieldValue(Object instance, Field field, String rawValue, int rowNumber) {
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

    private static final Map<Class<?>, Object> PRIMITIVE_DEFAULTS = Map.of(
            int.class,     0,
            long.class,    0L,
            double.class,  0.0,
            boolean.class, false
    );

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

    private static Object parseIntegerValue(String value) {
        rejectIfContainsDecimalPoint(value, "Integer");
        return Integer.parseInt(value);
    }

    private static Object parseLongValue(String value) {
        if (looksLikeIsoDate(value)) return isoDateToEpoch(value);
        rejectIfContainsDecimalPoint(value, "Long");
        return Long.parseLong(value);
    }

    private static boolean looksLikeIsoDate(String value) {
        return value.matches("\\d{4}-\\d{2}-\\d{2}");
    }

    private static Long isoDateToEpoch(String value) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            sdf.setLenient(false);
            return sdf.parse(value).getTime();
        } catch (ParseException e) {
            throw new CsvProcessingException(ErrorCode.CSV_TYPE_CONVERSION, 0, null, value, "Long");
        }
    }

    private static Object parseDoubleValue(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new CsvProcessingException(ErrorCode.CSV_TYPE_MISMATCH, 0, null, value, "Double");
        }
    }

    private static void rejectIfContainsDecimalPoint(String value, String targetType) {
        if (value.contains(".")) {
            throw new CsvProcessingException(ErrorCode.CSV_TYPE_MISMATCH, 0, null, value, targetType);
        }
    }

    private static Object parseBooleanValue(String value) {
        return switch (value.toLowerCase()) {
            case "true", "si", "sí", "s", "yes", "y", "1" -> true;
            case "false", "no", "n", "0"                   -> false;
            default -> throw new CsvProcessingException(
                    ErrorCode.CSV_TYPE_MISMATCH, 0, null, value, "Boolean");
        };
    }
}
