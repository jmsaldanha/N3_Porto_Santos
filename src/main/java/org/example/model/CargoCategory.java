package org.example.model;

import java.util.Arrays;

/**
 * Os 11 perfis de carga oficiais da APS (RN003). Os nomes das constantes são exatamente
 * os códigos gravados na coluna {@code category} da tabela {@code berths} do banco lineup_santos.
 */
public enum CargoCategory {
    SOLID_VEG("Granéis Sólidos Vegetais", "#16a34a"),
    CONTAINERS("Contêineres", "#2563eb"),
    PULP("Celulose", "#a16207"),
    LIQUID_CHEM("Líquidos Químicos/Combustíveis", "#7c3aed"),
    STEEL("Produtos Siderúrgicos", "#475569"),
    MINERAL("Granéis Minerais", "#b45309"),
    CITRUS("Sucos Cítricos", "#ea580c"),
    MULTIPURPOSE("Multipropósito", "#0891b2"),
    VEHICLES("Veículos (Ro-Ro)", "#db2777"),
    PASSENGERS("Passageiros (Cruzeiros)", "#0d9488"),
    OFFSHORE("Carga Offshore", "#334155");

    private final String label;
    private final String color;

    CargoCategory(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public static CargoCategory fromCode(String code) {
        return Arrays.stream(values()).filter(c -> c.name().equalsIgnoreCase(code.strip())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Perfil de carga desconhecido: " + code));
    }

    /**
     * Infere o perfil de carga de um navio pela classe (a tabela registered_vessels
     * não possui coluna de perfil). Ex.: "Post-Panamax Porta-Contêineres" → CONTAINERS.
     */
    public static CargoCategory fromVesselClass(String vesselClass) {
        String c = vesselClass.toLowerCase();
        if (c.contains("contêiner") || c.contains("container")) return CONTAINERS;
        if (c.contains("celulose")) return PULP;
        if (c.contains("suco") || c.contains("cítric")) return CITRUS;
        if (c.contains("tanque") || c.contains("tanker")) return LIQUID_CHEM;
        if (c.contains("sider")) return STEEL;
        if (c.contains("mineral")) return MINERAL;
        if (c.contains("ro-ro") || c.contains("pctc")) return VEHICLES;
        if (c.contains("cruzeiro") || c.contains("passageir")) return PASSENGERS;
        if (c.contains("offshore") || c.contains("psv") || c.contains("ahts")) return OFFSHORE;
        if (c.contains("multipurpose") || c.contains("multipropósito") || c.contains("general cargo")) return MULTIPURPOSE;
        return SOLID_VEG;
    }

    public String getLabel() {
        return label;
    }

    public String getColor() {
        return color;
    }

    /**
     * RN003 — um berço só aceita navios do mesmo perfil de carga.
     * Berços multipropósito aceitam qualquer carga, exceto passageiros.
     */
    public boolean accepts(CargoCategory vesselProfile) {
        return switch (this) {
            case MULTIPURPOSE -> vesselProfile != PASSENGERS;
            default -> this == vesselProfile;
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
