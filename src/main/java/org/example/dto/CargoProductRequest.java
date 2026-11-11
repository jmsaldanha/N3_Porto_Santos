package org.example.dto;

import org.example.model.CargoCategory;
import org.example.model.CargoDirection;

/** Dados do formulário do catálogo de cargas (RF007) — tabela port_cargo_catalog. */
public record CargoProductRequest(String code, String name, CargoDirection direction, CargoCategory profile,
                                  double averageRate, String rateUnit) {
}
