package com.ecpay.evolution.internal;

import com.ecpay.evolution.ErrorCode;
import com.ecpay.evolution.CsvProcessingException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.Optional;
import java.util.Locale;

public class CsvReflectionMapper {

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
            throw (CsvProcessingException) new CsvProcessingException(
                    ErrorCode.CSV_MAPPING_ERROR, rowNumber, null, null, null).initCause(e);
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
        return value.matches("\\d{4}[-/]\\d{2}[-/]\\d{2}")   // yyyy-MM-dd / yyyy/MM/dd
            || value.matches("\\d{2}[-/]\\d{2}[-/]\\d{4}");  // dd-MM-yyyy / dd/MM/yyyy
    }

    private static Long isoDateToEpoch(String value) {
        return tryParseDate(value, "yyyy-MM-dd")
                .or(() -> tryParseDate(value, "yyyy/MM/dd"))
                .or(() -> tryParseDate(value, "dd/MM/yyyy"))
                .or(() -> tryParseDate(value, "dd-MM-yyyy"))
                .orElseThrow(() -> new CsvProcessingException(ErrorCode.CSV_TYPE_CONVERSION, 0, null, value, "Long(fecha)"));
    }

    private static Optional<Long> tryParseDate(String value, String pattern) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(pattern);
            sdf.setLenient(false);
            return Optional.of(sdf.parse(value).getTime());
        } catch (ParseException e) {
            return Optional.empty();
        }
    }

    private static Object parseDoubleValue(String value) {
        try {
            return Double.parseDouble(normalizeDecimalSeparator(value));
        } catch (NumberFormatException e) {
            throw new CsvProcessingException(ErrorCode.CSV_TYPE_MISMATCH, 0, null, value, "Double");
        }
    }

    private static String normalizeDecimalSeparator(String value) {
        String normalized = value.replace(',', '.');
        if (normalized.chars().filter(c -> c == '.').count() > 1) {
            throw new CsvProcessingException(ErrorCode.CSV_TYPE_MISMATCH, 0, null, value, "Double");
        }
        return normalized;
    }

    private static void rejectIfContainsDecimalPoint(String value, String targetType) {
        if (value.contains(".")) {
            throw new CsvProcessingException(ErrorCode.CSV_TYPE_MISMATCH, 0, null, value, targetType);
        }
    }

    private static Object parseBooleanValue(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "si", "sí", "s", "yes", "y", "1" -> true;
            case "false", "no", "n", "0"                   -> false;
            default -> throw new CsvProcessingException(
                    ErrorCode.CSV_TYPE_MISMATCH, 0, null, value, "Boolean");
        };
    }
}
