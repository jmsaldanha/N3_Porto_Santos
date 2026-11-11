package org.example.dto;

/** Indicadores globais exibidos no topo do Lineup (RF001). */
public record LineupKpis(int visibleBerths, int totalBerths, int scheduledShips, long totalTonnage,
                         double occupancyPercent) {
}
