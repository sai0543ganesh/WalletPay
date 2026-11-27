package com.walletpay.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CsvSafetyTest {
    @ParameterizedTest
    @ValueSource(strings = {"=SUM(A1:A2)", "+cmd", "-cmd", "@SUM(A1)", "  =1+1", "\t=1+1"})
    void preventsSpreadsheetFormulaExecution(String value) {
        assertThat(TransactionQueryService.csvCell(value)).startsWith("\"'");
    }

    @Test
    void escapesQuotesAndCommas() {
        assertThat(TransactionQueryService.csvCell("hello, \"world\""))
                .isEqualTo("\"hello, \"\"world\"\"\"");
    }

    @Test
    void preservesOrdinaryText() {
        assertThat(TransactionQueryService.csvCell("Coffee")).isEqualTo("\"Coffee\"");
    }
}
