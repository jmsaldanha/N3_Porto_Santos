package org.example.model;

/**
 * Status operacional de uma atracação. As cores seguem a RN005:
 * vermelho (#e74c3c) para Operando/Prioridade, laranja (#e67e22) para Programado
 * e verde (#2ecc71) para Confirmado.
 */
public enum ScheduleStatus {
    OPERATING("Operando", "#e74c3c"),
    PRIORITY("Prioridade", "#e74c3c"),
    SCHEDULED("Programado", "#e67e22"),
    CONFIRMED("Confirmado", "#2ecc71");

    private final String label;
    private final String color;

    ScheduleStatus(String label, String color) {
        this.label = label;
        this.color = color;
    }

    /** Converte o texto da coluna status ("Operando", "Prioridade", "Programado", "Confirmado"). */
    public static ScheduleStatus fromLabel(String text) {
        for (ScheduleStatus s : values()) {
            if (s.label.equalsIgnoreCase(text.strip())) {
                return s;
            }
        }
        throw new IllegalArgumentException("Status de atracação desconhecido: " + text);
    }

    public String getLabel() {
        return label;
    }

    public String getColor() {
        return color;
    }

    @Override
    public String toString() {
        return label;
    }
}
