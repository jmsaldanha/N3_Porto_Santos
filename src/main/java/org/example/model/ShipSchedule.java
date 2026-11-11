package org.example.model;

import java.util.Objects;

/**
 * Atracação programada de um navio em um berço (RF011) — tabela {@code ship_schedules}.
 * O navio ocupa o berço no intervalo semiaberto [startDay, endDay).
 */
public class ShipSchedule {

    /** Texto usado pelo banco quando o navio não descarrega/carrega nada. */
    public static final String NO_PRODUCT = "Nenhum (Lastro)";

    private final long id;
    private final Berth berth;
    private RegisteredVessel vessel;
    private int startDay;
    private int endDay;
    private long tonnage;
    private String unloadingProduct;
    private String loadingProduct;
    private ScheduleStatus status;

    public ShipSchedule(long id, Berth berth, RegisteredVessel vessel, int startDay, int endDay, long tonnage,
                        String unloadingProduct, String loadingProduct, ScheduleStatus status) {
        this.id = id;
        this.berth = Objects.requireNonNull(berth, "berço");
        update(vessel, startDay, endDay, tonnage, unloadingProduct, loadingProduct, status);
    }

    public final void update(RegisteredVessel vessel, int startDay, int endDay, long tonnage,
                             String unloadingProduct, String loadingProduct, ScheduleStatus status) {
        validatePeriod(startDay, endDay);
        if (tonnage <= 0) {
            throw new IllegalArgumentException("A tonelagem deve ser maior que zero.");
        }
        String unload = normalizeProduct(unloadingProduct);
        String load = normalizeProduct(loadingProduct);
        if (isNone(unload) && isNone(load)) {
            throw new IllegalArgumentException("Informe ao menos um produto (descarga ↓ ou carga ↑).");
        }
        this.vessel = ModelValidation.requireValue(vessel, "Navio");
        this.status = ModelValidation.requireValue(status, "Status");
        this.startDay = startDay;
        this.endDay = endDay;
        this.tonnage = tonnage;
        this.unloadingProduct = unload;
        this.loadingProduct = load;
    }

    public static String normalizeProduct(String product) {
        return product == null || product.isBlank() ? NO_PRODUCT : product.strip();
    }

    public static boolean isNone(String product) {
        return product == null || product.isBlank() || product.strip().toLowerCase().startsWith("nenhum");
    }

    public static void validatePeriod(int startDay, int endDay) {
        if (startDay < 1 || endDay > Berth.MONTH_DAYS || endDay <= startDay) {
            throw new IllegalArgumentException(
                    "Período inválido: a chegada deve ser anterior à saída e ambas entre os dias 1 e 30.");
        }
    }

    public int getDurationDays() {
        return endDay - startDay;
    }

    public boolean overlaps(int otherStart, int otherEnd) {
        return !(endDay <= otherStart || otherEnd <= startDay);
    }

    /** RN006 — atracação já concluída em relação ao dia operacional informado. */
    public boolean isConcludedBefore(int currentDay) {
        return endDay < currentDay;
    }

    public String describe() {
        return String.format("%s | Berço %s | Dia %d ao Dia %d (%d dias) | ↓ %s | ↑ %s | Volume: %,d t | Status: %s",
                vessel.getName(), berth.getCode(), startDay, endDay, getDurationDays(),
                unloadingProduct, loadingProduct, tonnage, status.getLabel()).replace(',', '.');
    }

    public long getId() {
        return id;
    }

    public Berth getBerth() {
        return berth;
    }

    public RegisteredVessel getVessel() {
        return vessel;
    }

    public int getStartDay() {
        return startDay;
    }

    public int getEndDay() {
        return endDay;
    }

    public long getTonnage() {
        return tonnage;
    }

    public String getUnloadingProduct() {
        return unloadingProduct;
    }

    public String getLoadingProduct() {
        return loadingProduct;
    }

    public ScheduleStatus getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return vessel.getName() + " @ " + berth.getCode() + " [" + startDay + "-" + endDay + "]";
    }
}
