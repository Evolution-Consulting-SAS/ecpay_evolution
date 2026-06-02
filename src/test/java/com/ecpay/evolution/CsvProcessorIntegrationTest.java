package com.ecpay.evolution;

import com.ecpay.evolution.CsvProcessor;
import com.ecpay.evolution.CsvProcessingException;
import com.ecpay.evolution.ErrorCode;
import com.ecpay.evolution.fixture.HistoricalContributorRow;
import com.ecpay.evolution.fixture.HistoricalOrgRow;
import com.ecpay.evolution.fixture.HistoricalSalaryRow;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvProcessorIntegrationTest {


    private static String toBase64(String csv) {
        return Base64.getEncoder().encodeToString(csv.getBytes(StandardCharsets.UTF_8));
    }

    // -------------------------------------------------------------------------
    // HistoricalSalary — String, Integer, Long, Double
    // -------------------------------------------------------------------------

    @Test
    void processSalary_mapsFull11Columns() {
        String csv = """
                companyId,document,contractNumber,initialDate,finalDate,salaryType,salaryClassType,salary,coinType,salaryReason,spendingAccount
                900123456,12345678,3,2024-01-15,2024-12-31,FIJO,MENSUAL,5000000.0,COP,NORMAL,1
                """;

        System.out.println("[TEST] processSalary_mapsFull11Columns");
        System.out.println("[INPUT] CSV:\n" + csv);

        List<HistoricalSalaryRow> result = CsvProcessor.process(toBase64(csv), HistoricalSalaryRow.class);
        HistoricalSalaryRow row = result.get(0);

        System.out.println("[OUTPUT] companyId=" + row.getCompanyId()
                + " | document=" + row.getDocument()
                + " | contractNumber=" + row.getContractNumber()
                + " | initialDate(epoch)=" + row.getInitialDate()
                + " | salaryType=" + row.getSalaryType()
                + " | salary=" + row.getSalary()
                + " | coinType=" + row.getCoinType());

        assertThat(result).hasSize(1);
        assertThat(row.getCompanyId()).isEqualTo("900123456");
        assertThat(row.getDocument()).isEqualTo("12345678");
        assertThat(row.getContractNumber()).isEqualTo(3);
        assertThat(row.getInitialDate()).isNotNull();  // "2024-01-15" → epoch ms
        assertThat(row.getFinalDate()).isNotNull();    // "2024-12-31" → epoch ms
        assertThat(row.getSalaryType()).isEqualTo("FIJO");
        assertThat(row.getSalaryClassType()).isEqualTo("MENSUAL");
        assertThat(row.getSalary()).isEqualTo(5000000.0);
        assertThat(row.getCoinType()).isEqualTo("COP");
        assertThat(row.getSalaryReason()).isEqualTo("NORMAL");
        assertThat(row.getSpendingAccount()).isEqualTo(1);

        System.out.println("[OK] Todos los campos mapeados correctamente\n");
    }

    @Test
    void processSalary_mapsMultipleRows() {
        String csv = """
                companyId,document,contractNumber,initialDate,finalDate,salaryType,salaryClassType,salary,coinType,salaryReason,spendingAccount
                900123456,11111111,1,2024-01-01,2024-12-31,FIJO,MENSUAL,3000000.0,COP,NORMAL,0
                900123456,22222222,2,2024-03-01,2024-12-31,VARIABLE,QUINCENAL,4500000.0,USD,NUEVA,1
                900123456,33333333,3,2024-06-01,2024-12-31,FIJO,MENSUAL,8000000.0,COP,AJUSTE,0
                """;

        System.out.println("[TEST] processSalary_mapsMultipleRows");
        System.out.println("[INPUT] CSV con 3 filas de salario");

        List<HistoricalSalaryRow> result = CsvProcessor.process(toBase64(csv), HistoricalSalaryRow.class);

        System.out.println("[OUTPUT] Total filas mapeadas: " + result.size());
        for (int i = 0; i < result.size(); i++) {
            HistoricalSalaryRow r = result.get(i);
            System.out.println("  Fila " + (i + 1) + ": document=" + r.getDocument()
                    + " | salary=" + r.getSalary()
                    + " | salaryType=" + r.getSalaryType());
        }

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getDocument()).isEqualTo("11111111");
        assertThat(result.get(1).getDocument()).isEqualTo("22222222");
        assertThat(result.get(2).getDocument()).isEqualTo("33333333");
        assertThat(result.get(1).getSalary()).isEqualTo(4500000.0);

        System.out.println("[OK] 3 filas procesadas correctamente\n");
    }

    // -------------------------------------------------------------------------
    // HistoricalContributor — Boolean y Double
    // -------------------------------------------------------------------------

    @Test
    void processContributor_mapsBooleans() {
        String csv = """
                companyId,document,contractNumber,initialDate,finalDate,contributorType,subContributorType,healthPercentage,pensionPercentage,solidarityPercentage,healthAssistance,pensionAssistance,pensionForeigner
                900123456,12345678,3,2024-01-15,2024-12-31,DEPENDIENTE,NINGUNO,12.5,16.0,2.0,si,no,1
                """;

        System.out.println("[TEST] processContributor_mapsBooleans");
        System.out.println("[INPUT] healthAssistance=si | pensionAssistance=no | pensionForeigner=1");

        List<HistoricalContributorRow> result = CsvProcessor.process(toBase64(csv), HistoricalContributorRow.class);
        HistoricalContributorRow row = result.get(0);

        System.out.println("[OUTPUT] healthAssistance=" + row.getHealthAssistance()
                + " | pensionAssistance=" + row.getPensionAssistance()
                + " | pensionForeigner=" + row.getPensionForeigner());

        assertThat(row.getHealthAssistance()).isTrue();
        assertThat(row.getPensionAssistance()).isFalse();
        assertThat(row.getPensionForeigner()).isTrue();

        System.out.println("[OK] 'si'→true, 'no'→false, '1'→true\n");
    }

    @Test
    void processContributor_mapsDoubles() {
        String csv = """
                companyId,document,contractNumber,initialDate,finalDate,contributorType,subContributorType,healthPercentage,pensionPercentage,solidarityPercentage,healthAssistance,pensionAssistance,pensionForeigner
                900123456,12345678,3,2024-01-15,2024-12-31,DEPENDIENTE,NINGUNO,12.5,16.0,2.0,si,no,no
                """;

        System.out.println("[TEST] processContributor_mapsDoubles");
        System.out.println("[INPUT] healthPercentage=12.5 | pensionPercentage=16.0 | solidarityPercentage=2.0");

        List<HistoricalContributorRow> result = CsvProcessor.process(toBase64(csv), HistoricalContributorRow.class);
        HistoricalContributorRow row = result.get(0);

        System.out.println("[OUTPUT] healthPercentage=" + row.getHealthPercentage()
                + " | pensionPercentage=" + row.getPensionPercentage()
                + " | solidarityPercentage=" + row.getSolidarityPercentage());

        assertThat(row.getHealthPercentage()).isEqualTo(12.5);
        assertThat(row.getPensionPercentage()).isEqualTo(16.0);
        assertThat(row.getSolidarityPercentage()).isEqualTo(2.0);

        System.out.println("[OK] Doubles mapeados correctamente\n");
    }

    // -------------------------------------------------------------------------
    // HistoricalOrg — delimitador ; y headers case-insensitive
    // -------------------------------------------------------------------------

    @Test
    void processOrg_withSemicolonDelimiter() {
        String csv = """
                companyId;document;contractNumber;initialDate;finalDate;departmentId;positionId;positionType
                900123456;12345678;3;1700000000000;1730000000000;10;POS001;TIEMPO_COMPLETO
                """;

        System.out.println("[TEST] processOrg_withSemicolonDelimiter");
        System.out.println("[INPUT] CSV con delimitador ';' (estilo europeo)");

        List<HistoricalOrgRow> result = CsvProcessor.process(toBase64(csv), HistoricalOrgRow.class);
        HistoricalOrgRow row = result.get(0);

        System.out.println("[OUTPUT] companyId=" + row.getCompanyId()
                + " | departmentId=" + row.getDepartmentId()
                + " | positionId=" + row.getPositionId()
                + " | positionType=" + row.getPositionType());

        assertThat(result).hasSize(1);
        assertThat(row.getCompanyId()).isEqualTo("900123456");
        assertThat(row.getDepartmentId()).isEqualTo(10);
        assertThat(row.getPositionId()).isEqualTo("POS001");
        assertThat(row.getPositionType()).isEqualTo("TIEMPO_COMPLETO");

        System.out.println("[OK] Delimitador ';' detectado y procesado correctamente\n");
    }

    @Test
    void processOrg_headerDetectedAndSkipped() {
        String csv = """
                COMPANYID,DOCUMENT,CONTRACTNUMBER,INITIALDATE,FINALDATE,DEPARTMENTID,POSITIONID,POSITIONTYPE
                900123456,12345678,3,2024-01-15,2024-12-31,10,POS001,TIEMPO_COMPLETO
                """;

        System.out.println("[TEST] processOrg_headerDetectedAndSkipped");
        System.out.println("[INPUT] Header con nombres en mayúsculas — la librería lo detecta y salta, luego mapea por posición");

        List<HistoricalOrgRow> result = CsvProcessor.process(toBase64(csv), HistoricalOrgRow.class);

        System.out.println("[OUTPUT] companyId=" + result.get(0).getCompanyId()
                + " | positionId=" + result.get(0).getPositionId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCompanyId()).isEqualTo("900123456");
        assertThat(result.get(0).getPositionId()).isEqualTo("POS001");

        System.out.println("[OK] Header detectado y saltado — datos mapeados por posición\n");
    }

    @Test
    void processOrg_withoutHeader_mapsDirectly() {
        String csv = "900123456,12345678,3,2024-01-15,2024-12-31,10,POS001,TIEMPO_COMPLETO\n"
                + "900123456,99999999,5,2024-01-15,2024-12-31,20,POS002,MEDIO_TIEMPO\n";

        System.out.println("[TEST] processOrg_withoutHeader_mapsDirectly");
        System.out.println("[INPUT] CSV sin header — primera línea ya son datos");

        List<HistoricalOrgRow> result = CsvProcessor.process(toBase64(csv), HistoricalOrgRow.class);

        System.out.println("[OUTPUT] Total filas: " + result.size());
        for (int i = 0; i < result.size(); i++) {
            HistoricalOrgRow r = result.get(i);
            System.out.println("  Fila " + (i + 1) + ": document=" + r.getDocument()
                    + " | positionId=" + r.getPositionId()
                    + " | positionType=" + r.getPositionType());
        }

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getDocument()).isEqualTo("12345678");
        assertThat(result.get(0).getDepartmentId()).isEqualTo(10);
        assertThat(result.get(1).getDocument()).isEqualTo("99999999");
        assertThat(result.get(1).getDepartmentId()).isEqualTo(20);

        System.out.println("[OK] CSV sin header procesado correctamente por posición\n");
    }

    @Test
    void process_csvWithExcelBom_stillWorks() {
        String csvSinBom = "companyId,document,contractNumber,initialDate,finalDate,departmentId,positionId,positionType\n"
                + "900123456,12345678,3,2024-01-15,2024-12-31,10,POS001,TIEMPO_COMPLETO\n";
        String csvConBom = "﻿" + csvSinBom;
        String base64 = Base64.getEncoder().encodeToString(csvConBom.getBytes(StandardCharsets.UTF_8));

        System.out.println("[TEST] process_csvWithExcelBom_stillWorks");
        System.out.println("[INPUT] CSV con BOM de Excel (U+FEFF) al inicio");

        List<HistoricalOrgRow> result = CsvProcessor.process(base64, HistoricalOrgRow.class);

        System.out.println("[OUTPUT] companyId=" + result.get(0).getCompanyId()
                + " (BOM eliminado correctamente — no aparece como parte del campo)");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCompanyId()).isEqualTo("900123456");

        System.out.println("[OK] BOM eliminado automáticamente por la librería\n");
    }

    // -------------------------------------------------------------------------
    // Casos de error
    // -------------------------------------------------------------------------

    @Test
    void process_emptyCsv_throwsCsvEmptyFile() {
        String base64Vacio = toBase64("   ");

        System.out.println("[TEST] process_emptyCsv_throwsCsvEmptyFile");
        System.out.println("[INPUT] Base64 de un string vacío '   '");

        assertThatThrownBy(() -> CsvProcessor.process(base64Vacio, HistoricalOrgRow.class))
                .isInstanceOf(CsvProcessingException.class)
                .satisfies(ex -> {
                    CsvProcessingException e = (CsvProcessingException) ex;
                    System.out.println("[OUTPUT] Excepción: " + e.getMessage());
                    System.out.println("         ErrorCode: " + e.getErrorCode());
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CSV_EMPTY_FILE);
                });

        System.out.println("[OK] CsvProcessingException con CSV_EMPTY_FILE lanzada correctamente\n");
    }

    @Test
    void process_invalidBase64_throwsIllegalArgument() {
        String entrada = "esto-no-es-base64!!!";

        System.out.println("[TEST] process_invalidBase64_throwsIllegalArgument");
        System.out.println("[INPUT] String no-base64: '" + entrada + "'");

        assertThatThrownBy(() -> CsvProcessor.process(entrada, HistoricalOrgRow.class))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(ex -> System.out.println("[OUTPUT] Excepción: " + ex.getMessage()));

        System.out.println("[OK] IllegalArgumentException lanzada correctamente\n");
    }

    // -------------------------------------------------------------------------
    // Variantes de formato: fechas con /, double con coma, comillas
    // -------------------------------------------------------------------------

    @Test
    void dateWithSlash_mapsToSameEpochAsDashFormat() {
        String csvDash = """
                companyId,document,contractNumber,initialDate,finalDate,salaryType,salaryClassType,salary,coinType,salaryReason,spendingAccount
                900123456,12345678,3,2024-01-15,2024-12-31,FIJO,MENSUAL,5000000.0,COP,NORMAL,1
                """;
        String csvSlash = """
                companyId,document,contractNumber,initialDate,finalDate,salaryType,salaryClassType,salary,coinType,salaryReason,spendingAccount
                900123456,12345678,3,2024/01/15,2024/12/31,FIJO,MENSUAL,5000000.0,COP,NORMAL,1
                """;

        System.out.println("[TEST] dateWithSlash_mapsToSameEpochAsDashFormat");
        System.out.println("[INPUT] initialDate con '-': 2024-01-15 | con '/': 2024/01/15");

        HistoricalSalaryRow rowDash  = CsvProcessor.process(toBase64(csvDash),  HistoricalSalaryRow.class).get(0);
        HistoricalSalaryRow rowSlash = CsvProcessor.process(toBase64(csvSlash), HistoricalSalaryRow.class).get(0);

        System.out.println("[OUTPUT] epoch dash=" + rowDash.getInitialDate() + " | epoch slash=" + rowSlash.getInitialDate());

        assertThat(rowSlash.getInitialDate()).isEqualTo(rowDash.getInitialDate());
        assertThat(rowSlash.getFinalDate()).isEqualTo(rowDash.getFinalDate());

        System.out.println("[OK] yyyy/MM/dd produce el mismo epoch que yyyy-MM-dd\n");
    }

    @Test
    void doubleWithComma_semicolonDelimiter_mapsCorrectly() {
        String csv = """
                companyId;document;contractNumber;initialDate;finalDate;salaryType;salaryClassType;salary;coinType;salaryReason;spendingAccount
                900123456;12345678;3;2024-01-15;2024-12-31;FIJO;MENSUAL;5000000,75;COP;NORMAL;1
                """;

        System.out.println("[TEST] doubleWithComma_semicolonDelimiter_mapsCorrectly");
        System.out.println("[INPUT] salary='5000000,75' con delimitador ';'");

        HistoricalSalaryRow row = CsvProcessor.process(toBase64(csv), HistoricalSalaryRow.class).get(0);

        System.out.println("[OUTPUT] salary=" + row.getSalary());

        assertThat(row.getSalary()).isEqualTo(5000000.75);

        System.out.println("[OK] Coma decimal '5000000,75' → 5000000.75\n");
    }

    @Test
    void doubleQuotedValues_areStrippedAndMappedCorrectly() {
        String csv = "companyId,document,contractNumber,initialDate,finalDate,salaryType,salaryClassType,salary,coinType,salaryReason,spendingAccount\n"
                + "\"900123456\",\"12345678\",\"3\",\"2024-01-15\",\"2024-12-31\",\"FIJO\",\"MENSUAL\",\"5000000.0\",\"COP\",\"NORMAL\",\"1\"\n";

        System.out.println("[TEST] doubleQuotedValues_areStrippedAndMappedCorrectly");
        System.out.println("[INPUT] Todos los campos entre comillas dobles \"...\"");

        HistoricalSalaryRow row = CsvProcessor.process(toBase64(csv), HistoricalSalaryRow.class).get(0);

        System.out.println("[OUTPUT] companyId=" + row.getCompanyId() + " | salary=" + row.getSalary());

        assertThat(row.getCompanyId()).isEqualTo("900123456");
        assertThat(row.getContractNumber()).isEqualTo(3);
        assertThat(row.getSalary()).isEqualTo(5000000.0);
        assertThat(row.getSalaryType()).isEqualTo("FIJO");

        System.out.println("[OK] Comillas dobles stripadas por OpenCSV (RFC 4180)\n");
    }

    @Test
    void singleQuotedValues_areStrippedAndMappedCorrectly() {
        String csv = "companyId,document,contractNumber,initialDate,finalDate,salaryType,salaryClassType,salary,coinType,salaryReason,spendingAccount\n"
                + "'900123456','12345678','3','2024-01-15','2024-12-31','FIJO','MENSUAL','5000000.0','COP','NORMAL','1'\n";

        System.out.println("[TEST] singleQuotedValues_areStrippedAndMappedCorrectly");
        System.out.println("[INPUT] Todos los campos entre comillas simples '...'");

        HistoricalSalaryRow row = CsvProcessor.process(toBase64(csv), HistoricalSalaryRow.class).get(0);

        System.out.println("[OUTPUT] companyId=" + row.getCompanyId() + " | salary=" + row.getSalary());

        assertThat(row.getCompanyId()).isEqualTo("900123456");
        assertThat(row.getContractNumber()).isEqualTo(3);
        assertThat(row.getSalary()).isEqualTo(5000000.0);
        assertThat(row.getSalaryType()).isEqualTo("FIJO");

        System.out.println("[OK] Comillas simples stripadas por la librería\n");
    }

    @Test
    void doubleWithComma_commaDelimiter_quotedField_mapsCorrectly() {
        String csv = "companyId,document,contractNumber,initialDate,finalDate,salaryType,salaryClassType,salary,coinType,salaryReason,spendingAccount\n"
                + "900123456,12345678,3,2024-01-15,2024-12-31,FIJO,MENSUAL,\"5000000,75\",COP,NORMAL,1\n";

        System.out.println("[TEST] doubleWithComma_commaDelimiter_quotedField_mapsCorrectly");
        System.out.println("[INPUT] salary=\"5000000,75\" entre comillas dobles con delimitador ','");

        HistoricalSalaryRow row = CsvProcessor.process(toBase64(csv), HistoricalSalaryRow.class).get(0);

        System.out.println("[OUTPUT] salary=" + row.getSalary());

        assertThat(row.getSalary()).isEqualTo(5000000.75);

        System.out.println("[OK] Coma decimal protegida por comillas dobles → 5000000.75\n");
    }

    @Test
    void process_integerWithDecimal_throwsTypeMismatch() {
        String csv = """
                companyId,document,contractNumber,initialDate,finalDate,departmentId,positionId,positionType
                900123456,12345678,3.5,2024-01-15,2024-12-31,10,POS001,TIEMPO_COMPLETO
                """;

        System.out.println("[TEST] process_integerWithDecimal_throwsTypeMismatch");
        System.out.println("[INPUT] contractNumber='3.5' pero el campo es Integer");

        assertThatThrownBy(() -> CsvProcessor.process(toBase64(csv), HistoricalOrgRow.class))
                .isInstanceOf(CsvProcessingException.class)
                .satisfies(ex -> {
                    CsvProcessingException e = (CsvProcessingException) ex;
                    System.out.println("[OUTPUT] Excepción: " + e.getMessage());
                    System.out.println("         ErrorCode: " + e.getErrorCode()
                            + " | fila: " + e.getRow()
                            + " | columna: " + e.getColumn()
                            + " | valor: " + e.getRawValue());
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CSV_TYPE_MISMATCH);
                    assertThat(e.getColumn()).isEqualTo("contractNumber");
                    assertThat(e.getRow()).isEqualTo(1);
                });

        System.out.println("[OK] Error con contexto completo: fila + columna + valor incorrecto\n");
    }

    // =========================================================================
    // STRESS TESTS — formatos de fecha colombianos y variantes
    // =========================================================================

    @Test
    void date_ddMMyyyy_slash_mapsCorrectly() {
        String csv = """
                companyId,document,contractNumber,initialDate,finalDate,salaryType,salaryClassType,salary,coinType,salaryReason,spendingAccount
                900123456,12345678,1,15/01/2024,31/12/2024,FIJO,MENSUAL,5000000.0,COP,NORMAL,1
                """;

        System.out.println("[TEST] date_ddMMyyyy_slash_mapsCorrectly");
        System.out.println("[INPUT] initialDate='15/01/2024' (formato dd/MM/yyyy con '/')");

        HistoricalSalaryRow row = CsvProcessor.process(toBase64(csv), HistoricalSalaryRow.class).get(0);

        System.out.println("[OUTPUT] initialDate(epoch)=" + row.getInitialDate()
                + " | finalDate(epoch)=" + row.getFinalDate());

        assertThat(row.getInitialDate()).isEqualTo(1705291200000L); // 2024-01-15 00:00:00 America/Santo_Domingo
        assertThat(row.getFinalDate()).isNotNull();

        System.out.println("[OK] dd/MM/yyyy mapeado correctamente a epoch America/Santo_Domingo\n");
    }

    @Test
    void date_ddMMyyyy_dash_mapsCorrectly() {
        String csv = """
                companyId,document,contractNumber,initialDate,finalDate,salaryType,salaryClassType,salary,coinType,salaryReason,spendingAccount
                900123456,12345678,1,15-01-2024,31-12-2024,FIJO,MENSUAL,5000000.0,COP,NORMAL,1
                """;

        System.out.println("[TEST] date_ddMMyyyy_dash_mapsCorrectly");
        System.out.println("[INPUT] initialDate='15-01-2024' (formato dd-MM-yyyy con '-')");

        HistoricalSalaryRow row = CsvProcessor.process(toBase64(csv), HistoricalSalaryRow.class).get(0);

        System.out.println("[OUTPUT] initialDate(epoch)=" + row.getInitialDate());

        assertThat(row.getInitialDate()).isEqualTo(1705291200000L); // 2024-01-15 00:00:00 America/Santo_Domingo

        System.out.println("[OK] dd-MM-yyyy mapeado correctamente a epoch America/Santo_Domingo\n");
    }

    @Test
    void date_yyyyMMdd_producesLocalEpoch() {
        String csv = """
                companyId,document,contractNumber,initialDate,finalDate,salaryType,salaryClassType,salary,coinType,salaryReason,spendingAccount
                900123456,12345678,1,2024-01-15,2024-12-31,FIJO,MENSUAL,5000000.0,COP,NORMAL,1
                """;

        System.out.println("[TEST] date_yyyyMMdd_producesLocalEpoch");
        System.out.println("[INPUT] initialDate='2024-01-15' — verificar epoch en America/Santo_Domingo");

        HistoricalSalaryRow row = CsvProcessor.process(toBase64(csv), HistoricalSalaryRow.class).get(0);

        System.out.println("[OUTPUT] epoch=" + row.getInitialDate()
                + " (esperado=1705291200000 = 2024-01-15 00:00:00 America/Santo_Domingo / UTC-4)");

        assertThat(row.getInitialDate()).isEqualTo(1705291200000L);

        System.out.println("[OK] yyyy-MM-dd produce epoch correcto en America/Santo_Domingo\n");
    }

    @Test
    void date_invalid_throwsTypeMismatch() {
        String csv = """
                companyId,document,contractNumber,initialDate,finalDate,salaryType,salaryClassType,salary,coinType,salaryReason,spendingAccount
                900123456,12345678,1,2024-13-01,2024-12-31,FIJO,MENSUAL,5000000.0,COP,NORMAL,1
                """;

        System.out.println("[TEST] date_invalid_throwsTypeMismatch");
        System.out.println("[INPUT] initialDate='2024-13-01' (mes 13 — inválido)");

        assertThatThrownBy(() -> CsvProcessor.process(toBase64(csv), HistoricalSalaryRow.class))
                .isInstanceOf(CsvProcessingException.class)
                .satisfies(ex -> {
                    CsvProcessingException e = (CsvProcessingException) ex;
                    System.out.println("[OUTPUT] Excepción: " + e.getMessage());
                    System.out.println("         ErrorCode: " + e.getErrorCode()
                            + " | fila: " + e.getRow() + " | columna: " + e.getColumn());
                    assertThat(e.getErrorCode()).isIn(ErrorCode.CSV_TYPE_MISMATCH, ErrorCode.CSV_TYPE_CONVERSION);
                    assertThat(e.getRow()).isEqualTo(1);
                });

        System.out.println("[OK] Fecha inválida lanza excepción con contexto\n");
    }

    // =========================================================================
    // STRESS TESTS — booleanos extremos
    // =========================================================================

    @Test
    void boolean_invalid_throwsTypeMismatch() {
        String csv = """
                companyId,document,contractNumber,initialDate,finalDate,contributorType,subContributorType,healthPercentage,pensionPercentage,solidarityPercentage,healthAssistance,pensionAssistance,pensionForeigner
                900123456,12345678,3,2024-01-15,2024-12-31,DEPENDIENTE,NINGUNO,12.5,16.0,2.0,maybe,no,1
                """;

        System.out.println("[TEST] boolean_invalid_throwsTypeMismatch");
        System.out.println("[INPUT] healthAssistance='maybe' — no es un valor booleano válido");

        assertThatThrownBy(() -> CsvProcessor.process(toBase64(csv), HistoricalContributorRow.class))
                .isInstanceOf(CsvProcessingException.class)
                .satisfies(ex -> {
                    CsvProcessingException e = (CsvProcessingException) ex;
                    System.out.println("[OUTPUT] Excepción: " + e.getMessage());
                    System.out.println("         ErrorCode: " + e.getErrorCode()
                            + " | fila: " + e.getRow() + " | columna: " + e.getColumn()
                            + " | valor: " + e.getRawValue());
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CSV_TYPE_MISMATCH);
                    assertThat(e.getColumn()).isEqualTo("healthAssistance");
                    assertThat(e.getRow()).isEqualTo(1);
                });

        System.out.println("[OK] Booleano inválido lanza CSV_TYPE_MISMATCH con fila y columna\n");
    }

    @Test
    void boolean_uppercaseSi_mapsTrue() {
        String csv = """
                companyId,document,contractNumber,initialDate,finalDate,contributorType,subContributorType,healthPercentage,pensionPercentage,solidarityPercentage,healthAssistance,pensionAssistance,pensionForeigner
                900123456,12345678,3,2024-01-15,2024-12-31,DEPENDIENTE,NINGUNO,12.5,16.0,2.0,SI,NO,SÍ
                """;

        System.out.println("[TEST] boolean_uppercaseSi_mapsTrue");
        System.out.println("[INPUT] healthAssistance='SI' | pensionAssistance='NO' | pensionForeigner='SÍ' (mayúsculas)");

        HistoricalContributorRow row = CsvProcessor.process(toBase64(csv), HistoricalContributorRow.class).get(0);

        System.out.println("[OUTPUT] healthAssistance=" + row.getHealthAssistance()
                + " | pensionAssistance=" + row.getPensionAssistance()
                + " | pensionForeigner=" + row.getPensionForeigner());

        assertThat(row.getHealthAssistance()).isTrue();
        assertThat(row.getPensionAssistance()).isFalse();
        assertThat(row.getPensionForeigner()).isTrue();

        System.out.println("[OK] 'SI'→true, 'NO'→false, 'SÍ'→true\n");
    }

    // =========================================================================
    // STRESS TESTS — números con separadores de miles europeos
    // =========================================================================

    @Test
    void double_europeanThousandsSeparator_throwsMismatch() {
        String csv = """
                companyId;document;contractNumber;initialDate;finalDate;salaryType;salaryClassType;salary;coinType;salaryReason;spendingAccount
                900123456;12345678;1;2024-01-15;2024-12-31;FIJO;MENSUAL;1.000.000,50;COP;NORMAL;1
                """;

        System.out.println("[TEST] double_europeanThousandsSeparator_throwsMismatch");
        System.out.println("[INPUT] salary='1.000.000,50' — punto como miles y coma como decimal (ambiguo)");

        assertThatThrownBy(() -> CsvProcessor.process(toBase64(csv), HistoricalSalaryRow.class))
                .isInstanceOf(CsvProcessingException.class)
                .satisfies(ex -> {
                    CsvProcessingException e = (CsvProcessingException) ex;
                    System.out.println("[OUTPUT] Excepción: " + e.getMessage());
                    System.out.println("         ErrorCode: " + e.getErrorCode()
                            + " | columna: " + e.getColumn() + " | valor: " + e.getRawValue());
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CSV_TYPE_MISMATCH);
                    assertThat(e.getColumn()).isEqualTo("salary");
                });

        System.out.println("[OK] Formato europeo con miles lanza error claro en lugar de dato corrupto\n");
    }

    // =========================================================================
    // STRESS TESTS — estructura del CSV (columnas de más o de menos)
    // =========================================================================

    @Test
    void csv_fewerColumnsThanFields_remainsNullOrZero() {
        // CSV solo trae 5 columnas; HistoricalOrgRow tiene 8 campos
        String csv = "900123456,12345678,3,2024-01-15,2024-12-31\n";

        System.out.println("[TEST] csv_fewerColumnsThanFields_remainsNullOrZero");
        System.out.println("[INPUT] CSV con 5 columnas — HistoricalOrgRow tiene 8 campos");

        List<HistoricalOrgRow> result = CsvProcessor.process(toBase64(csv), HistoricalOrgRow.class);
        HistoricalOrgRow row = result.get(0);

        System.out.println("[OUTPUT] departmentId=" + row.getDepartmentId()
                + " | positionId=" + row.getPositionId()
                + " | positionType=" + row.getPositionType());

        assertThat(result).hasSize(1);
        assertThat(row.getCompanyId()).isEqualTo("900123456");
        assertThat(row.getDepartmentId()).isNull();
        assertThat(row.getPositionId()).isNull();
        assertThat(row.getPositionType()).isNull();

        System.out.println("[OK] Campos sin columna en CSV quedan null — sin error\n");
    }

    @Test
    void csv_moreColumnsThanFields_extraIgnored() {
        // CSV trae 10 columnas; HistoricalOrgRow tiene 8 campos
        String csv = "900123456,12345678,3,2024-01-15,2024-12-31,10,POS001,TIEMPO_COMPLETO,EXTRA1,EXTRA2\n";

        System.out.println("[TEST] csv_moreColumnsThanFields_extraIgnored");
        System.out.println("[INPUT] CSV con 10 columnas — HistoricalOrgRow tiene 8 campos");

        List<HistoricalOrgRow> result = CsvProcessor.process(toBase64(csv), HistoricalOrgRow.class);
        HistoricalOrgRow row = result.get(0);

        System.out.println("[OUTPUT] companyId=" + row.getCompanyId()
                + " | positionType=" + row.getPositionType() + " (columnas extra ignoradas)");

        assertThat(result).hasSize(1);
        assertThat(row.getCompanyId()).isEqualTo("900123456");
        assertThat(row.getPositionType()).isEqualTo("TIEMPO_COMPLETO");

        System.out.println("[OK] Columnas extra ignoradas — sin error\n");
    }

    // =========================================================================
    // STRESS TESTS — comillas simples en header y datos
    // =========================================================================

    @Test
    void csv_singleQuotedHeader_detectedAndSkipped() {
        String csv = "'companyId','document','contractNumber','initialDate','finalDate','departmentId','positionId','positionType'\n"
                + "900123456,12345678,3,2024-01-15,2024-12-31,10,POS001,TIEMPO_COMPLETO\n";

        System.out.println("[TEST] csv_singleQuotedHeader_detectedAndSkipped");
        System.out.println("[INPUT] Header con todas las columnas entre comillas simples '...'");

        List<HistoricalOrgRow> result = CsvProcessor.process(toBase64(csv), HistoricalOrgRow.class);

        System.out.println("[OUTPUT] filas=" + result.size()
                + " | companyId=" + result.get(0).getCompanyId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCompanyId()).isEqualTo("900123456");
        assertThat(result.get(0).getPositionId()).isEqualTo("POS001");

        System.out.println("[OK] Header con comillas simples detectado y saltado\n");
    }

    @Test
    void csv_noHeader_singleQuotedValues_mapsCorrectly() {
        String csv = "'900123456','12345678','3','2024-01-15','2024-12-31','10','POS001','TIEMPO_COMPLETO'\n";

        System.out.println("[TEST] csv_noHeader_singleQuotedValues_mapsCorrectly");
        System.out.println("[INPUT] Sin header — primera línea son datos con comillas simples");

        List<HistoricalOrgRow> result = CsvProcessor.process(toBase64(csv), HistoricalOrgRow.class);
        HistoricalOrgRow row = result.get(0);

        System.out.println("[OUTPUT] companyId=" + row.getCompanyId()
                + " | contractNumber=" + row.getContractNumber()
                + " | departmentId=" + row.getDepartmentId());

        assertThat(result).hasSize(1);
        assertThat(row.getCompanyId()).isEqualTo("900123456");
        assertThat(row.getContractNumber()).isEqualTo(3);
        assertThat(row.getDepartmentId()).isEqualTo(10);
        assertThat(row.getPositionId()).isEqualTo("POS001");

        System.out.println("[OK] Datos con comillas simples sin header mapeados correctamente\n");
    }

    @Test
    void csv_semicolonDelimiter_singleQuotedValues_mapsCorrectly() {
        String csv = "'companyId';'document';'contractNumber';'initialDate';'finalDate';'departmentId';'positionId';'positionType'\n"
                + "'900123456';'12345678';'3';'2024-01-15';'2024-12-31';'10';'POS001';'TIEMPO_COMPLETO'\n";

        System.out.println("[TEST] csv_semicolonDelimiter_singleQuotedValues_mapsCorrectly");
        System.out.println("[INPUT] Delimitador ';' + valores entre comillas simples");

        List<HistoricalOrgRow> result = CsvProcessor.process(toBase64(csv), HistoricalOrgRow.class);
        HistoricalOrgRow row = result.get(0);

        System.out.println("[OUTPUT] companyId=" + row.getCompanyId()
                + " | departmentId=" + row.getDepartmentId());

        assertThat(result).hasSize(1);
        assertThat(row.getCompanyId()).isEqualTo("900123456");
        assertThat(row.getDepartmentId()).isEqualTo(10);
        assertThat(row.getPositionType()).isEqualTo("TIEMPO_COMPLETO");

        System.out.println("[OK] ';' + comillas simples procesados correctamente\n");
    }

    // =========================================================================
    // STRESS TESTS — líneas en blanco intercaladas y BOM con semicolón
    // =========================================================================

    @Test
    void csv_multipleBlankLines_ignored() {
        String csv = "companyId,document,contractNumber,initialDate,finalDate,departmentId,positionId,positionType\n"
                + "\n"
                + "900123456,11111111,1,2024-01-15,2024-12-31,10,POS001,TIEMPO_COMPLETO\n"
                + "\n"
                + "900123456,22222222,2,2024-03-01,2024-12-31,20,POS002,MEDIO_TIEMPO\n"
                + "\n";

        System.out.println("[TEST] csv_multipleBlankLines_ignored");
        System.out.println("[INPUT] CSV con líneas en blanco antes, entre y después de los datos");

        List<HistoricalOrgRow> result = CsvProcessor.process(toBase64(csv), HistoricalOrgRow.class);

        System.out.println("[OUTPUT] filas=" + result.size());
        result.forEach(r -> System.out.println("  document=" + r.getDocument()));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getDocument()).isEqualTo("11111111");
        assertThat(result.get(1).getDocument()).isEqualTo("22222222");

        System.out.println("[OK] Líneas en blanco ignoradas — solo 2 filas de datos\n");
    }

    @Test
    void csv_withBom_semicolonDelimiter_mapsCorrectly() {
        String csvSinBom = "companyId;document;contractNumber;initialDate;finalDate;departmentId;positionId;positionType\n"
                + "900123456;12345678;3;2024-01-15;2024-12-31;10;POS001;TIEMPO_COMPLETO\n";
        String csvConBom = "﻿" + csvSinBom;
        String base64 = Base64.getEncoder().encodeToString(csvConBom.getBytes(StandardCharsets.UTF_8));

        System.out.println("[TEST] csv_withBom_semicolonDelimiter_mapsCorrectly");
        System.out.println("[INPUT] BOM de Excel + delimitador ';'");

        List<HistoricalOrgRow> result = CsvProcessor.process(base64, HistoricalOrgRow.class);

        System.out.println("[OUTPUT] companyId=" + result.get(0).getCompanyId()
                + " | departmentId=" + result.get(0).getDepartmentId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCompanyId()).isEqualTo("900123456");
        assertThat(result.get(0).getDepartmentId()).isEqualTo(10);

        System.out.println("[OK] BOM + semicolón procesados correctamente\n");
    }
    // =========================================================================
    // STRESS TESTS — comillas en CSV con delimitador TAB
    // =========================================================================

    @Test
    void csv_tabDelimiter_mixedQuotes_strippedCorrectly() {
        // Simula exactamente el formato que viene del sistema: TAB + comillas simples y dobles mezcladas
        String csv = "COMPANYID\tDOCUMENT\tCONTRACTNUMBER\tINITIALDATE\tFINALDATE\tDEPARTMENTID\tPOSITIONID\tPOSITIONTYPE\n"
                + "'101681136'\t\"22500131689\"\t1\t2026-05-15\t2026-12-30\t10\tPOS001\tTIEMPO_COMPLETO\n";

        System.out.println("[TEST] csv_tabDelimiter_mixedQuotes_strippedCorrectly");
        System.out.println("[INPUT] TAB delimitador | companyId con '' | document con \"\"");

        HistoricalOrgRow row = CsvProcessor.process(toBase64(csv), HistoricalOrgRow.class).get(0);

        System.out.println("[OUTPUT] companyId=[" + row.getCompanyId() + "]"
                + " | document=[" + row.getDocument() + "]");

        assertThat(row.getCompanyId()).isEqualTo("101681136");   // sin comillas simples
        assertThat(row.getDocument()).isEqualTo("22500131689");  // sin comillas dobles

        System.out.println("[OK] Comillas simples y dobles quitadas con delimitador TAB\n");
    }

    @Test
    void csv_unicodeTypographicQuotes_strippedCorrectly() {
        // Comillas tipográficas de Excel/Word: ‘’ (simples) y “” (dobles)
        String csv = "COMPANYID\tDOCUMENT\tCONTRACTNUMBER\tINITIALDATE\tFINALDATE\tDEPARTMENTID\tPOSITIONID\tPOSITIONTYPE\n"
                + "‘101681136’\t“22500131689”\t1\t2026-05-15\t2026-12-30\t10\tPOS001\tTIEMPO_COMPLETO\n";

        System.out.println("[TEST] csv_unicodeTypographicQuotes_strippedCorrectly");
        System.out.println("[INPUT] Comillas Unicode: ‘...’ (simples) y “...” (dobles)");

        HistoricalOrgRow row = CsvProcessor.process(toBase64(csv), HistoricalOrgRow.class).get(0);

        System.out.println("[OUTPUT] companyId=[" + row.getCompanyId() + "] | document=[" + row.getDocument() + "]");

        assertThat(row.getCompanyId()).isEqualTo("101681136");
        assertThat(row.getDocument()).isEqualTo("22500131689");

        System.out.println("[OK] Comillas tipográficas Unicode normalizadas y removidas\n");
    }
}
