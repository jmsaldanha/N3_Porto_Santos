package org.example.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.IntPredicate;

/**
 * Berço de atracação do cais (RF005). Mantém a lista das suas atracações e
 * garante a regra anti-sobreposição temporal (RN001).
 */
public class Berth {

    /** O lineup é mensal: dias 1 a 30. */
    public static final int MONTH_DAYS = 30;

    private final String code;
    private String name;
    private String sectorName;
    private double maxDraft;
    private CargoCategory category;
    /** Ordem de exibição no Lineup (coluna display_order). */
    private final int displayOrder;
    private final List<ShipSchedule> schedules = new ArrayList<>();

    public Berth(String code, String name, String sectorName, double maxDraft, CargoCategory category,
                 int displayOrder) {
        this.code = ModelValidation.requireText(code, "Código do berço").toUpperCase();
        this.displayOrder = displayOrder;
        update(name, sectorName, maxDraft, category);
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    /** Atualiza os dados editáveis do berço (o código é a identidade e não muda). */
    public final void update(String name, String sectorName, double maxDraft, CargoCategory category) {
        this.name = ModelValidation.requireText(name, "Nome do berço");
        this.sectorName = ModelValidation.requireText(sectorName, "Setor / Região");
        this.maxDraft = ModelValidation.requireRange(maxDraft, 5.0, 25.0, "Calado máximo (m)");
        this.category = ModelValidation.requireValue(category, "Perfil de carga");
    }

    /**
     * RN001 — verifica se o intervalo [start, end) está livre neste berço.
     * Condição formal: (FA <= IB) ou (FB <= IA) para toda atracação existente.
     *
     * @param ignore atracação a desconsiderar (usada na edição); pode ser null
     */
    public boolean isValidPeriod(int start, int end, ShipSchedule ignore) {
        return schedules.stream()
                .filter(s -> s != ignore)
                .allMatch(s -> s.getEndDay() <= start || end <= s.getStartDay());
    }

    /** Primeiro dia de chegada livre a partir de {@code fromDay} para a duração informada. */
    public OptionalInt findFirstAvailableSlot(int durationDays, int fromDay, ShipSchedule ignore,
                                              IntPredicate extraCheck) {
        for (int start = Math.max(1, fromDay); start + durationDays <= MONTH_DAYS; start++) {
            if (isValidPeriod(start, start + durationDays, ignore) && extraCheck.test(start)) {
                return OptionalInt.of(start);
            }
        }
        return OptionalInt.empty();
    }

    public void addSchedule(ShipSchedule schedule) {
        Objects.requireNonNull(schedule);
        if (schedule.getBerth() != this) {
            throw new IllegalArgumentException("A atracação pertence a outro berço.");
        }
        if (!isValidPeriod(schedule.getStartDay(), schedule.getEndDay(), schedule)) {
            throw new BusinessRuleException("RN001", "Sobreposição de atracações no berço " + code + ".");
        }
        if (!schedules.contains(schedule)) {
            schedules.add(schedule);
        }
    }

    public void removeSchedule(ShipSchedule schedule) {
        schedules.remove(schedule);
    }

    /** Atracações ordenadas por dia de chegada (cópia somente leitura). */
    public List<ShipSchedule> getSchedules() {
        return schedules.stream().sorted(Comparator.comparingInt(ShipSchedule::getStartDay)).toList();
    }

    public long getTotalTonnage() {
        return schedules.stream().mapToLong(ShipSchedule::getTonnage).sum();
    }

    public int getOccupiedDays() {
        return schedules.stream().mapToInt(ShipSchedule::getDurationDays).sum();
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getSectorName() {
        return sectorName;
    }

    public double getMaxDraft() {
        return maxDraft;
    }

    public CargoCategory getCategory() {
        return category;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Berth other && other.code.equals(code);
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public String toString() {
        return code + " — " + name;
    }
}
