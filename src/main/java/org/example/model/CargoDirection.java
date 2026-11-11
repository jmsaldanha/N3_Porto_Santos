package org.example.model;

/** Sentido operacional de uma commodity no porto (RF007). */
public enum CargoDirection {
    DISCHARGE("↓ Descarga"),
    LOAD("↑ Carga"),
    MIXED("↓↑ Descarga & Carga");

    private final String label;

    CargoDirection(String label) {
        this.label = label;
    }

    /** Converte o texto gravado no banco ("⬇ Descarga", "⬆ Carga", "⬇⬆ Descarga & Carga"). */
    public static CargoDirection fromText(String text) {
        boolean down = text.contains("⬇") || text.contains("Descarga");
        boolean up = text.contains("⬆") || text.replace("Descarga", "").contains("Carga");
        if (down && up) {
            return MIXED;
        }
        return down ? DISCHARGE : LOAD;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
