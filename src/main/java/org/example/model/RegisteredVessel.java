package org.example.model;

import java.util.regex.Pattern;

/** Navio mercante cadastrado no catálogo (RF008) — tabela {@code registered_vessels}. */
public class RegisteredVessel {

    /** RN007 — prefixo "IMO-" seguido dos 7 dígitos do número IMO internacional. */
    private static final Pattern IMO_PATTERN = Pattern.compile("^IMO-\\d{7}$");

    private final String imoCode;
    private String name;
    private String vesselClass;
    private double maxDraft;
    private long defaultTonnage;
    private CargoCategory profile;
    private String defaultUnloadProduct;
    private String defaultLoadProduct;

    public RegisteredVessel(String imoCode, String name, String vesselClass, double maxDraft, long defaultTonnage,
                            CargoCategory profile, String defaultUnloadProduct, String defaultLoadProduct) {
        String imo = ModelValidation.requireText(imoCode, "Código IMO").toUpperCase();
        if (!isValidImo(imo)) {
            throw new BusinessRuleException("RN007",
                    "Código IMO inválido: use o prefixo IMO- seguido de 7 dígitos (ex.: IMO-9810101).");
        }
        this.imoCode = imo;
        update(name, vesselClass, maxDraft, defaultTonnage, profile, defaultUnloadProduct, defaultLoadProduct);
    }

    /** Atualiza o navio. Se o perfil vier nulo, ele é inferido pela classe de porte. */
    public final void update(String name, String vesselClass, double maxDraft, long defaultTonnage,
                             CargoCategory profile, String defaultUnloadProduct, String defaultLoadProduct) {
        this.name = ModelValidation.requireText(name, "Nome do navio");
        this.vesselClass = ModelValidation.requireText(vesselClass, "Classe");
        this.maxDraft = ModelValidation.requireRange(maxDraft, 3.0, 25.0, "Calado máximo (m)");
        if (defaultTonnage <= 0 || defaultTonnage > 500_000) {
            throw new IllegalArgumentException("A tonelagem padrão deve estar entre 1 e 500.000 t.");
        }
        this.defaultTonnage = defaultTonnage;
        this.profile = profile != null ? profile : CargoCategory.fromVesselClass(this.vesselClass);
        this.defaultUnloadProduct = ShipSchedule.normalizeProduct(defaultUnloadProduct);
        this.defaultLoadProduct = ShipSchedule.normalizeProduct(defaultLoadProduct);
    }

    public static boolean isValidImo(String code) {
        return code != null && IMO_PATTERN.matcher(code.strip().toUpperCase()).matches();
    }

    public String getImoCode() {
        return imoCode;
    }

    public String getName() {
        return name;
    }

    public String getVesselClass() {
        return vesselClass;
    }

    public double getMaxDraft() {
        return maxDraft;
    }

    public long getDefaultTonnage() {
        return defaultTonnage;
    }

    public CargoCategory getProfile() {
        return profile;
    }

    public String getDefaultUnloadProduct() {
        return defaultUnloadProduct;
    }

    public String getDefaultLoadProduct() {
        return defaultLoadProduct;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RegisteredVessel other && other.imoCode.equals(imoCode);
    }

    @Override
    public int hashCode() {
        return imoCode.hashCode();
    }

    @Override
    public String toString() {
        return name + " (" + imoCode + ")";
    }
}
