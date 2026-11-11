package org.example.model;

import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

/** Tábua de marés astronômica de um mês (RF010). */
public class TideTable {

    /** Nível de corte da maré baixa crítica (RN002). */
    public static final double CRITICAL_LEVEL_M = 0.4;

    private final YearMonth month;
    private final String source;
    private final List<TideMeasurement> measurements;
    private final SortedSet<Integer> criticalDays;

    public TideTable(YearMonth month, String source, List<TideMeasurement> measurements) {
        this.month = Objects.requireNonNull(month);
        this.source = ModelValidation.requireText(source, "Origem da tábua");
        if (measurements == null || measurements.isEmpty()) {
            throw new IllegalArgumentException("A tábua de marés não possui medições.");
        }
        this.measurements = measurements.stream()
                .sorted(Comparator.comparing(TideMeasurement::timestamp)).toList();
        TreeSet<Integer> days = new TreeSet<>();
        this.measurements.stream().filter(TideMeasurement::isCritical).forEach(m -> days.add(m.day()));
        this.criticalDays = Collections.unmodifiableSortedSet(days);
    }

    public boolean isCriticalDay(int day) {
        return criticalDays.contains(day);
    }

    public double minHeight(int day) {
        return measurements.stream().filter(m -> m.day() == day)
                .mapToDouble(TideMeasurement::heightMeters).min().orElse(Double.NaN);
    }

    public String getLabel() {
        String m = month.getMonth().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("pt-BR"));
        return Character.toUpperCase(m.charAt(0)) + m.substring(1) + " / " + month.getYear();
    }

    public YearMonth getMonth() {
        return month;
    }

    public String getSource() {
        return source;
    }

    public List<TideMeasurement> getMeasurements() {
        return measurements;
    }

    public SortedSet<Integer> getCriticalDays() {
        return criticalDays;
    }

    @Override
    public String toString() {
        return getLabel();
    }
}
