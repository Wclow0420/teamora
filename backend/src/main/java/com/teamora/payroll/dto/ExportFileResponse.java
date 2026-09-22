package com.teamora.payroll.dto;

/**
 * A generated export file returned inline as text: the app writes {@code content} to a
 * file named {@code filename} and shares it. {@code mimeType} is {@code text/csv} for the
 * CSV exports and {@code text/plain} for the CP39 text file.
 */
public record ExportFileResponse(
        String filename,
        String mimeType,
        String content
) {}
