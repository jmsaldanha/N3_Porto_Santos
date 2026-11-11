package org.example.dto;

import org.example.model.CargoCategory;

/** Dados do formulário de berço (RF005) — equivalente ao corpo do POST /api/port/berths. */
public record BerthRequest(String code, String name, String sectorName, double maxDraft, CargoCategory category) {
}
