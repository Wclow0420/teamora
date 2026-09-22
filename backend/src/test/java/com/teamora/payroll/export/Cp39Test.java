package com.teamora.payroll.export;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CP39 (PCB) fixed-width layout — pins the documented field positions of layout version
 * {@code CP39-TXT-v1} for a known employee/amount so an accidental layout change is caught.
 */
class Cp39Test {

    @Test
    void employeeLineMatchesDocumentedFixedLayout() {
        // Amir Hakim, tax no SG12345678, NRIC 900101-14-5678, PCB RM30.00, July 2026.
        String line = Cp39Generator.employeeLine(
                "SG12345678", "900101-14-5678", "Amir Hakim", new BigDecimal("30.00"), 7, 2026);

        assertThat(line).hasSize(92);
        assertThat(line.substring(0, 12)).isEqualTo("SG12345678  "); // tax no, 12 cols, space-padded
        assertThat(line.substring(12, 24)).isEqualTo("900101145678"); // NRIC digits only, 12 cols
        assertThat(line.substring(24, 64)).isEqualTo("AMIR HAKIM" + " ".repeat(30)); // name upper, 40 cols
        assertThat(line.substring(64, 75)).isEqualTo("00000003000"); // PCB 3000 sen, 11 cols
        assertThat(line.substring(75, 86)).isEqualTo("00000000000"); // CP38 = 0
        assertThat(line.substring(86, 88)).isEqualTo("07");          // month
        assertThat(line.substring(88, 92)).isEqualTo("2026");        // year
    }

    @Test
    void amountConvertsRinggitToSen() {
        assertThat(Cp39Generator.toSen(new BigDecimal("30.00"))).isEqualTo(3000L);
        assertThat(Cp39Generator.toSen(new BigDecimal("19.75"))).isEqualTo(1975L);
        assertThat(Cp39Generator.toSen(new BigDecimal("1234.56"))).isEqualTo(123456L);
        assertThat(Cp39Generator.toSen(null)).isZero();
    }

    @Test
    void documentHasHeaderThenOneLinePerRecord() {
        String doc = Cp39Generator.document("Lumi Foods", "2026-07", List.of(
                new Cp39Generator.Record("SG1", "900101145678", "Amir Hakim", new BigDecimal("30.00")),
                new Cp39Generator.Record("SG2", "880202145679", "Nadia Rahman", new BigDecimal("100.00"))));

        String[] lines = doc.split("\n");
        assertThat(lines).hasSize(3); // header + 2 detail
        assertThat(lines[0]).contains(Cp39Generator.LAYOUT_VERSION).contains("Lumi Foods").contains("07");
        assertThat(lines[1]).hasSize(92).contains("AMIR HAKIM");
        assertThat(lines[2]).hasSize(92).contains("NADIA RAHMAN");
    }
}
