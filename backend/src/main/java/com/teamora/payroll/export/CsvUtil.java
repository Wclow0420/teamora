package com.teamora.payroll.export;

import java.util.List;

/**
 * Minimal RFC-4180-ish CSV writer. Fields are quoted only when they need it
 * (contain a comma, double-quote, CR or LF), and embedded double-quotes are
 * doubled. Rows are joined with CRLF (the RFC-4180 line terminator), which
 * spreadsheets and accountants' tools import cleanly.
 *
 * <p>Kept tiny and dependency-free on purpose — it's the shared escaping helper
 * behind every CSV export and is unit-tested directly ({@code CsvUtilTest}).
 */
public final class CsvUtil {

    private CsvUtil() {
    }

    private static final String LINE_SEP = "\r\n";

    /** Escape a single field, quoting only when required. Null → empty. */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean mustQuote = value.contains(",") || value.contains("\"")
                || value.contains("\n") || value.contains("\r");
        if (!mustQuote) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /** Join one row's already-ordered fields into a CSV line (no trailing separator). */
    public static String row(List<String> fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(fields.get(i)));
        }
        return sb.toString();
    }

    /** Build a full CSV document from rows (each a list of fields), terminated with CRLF. */
    public static String document(List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        for (List<String> r : rows) {
            sb.append(row(r)).append(LINE_SEP);
        }
        return sb.toString();
    }
}
