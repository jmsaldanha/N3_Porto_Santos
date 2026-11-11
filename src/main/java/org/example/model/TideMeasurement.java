package org.example.model;

import java.time.LocalDateTime;
import java.util.Objects;

/** Medição imutável da tábua de marés (intervalos de 30 minutos). */
public record TideMeasurement(LocalDateTime timestamp, double heightMeters) {

    public TideMeasurement {
        Objects.requireNonNull(timestamp, "timestamp");
        if (heightMeters < -2 || heightMeters > 5) {
            throw new IllegalArgumentException("Altura de maré fora da faixa física: " + heightMeters);
        }
    }

    public boolean isCritical() {
        return heightMeters <= TideTable.CRITICAL_LEVEL_M;
    }

    public int day() {
        return timestamp.getDayOfMonth();
    }

    /** Condição exibida na tabela: Alta / Baixa / Baixa Crítica (≤ 0,4 m). */
    public String condition() {
        if (isCritical()) {
            return "Baixa Crítica";
        }
        return heightMeters >= 0.9 ? "Alta" : "Baixa";
    }
}
