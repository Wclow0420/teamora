package com.teamora.calendar;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * The bundled catalogue of Malaysian public holidays ({@code holidays/my-public-holidays.json}).
 *
 * <p>This is a <em>suggestion</em> list compiled from published third-party listings — dates move
 * every year (lunar / Islamic calendars) and observance differs by state — so it is never inserted
 * automatically. Admins review it and pick what applies (see {@link CalendarService#importHolidays}).
 */
@Component
public class HolidayCatalogue {

    private static final String RESOURCE = "holidays/my-public-holidays.json";

    /** One catalogue row. {@code note} is a short regional caveat, e.g. "Not observed in Sarawak". */
    public record Entry(int year, LocalDate date, String name, String note) {}

    private final List<Entry> entries;

    public HolidayCatalogue(ObjectMapper om) {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            this.entries = om.readValue(in, new TypeReference<List<Entry>>() {}).stream()
                    .sorted(Comparator.comparing(Entry::date))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, e);
        }
    }

    /** Years the catalogue covers, ascending. */
    public List<Integer> years() {
        return entries.stream().map(Entry::year).distinct().sorted().toList();
    }

    /** That year's suggestions, by date; empty for a year the catalogue doesn't cover. */
    public List<Entry> forYear(int year) {
        return entries.stream().filter(e -> e.year() == year).toList();
    }
}
