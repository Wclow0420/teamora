package com.teamora.payroll.export;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RFC-4180-ish CSV escaping + row/document assembly. */
class CsvUtilTest {

    @Test
    void plainFieldsAreNotQuoted() {
        assertThat(CsvUtil.escape("Amir Hakim")).isEqualTo("Amir Hakim");
        assertThat(CsvUtil.escape("EMP-042")).isEqualTo("EMP-042");
        assertThat(CsvUtil.escape("")).isEmpty();
    }

    @Test
    void nullBecomesEmpty() {
        assertThat(CsvUtil.escape(null)).isEmpty();
    }

    @Test
    void fieldWithCommaIsQuoted() {
        // Grouped money like "4,000.00" carries a comma → must be quoted.
        assertThat(CsvUtil.escape("4,000.00")).isEqualTo("\"4,000.00\"");
    }

    @Test
    void embeddedQuotesAreDoubledAndFieldQuoted() {
        assertThat(CsvUtil.escape("Ah \"Beng\" Tan")).isEqualTo("\"Ah \"\"Beng\"\" Tan\"");
    }

    @Test
    void newlinesForceQuoting() {
        assertThat(CsvUtil.escape("line1\nline2")).isEqualTo("\"line1\nline2\"");
        assertThat(CsvUtil.escape("line1\r\nline2")).isEqualTo("\"line1\r\nline2\"");
    }

    @Test
    void rowJoinsFieldsWithCommas() {
        assertThat(CsvUtil.row(List.of("Amir", "EMP-042", "4,000.00")))
                .isEqualTo("Amir,EMP-042,\"4,000.00\"");
    }

    @Test
    void documentTerminatesEachRowWithCrlf() {
        String doc = CsvUtil.document(List.of(
                List.of("Employee", "Net Pay"),
                List.of("Amir Hakim", "4,285.50")));
        assertThat(doc).isEqualTo("Employee,Net Pay\r\nAmir Hakim,\"4,285.50\"\r\n");
    }

    @Test
    void formulaLikeCells_arePrefixedWithAQuote() {
        assertThat(CsvUtil.escape("=HYPERLINK(\"http://x\")")).isEqualTo("\"'=HYPERLINK(\"\"http://x\"\")\"");
        assertThat(CsvUtil.escape("+60123")).isEqualTo("+60123");          // a plain number stays a number
        assertThat(CsvUtil.escape("+cmd|' /C calc'!A0")).isEqualTo("'+cmd|' /C calc'!A0");
        assertThat(CsvUtil.escape("-2+3")).isEqualTo("'-2+3");
        assertThat(CsvUtil.escape("@SUM(A1:A9)")).isEqualTo("'@SUM(A1:A9)");
        assertThat(CsvUtil.escape("\t=1+1")).isEqualTo("'\t=1+1");
    }

    @Test
    void plainNumbers_includingNegatives_areLeftAlone() {
        assertThat(CsvUtil.escape("-12.50")).isEqualTo("-12.50");
        assertThat(CsvUtil.escape("-4,000.00")).isEqualTo("\"-4,000.00\"");
        assertThat(CsvUtil.escape("900101-14-5678")).isEqualTo("900101-14-5678");
    }
}
