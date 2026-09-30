package com.ecpay.evolution;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvTableReadTest {

    private static String toBase64(String csv) {
        return Base64.getEncoder().encodeToString(csv.getBytes(StandardCharsets.UTF_8));
    }

    private static CsvProcessingException readFailure(String base64) {
        try {
            CsvProcessor.readTable(base64);
        } catch (CsvProcessingException e) {
            return e;
        }
        throw new AssertionError("Expected CsvProcessingException");
    }

    @Test
    void readTable_returnsHeaderAndRecordsWithPhysicalLinesForLf() {
        CsvTable table = CsvProcessor.readTable(toBase64(
                "codigo,descripcion,hijo\nADM,Administración,\n\nVEN,Ventas,ADM\n"));

        assertThat(table.delimiter()).isEqualTo(',');
        assertThat(table.header()).isEqualTo(
                new CsvRecord(1, 1, 1, List.of("codigo", "descripcion", "hijo")));
        assertThat(table.records()).containsExactly(
                new CsvRecord(2, 2, 2, List.of("ADM", "Administración", "")),
                new CsvRecord(4, 4, 4, List.of("VEN", "Ventas", "ADM")));
    }

    @Test
    void readTable_countsCrlfLinesLikeLf() {
        CsvTable table = CsvProcessor.readTable(toBase64(
                "codigo,descripcion\r\nADM,Administración\r\n\r\nVEN,Ventas\r\n"));

        assertThat(table.records()).containsExactly(
                new CsvRecord(2, 2, 2, List.of("ADM", "Administración")),
                new CsvRecord(4, 4, 4, List.of("VEN", "Ventas")));
    }

    @Test
    void readTable_keepsQuotedLineBreakInsideOneRecordSpanningPhysicalLines() {
        CsvTable table = CsvProcessor.readTable(toBase64(
                "codigo,descripcion\nADM,\"Administración\ny finanzas\"\nVEN,Ventas\n"));

        assertThat(table.records()).containsExactly(
                new CsvRecord(2, 2, 3, List.of("ADM", "Administración\ny finanzas")),
                new CsvRecord(3, 4, 4, List.of("VEN", "Ventas")));
    }

    @Test
    void readTable_deliversQuotedCrlfLineBreakAsLf() {
        CsvTable table = CsvProcessor.readTable(toBase64(
                "codigo,descripcion\r\nADM,\"Administración\r\ny finanzas\"\r\n"));

        assertThat(table.records()).containsExactly(
                new CsvRecord(2, 2, 3, List.of("ADM", "Administración\ny finanzas")));
    }

    @Test
    void readTable_takesFirstNonBlankRecordAsHeaderWithoutGuessing() {
        CsvTable table = CsvProcessor.readTable(toBase64("\n\n2024-01-01,100\n2024-02-01,200\n"));

        assertThat(table.header()).isEqualTo(new CsvRecord(3, 3, 3, List.of("2024-01-01", "100")));
        assertThat(table.records()).containsExactly(
                new CsvRecord(4, 4, 4, List.of("2024-02-01", "200")));
    }

    @Test
    void readTable_keepsDuplicateAndBlankHeaderNamesAndIrregularWidths() {
        CsvTable table = CsvProcessor.readTable(toBase64(
                "codigo,,codigo\nADM\nVEN,Ventas,ADM,EXTRA\n,,\n"));

        assertThat(table.header().cells()).containsExactly("codigo", "", "codigo");
        assertThat(table.records()).extracting(CsvRecord::cells).containsExactly(
                List.of("ADM"),
                List.of("VEN", "Ventas", "ADM", "EXTRA"),
                List.of("", "", ""));
    }

    @Test
    void readTable_returnsParsedTextWithoutTrimmingOrNormalizingQuotes() {
        CsvTable table = CsvProcessor.readTable(toBase64(
                "codigo,descripcion\n  ADM  ,'Admón'\nVEN,“Ventas”\n\"O\"\"Brien\",C:\\ruta\n"));

        assertThat(table.records()).extracting(CsvRecord::cells).containsExactly(
                List.of("  ADM  ", "'Admón'"),
                List.of("VEN", "“Ventas”"),
                List.of("O\"Brien", "C:\\ruta"));
    }

    @Test
    void readTable_keepsWhitespaceOnlyRecordWhenItHasSeveralCells() {
        CsvTable table = CsvProcessor.readTable(toBase64("codigo,descripcion\n   \n  ,  \n"));

        assertThat(table.records()).containsExactly(new CsvRecord(3, 3, 3, List.of("  ", "  ")));
    }

    @Test
    void readTable_returnsHeaderOnlyFileWithoutRecords() {
        CsvTable table = CsvProcessor.readTable(toBase64("codigo,descripcion\n"));

        assertThat(table.header().cells()).containsExactly("codigo", "descripcion");
        assertThat(table.records()).isEmpty();
    }

    @Test
    void readTable_ignoresDelimiterInsideQuotedHeaderCell() {
        CsvTable table = CsvProcessor.readTable(toBase64(
                "\"Apellido, Nombre\";documento\n\"Pérez, Ana\";123\n"));

        assertThat(table.delimiter()).isEqualTo(';');
        assertThat(table.header().cells()).containsExactly("Apellido, Nombre", "documento");
        assertThat(table.records().get(0).cells()).containsExactly("Pérez, Ana", "123");
    }

    @Test
    void readTable_detectsDelimiterOfMultilineQuotedHeader() {
        CsvTable table = CsvProcessor.readTable(toBase64(
                "\"codigo\nlargo\";descripcion\nADM;Administración\n"));

        assertThat(table.delimiter()).isEqualTo(';');
        assertThat(table.header()).isEqualTo(
                new CsvRecord(1, 1, 2, List.of("codigo\nlargo", "descripcion")));
        assertThat(table.records()).containsExactly(
                new CsvRecord(2, 3, 3, List.of("ADM", "Administración")));
    }

    @Test
    void readTable_usesCommaForSingleColumnFile() {
        CsvTable table = CsvProcessor.readTable(toBase64("codigo\nADM\n"));

        assertThat(table.delimiter()).isEqualTo(',');
        assertThat(table.records()).containsExactly(new CsvRecord(2, 2, 2, List.of("ADM")));
    }

    @Test
    void readTable_rejectsHeaderWhereTwoDelimitersGiveTheSameWidth() {
        CsvProcessingException failure = readFailure(toBase64("a;b,c;d,e\n1;2,3;4,5\n"));

        assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.CSV_DELIMITER_AMBIGUOUS);
    }

    @Test
    void readTable_acceptsDataUrlPrefixAndRemovesBom() {
        String base64 = "data:text/csv;base64," + toBase64("\uFEFFcodigo,descripcion\nADM,Adm\n");

        CsvTable table = CsvProcessor.readTable(base64);

        assertThat(table.header().cells()).containsExactly("codigo", "descripcion");
    }

    @Test
    void readTable_rejectsNullBlankOrContentlessInputAsEmptyFile() {
        assertThat(readFailure(null).getErrorCode()).isEqualTo(ErrorCode.CSV_EMPTY_FILE);
        assertThat(readFailure("   ").getErrorCode()).isEqualTo(ErrorCode.CSV_EMPTY_FILE);
        assertThat(readFailure(toBase64("\n \r\n\n")).getErrorCode())
                .isEqualTo(ErrorCode.CSV_EMPTY_FILE);
    }

    @Test
    void readTable_rejectsInvalidBase64AsDecodeError() {
        assertThat(readFailure("@@@no-es-base64@@@").getErrorCode())
                .isEqualTo(ErrorCode.CSV_DECODE_ERROR);
        assertThat(readFailure("YSxi\nYw==").getErrorCode()).isEqualTo(ErrorCode.CSV_DECODE_ERROR);
    }

    @Test
    void readTable_rejectsBytesThatAreNotUtf8InsteadOfReplacingThem() {
        String latin1 = Base64.getEncoder().encodeToString(
                "codigo,descripcion\nADM,Administración\n".getBytes(StandardCharsets.ISO_8859_1));

        assertThat(readFailure(latin1).getErrorCode()).isEqualTo(ErrorCode.CSV_ENCODING_INVALID);
    }

    @Test
    void readTable_reportsStartLineOfUnterminatedQuotedRecord() {
        CsvProcessingException failure = readFailure(toBase64(
                "codigo,descripcion\nADM,Adm\n\nVEN,\"abierta\nNOM,Nómina\n"));

        assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.CSV_FORMAT_INVALID);
        assertThat(failure.getRow()).isEqualTo(4);
    }

    @Test
    void readTable_rejectsTextAfterClosingQuote() {
        CsvProcessingException failure = readFailure(toBase64(
                "codigo,descripcion\n\"AD\"M,Adm\n"));

        assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.CSV_FORMAT_INVALID);
        assertThat(failure.getRow()).isEqualTo(2);
    }

    @Test
    void everyErrorCodeHasAnErrorReason() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(ErrorReason.fromErrorCode(code).getErrorCode()).isEqualTo(code);
        }
    }

    @Test
    void process_keepsLegacyExceptionForInvalidBase64() {
        assertThatThrownBy(() -> CsvProcessor.process("@@@", Object.class))
                .isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(CsvProcessingException.class);
    }
}
