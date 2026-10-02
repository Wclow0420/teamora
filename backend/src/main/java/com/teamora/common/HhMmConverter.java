package com.teamora.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Stores a wall-clock {@link LocalTime} as plain {@code HH:mm} text. A SQL
 * {@code TIME} column goes through the JDBC time-zone conversion, which shifts
 * values written outside Hibernate (e.g. a migration's column default).
 */
@Converter
public class HhMmConverter implements AttributeConverter<LocalTime, String> {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public String convertToDatabaseColumn(LocalTime time) {
        return time == null ? null : time.format(HH_MM);
    }

    @Override
    public LocalTime convertToEntityAttribute(String value) {
        return value == null || value.isBlank() ? null : LocalTime.parse(value.trim(), HH_MM);
    }
}
