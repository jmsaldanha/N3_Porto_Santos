package org.example.dto;

import org.example.model.CargoCategory;

/** Dados do formulário de navio (RF008) — mesmos campos da tabela registered_vessels. */
public record VesselRequest(String imoCode, String name, String vesselClass, double maxDraft, long defaultTonnage,
                            CargoCategory profile, String defaultUnloadProduct, String defaultLoadProduct) {
}
