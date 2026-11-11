package org.example.model;

import java.util.Locale;

/**
 * Commodity do catálogo portuário APS (RF007) — tabela {@code port_cargo_catalog}.
 * A taxa média é um número com unidade (t/dia, mov/dia, unid/dia, pax/dia).
 */
public class CargoProduct {

    public static final String TONS_PER_DAY = "t/dia";

    private final String code;
    private String name;
    private CargoDirection direction;
    private CargoCategory profile;
    private double averageRate;
    private String rateUnit;

    public CargoProduct(String code, String name, CargoDirection direction, CargoCategory profile,
                        double averageRate, String rateUnit) {
        this.code = ModelValidation.requireText(code, "Código do produto").toUpperCase();
        update(name, direction, profile, averageRate, rateUnit);
    }

    public final void update(String name, CargoDirection direction, CargoCategory profile,
                             double averageRate, String rateUnit) {
        this.name = ModelValidation.requireText(name, "Nome da commodity");
        this.direction = ModelValidation.requireValue(direction, "Sentido");
        this.profile = ModelValidation.requireValue(profile, "Perfil de carga");
        this.averageRate = ModelValidation.requireRange(averageRate, 1, 500_000, "Taxa média diária");
        this.rateUnit = rateUnit == null || rateUnit.isBlank() ? TONS_PER_DAY : rateUnit.strip();
    }

    /** Só taxas em toneladas/dia servem para calcular a duração de estadia (RN004). */
    public boolean isTonnageRate() {
        return TONS_PER_DAY.equalsIgnoreCase(rateUnit);
    }

    /** RN004 — duração da estadia: D = ⌈toneladas / taxa diária⌉ (mínimo de 1 dia). */
    public int daysFor(long tonnage) {
        return (int) Math.max(1, Math.ceil(tonnage / averageRate));
    }

    /** Texto no formato do banco, ex.: "36.000 t/dia". */
    public String getRateText() {
        return String.format(Locale.forLanguageTag("pt-BR"), "%,.0f %s", averageRate, rateUnit);
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public CargoDirection getDirection() {
        return direction;
    }

    public CargoCategory getProfile() {
        return profile;
    }

    public double getAverageRate() {
        return averageRate;
    }

    public String getRateUnit() {
        return rateUnit;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CargoProduct other && other.code.equals(code);
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
