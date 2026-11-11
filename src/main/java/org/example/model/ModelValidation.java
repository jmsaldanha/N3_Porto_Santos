package org.example.model;

/** Validações reutilizadas pelas classes de domínio (encapsulamento com invariantes). */
public final class ModelValidation {

    private ModelValidation() {
    }

    public static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("O campo \"" + field + "\" é obrigatório.");
        }
        return value.strip();
    }

    public static <T> T requireValue(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Selecione um valor para \"" + field + "\".");
        }
        return value;
    }

    public static double requireRange(double value, double min, double max, String field) {
        if (Double.isNaN(value) || value < min || value > max) {
            throw new IllegalArgumentException(
                    String.format("\"%s\" deve estar entre %.1f e %.1f.", field, min, max));
        }
        return value;
    }
}
