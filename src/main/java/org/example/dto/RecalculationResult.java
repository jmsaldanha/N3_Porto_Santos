package org.example.dto;

/** Resultado do recálculo de durações após alterar a taxa diária de uma carga (RF007 - A1). */
public record RecalculationResult(int recalculated, int keptByConflict) {
}
