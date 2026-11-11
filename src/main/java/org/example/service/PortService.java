package org.example.service;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.example.dto.BerthRequest;
import org.example.dto.CargoProductRequest;
import org.example.dto.LineupKpis;
import org.example.dto.RecalculationResult;
import org.example.dto.ShipScheduleRequest;
import org.example.dto.VesselRequest;
import org.example.gateway.PortDataGateway;
import org.example.model.Berth;
import org.example.model.BusinessRuleException;
import org.example.model.CargoProduct;
import org.example.model.ModelValidation;
import org.example.model.RegisteredVessel;
import org.example.model.ScheduleConflictException;
import org.example.model.ShipSchedule;
import org.example.model.TideTable;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;

/**
 * Camada de controle/serviço do front-end (módulo Portos).
 * <p>
 * Expõe {@link ObservableList}s para as telas e centraliza as regras de negócio
 * (RN001–RN008, RN024). Toda persistência é delegada ao {@link PortDataGateway}:
 * hoje um mock em memória, na entrega final o cliente REST.
 */
public class PortService {

    /** RN002 — navios com calado a partir deste valor não atracam/desatracam em maré baixa crítica. */
    public static final double DEEP_DRAFT_LIMIT_M = 13.5;
    /** Mês do Lineup programado. */
    public static final YearMonth LINEUP_MONTH = YearMonth.of(2026, 11);

    private final PortDataGateway gateway;
    private final ObservableList<Berth> berths = FXCollections.observableArrayList();
    private final ObservableList<RegisteredVessel> vessels = FXCollections.observableArrayList();
    private final ObservableList<CargoProduct> cargoCatalog = FXCollections.observableArrayList();
    private final ObservableList<ShipSchedule> schedules = FXCollections.observableArrayList();
    private final ObservableList<TideTable> tideTables = FXCollections.observableArrayList();
    private final ObjectProperty<TideTable> activeTide = new SimpleObjectProperty<>();
    /** RN008 — incrementado a cada inclusão/edição/exclusão; o Lineup escuta e se reconstrói. */
    private final ReadOnlyIntegerWrapper dataVersion = new ReadOnlyIntegerWrapper(0);

    public PortService(PortDataGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway);
        activeTide.addListener((obs, oldTable, newTable) -> bump());
    }

    public void loadAll() {
        berths.setAll(gateway.fetchAllBerths());
        vessels.setAll(gateway.fetchAllVessels());
        cargoCatalog.setAll(gateway.fetchCargoCatalog());
        schedules.setAll(gateway.fetchAllSchedules());
        tideTables.setAll(gateway.fetchTideTables());
        TideTable active = gateway.fetchActiveTide();
        activeTide.set(active != null ? active : tideTables.stream()
                .filter(t -> t.getMonth().equals(LINEUP_MONTH)).findFirst()
                .orElse(tideTables.isEmpty() ? null : tideTables.getFirst()));
        bump();
    }

    // =================================================================== Berços (RF005/RF006)

    public Berth createBerth(BerthRequest r) {
        String code = ModelValidation.requireText(r.code(), "Código do berço").toUpperCase();
        if (findBerth(code).isPresent()) {
            throw new BusinessRuleException("HTTP 400", "Já existe um berço cadastrado com o código " + code + ".");
        }
        int order = berths.stream().mapToInt(Berth::getDisplayOrder).max().orElse(0) + 1;
        Berth berth = new Berth(code, r.name(), r.sectorName(), r.maxDraft(), r.category(), order);
        gateway.saveBerth(berth);
        berths.add(berth);
        bump();
        return berth;
    }

    public void updateBerth(Berth berth, BerthRequest r) {
        for (ShipSchedule s : berth.getSchedules()) {
            RegisteredVessel v = s.getVessel();
            if (r.category() != null && !r.category().accepts(v.getProfile())) {
                throw new BusinessRuleException("RN003", String.format(
                        "Não é possível mudar o perfil para \"%s\": o navio %s (%s) está programado neste berço.",
                        r.category(), v.getName(), v.getProfile()));
            }
            if (v.getMaxDraft() > r.maxDraft()) {
                throw new BusinessRuleException("RN003", String.format(
                        "Não é possível reduzir o calado para %.1f m: o navio %s exige %.1f m.",
                        r.maxDraft(), v.getName(), v.getMaxDraft()));
            }
        }
        berth.update(r.name(), r.sectorName(), r.maxDraft(), r.category());
        gateway.saveBerth(berth);
        touch(berths, berth);
    }

    /** RN024 — exclui o berço e, em cascata, todas as suas atracações. Retorna quantas foram removidas. */
    public int deleteBerth(Berth berth) {
        List<ShipSchedule> linked = berth.getSchedules();
        linked.forEach(this::removeScheduleInternal);
        gateway.deleteBerth(berth.getCode());
        berths.remove(berth);
        bump();
        return linked.size();
    }

    public Optional<Berth> findBerth(String code) {
        return berths.stream().filter(b -> b.getCode().equalsIgnoreCase(code)).findFirst();
    }

    // =================================================================== Navios (RF008/RF009)

    public RegisteredVessel createVessel(VesselRequest r) {
        String imo = ModelValidation.requireText(r.imoCode(), "Código IMO").toUpperCase();
        if (!RegisteredVessel.isValidImo(imo)) {
            throw new BusinessRuleException("RN007",
                    "Código IMO inválido: use o prefixo IMO- seguido de 7 dígitos (ex.: IMO-9810101).");
        }
        if (findVessel(imo).isPresent()) {
            throw new BusinessRuleException("RN007", "O código " + imo + " já está cadastrado (IMO deve ser único).");
        }
        RegisteredVessel v = new RegisteredVessel(imo, r.name(), r.vesselClass(), r.maxDraft(),
                r.defaultTonnage(), r.profile(), r.defaultUnloadProduct(), r.defaultLoadProduct());
        gateway.saveVessel(v);
        vessels.add(v);
        bump();
        return v;
    }

    public void updateVessel(RegisteredVessel v, VesselRequest r) {
        for (ShipSchedule s : schedulesOf(v)) {
            Berth b = s.getBerth();
            if (r.profile() != null && !b.getCategory().accepts(r.profile())) {
                throw new BusinessRuleException("RN003", "O navio está programado no berço " + b.getCode()
                        + " (" + b.getCategory() + "); o novo perfil seria incompatível.");
            }
            if (r.maxDraft() > b.getMaxDraft()) {
                throw new BusinessRuleException("RN003", "O navio está programado no berço " + b.getCode()
                        + " com calado máximo de " + b.getMaxDraft() + " m.");
            }
        }
        v.update(r.name(), r.vesselClass(), r.maxDraft(), r.defaultTonnage(), r.profile(),
                r.defaultUnloadProduct(), r.defaultLoadProduct());
        gateway.saveVessel(v);
        touch(vessels, v);
    }

    /** Exclui o navio e, em cascata, as suas atracações. */
    public int deleteVessel(RegisteredVessel v) {
        List<ShipSchedule> linked = schedulesOf(v);
        linked.forEach(this::removeScheduleInternal);
        gateway.deleteVessel(v.getImoCode());
        vessels.remove(v);
        bump();
        return linked.size();
    }

    public List<ShipSchedule> schedulesOf(RegisteredVessel v) {
        return schedules.stream().filter(s -> s.getVessel().equals(v)).toList();
    }

    public Optional<RegisteredVessel> findVessel(String imo) {
        return vessels.stream().filter(v -> v.getImoCode().equalsIgnoreCase(imo)).findFirst();
    }

    // =================================================================== Cargas (RF007)

    public CargoProduct createCargoProduct(CargoProductRequest r) {
        String code = ModelValidation.requireText(r.code(), "Código do produto").toUpperCase();
        if (cargoCatalog.stream().anyMatch(c -> c.getCode().equals(code))) {
            throw new BusinessRuleException("HTTP 400", "Já existe um produto com o código " + code + ".");
        }
        CargoProduct p = new CargoProduct(code, r.name(), r.direction(), r.profile(), r.averageRate(), r.rateUnit());
        gateway.saveCargoProduct(p);
        cargoCatalog.add(p);
        bump();
        return p;
    }

    /** Atualiza a commodity; se a taxa diária mudar, recalcula a duração das próximas atracações (RF007 - A1). */
    public RecalculationResult updateCargoProduct(CargoProduct p, CargoProductRequest r) {
        boolean rateChanged = Double.compare(p.getAverageRate(), r.averageRate()) != 0;
        p.update(r.name(), r.direction(), r.profile(), r.averageRate(), r.rateUnit());
        gateway.saveCargoProduct(p);
        RecalculationResult result = rateChanged ? recalculateFutureSchedules(p) : new RecalculationResult(0, 0);
        touch(cargoCatalog, p);
        return result;
    }

    /** Os produtos das atracações são texto livre (como no banco); o catálogo pode ser excluído livremente. */
    public void deleteCargoProduct(CargoProduct p) {
        gateway.deleteCargoProduct(p.getCode());
        cargoCatalog.remove(p);
        bump();
    }

    /** Commodity do catálogo cuja taxa (t/dia) rege a prancha de um berço do perfil informado (RN004). */
    public Optional<CargoProduct> rateProductFor(org.example.model.CargoCategory category) {
        return cargoCatalog.stream()
                .filter(c -> c.getProfile() == category && c.isTonnageRate())
                .findFirst();
    }

    /** Quantidade de atracações em berços do mesmo perfil da commodity. */
    public long schedulesUsingProfile(CargoProduct p) {
        return schedules.stream().filter(s -> s.getBerth().getCategory() == p.getProfile()).count();
    }

    private RecalculationResult recalculateFutureSchedules(CargoProduct p) {
        int today = currentOperationalDay();
        int updated = 0;
        int kept = 0;
        for (ShipSchedule s : List.copyOf(schedules)) {
            boolean governedByThis = rateProductFor(s.getBerth().getCategory()).map(p::equals).orElse(false);
            if (!governedByThis || s.getStartDay() <= today) {
                continue;
            }
            int newEnd = s.getStartDay() + p.daysFor(s.getTonnage());
            if (newEnd == s.getEndDay()) {
                continue;
            }
            boolean fits = newEnd <= Berth.MONTH_DAYS
                    && s.getBerth().isValidPeriod(s.getStartDay(), newEnd, s)
                    && !isTideBlocked(s.getVessel(), newEnd)
                    && isVesselFree(s.getVessel(), s.getStartDay(), newEnd, s);
            if (fits) {
                s.update(s.getVessel(), s.getStartDay(), newEnd, s.getTonnage(), s.getUnloadingProduct(),
                        s.getLoadingProduct(), s.getStatus());
                gateway.saveSchedule(s);
                updated++;
            } else {
                kept++;
            }
        }
        if (updated > 0) {
            schedules.setAll(List.copyOf(schedules));
        }
        return new RecalculationResult(updated, kept);
    }

    // =================================================================== Marés (RF010)

    public TideTable importTideCsv(Path file) throws IOException {
        TideTable table = TideCsvParser.parse(file);
        gateway.saveTideTable(table);
        tideTables.removeIf(t -> t.getMonth().equals(table.getMonth()));
        tideTables.add(table);
        activeTide.set(table);
        return table;
    }

    /** RN002 — calado ≥ 13,5 m em dia de maré baixa crítica (≤ 0,4 m) da tábua ativa. */
    public boolean isTideBlocked(RegisteredVessel v, int day) {
        TideTable t = activeTide.get();
        return t != null && v.getMaxDraft() >= DEEP_DRAFT_LIMIT_M && t.isCriticalDay(day);
    }

    /** Indica atracações existentes que ficaram irregulares após troca da tábua de marés. */
    public boolean violatesTide(ShipSchedule s) {
        return isTideBlocked(s.getVessel(), s.getStartDay()) || isTideBlocked(s.getVessel(), s.getEndDay());
    }

    // =================================================================== Atracações (RF011/RF012)

    public ShipSchedule createShipSchedule(ShipScheduleRequest r) {
        validateTideAndCollision(r, null);
        ShipSchedule s = new ShipSchedule(gateway.nextScheduleId(), r.berth(), r.vessel(), r.startDay(), r.endDay(),
                r.tonnage(), r.unloadingProduct(), r.loadingProduct(), r.status());
        r.berth().addSchedule(s);
        gateway.saveSchedule(s);
        schedules.add(s);
        bump();
        return s;
    }

    public ShipSchedule updateShipSchedule(ShipSchedule s, ShipScheduleRequest r, boolean supervisor) {
        checkHistoryLock(s, supervisor);
        validateTideAndCollision(r, s);
        if (!r.berth().equals(s.getBerth())) {
            // Mudança de berço: a atracação é recriada no novo berço mantendo o mesmo id.
            removeScheduleInternal(s);
            ShipSchedule moved = new ShipSchedule(s.getId(), r.berth(), r.vessel(), r.startDay(), r.endDay(),
                    r.tonnage(), r.unloadingProduct(), r.loadingProduct(), r.status());
            r.berth().addSchedule(moved);
            gateway.saveSchedule(moved);
            schedules.add(moved);
            bump();
            return moved;
        }
        s.update(r.vessel(), r.startDay(), r.endDay(), r.tonnage(), r.unloadingProduct(), r.loadingProduct(), r.status());
        gateway.saveSchedule(s);
        touch(schedules, s);
        return s;
    }

    public void deleteShipSchedule(ShipSchedule s, boolean supervisor) {
        checkHistoryLock(s, supervisor);
        removeScheduleInternal(s);
        bump();
    }

    /** Valida RN003 (perfil e calado), RN002 (maré) e RN001 (sobreposição) — nessa ordem. */
    public void validateTideAndCollision(ShipScheduleRequest r, ShipSchedule ignore) {
        Berth b = ModelValidation.requireValue(r.berth(), "Berço de atracação");
        RegisteredVessel v = ModelValidation.requireValue(r.vessel(), "Navio");
        ModelValidation.requireValue(r.status(), "Status");
        ShipSchedule.validatePeriod(r.startDay(), r.endDay());
        if (r.tonnage() <= 0) {
            throw new IllegalArgumentException("Informe a tonelagem total (t).");
        }
        if (!b.getCategory().accepts(v.getProfile())) {
            throw new BusinessRuleException("RN003", String.format(
                    "Perfil incompatível: o navio %s é de \"%s\" e o berço %s atende \"%s\".",
                    v.getName(), v.getProfile(), b.getCode(), b.getCategory()));
        }
        if (v.getMaxDraft() > b.getMaxDraft()) {
            throw new BusinessRuleException("RN003", String.format(
                    "O calado do navio (%.1f m) excede o calado máximo do berço %s (%.1f m).",
                    v.getMaxDraft(), b.getCode(), b.getMaxDraft()));
        }
        int duration = r.endDay() - r.startDay();
        if (isTideBlocked(v, r.startDay()) || isTideBlocked(v, r.endDay())) {
            throw new ScheduleConflictException("RN002", String.format(
                    "Bloqueio por maré baixa: o navio %s tem calado de %.1f m (≥ %.1f m) e a chegada/saída cai em dia "
                            + "de maré baixa crítica (dias %s da tábua %s).",
                    v.getName(), v.getMaxDraft(), DEEP_DRAFT_LIMIT_M, criticalDaysText(), activeTide.get()),
                    findAvailableSlot(b, v, duration, r.startDay(), ignore));
        }
        if (!b.isValidPeriod(r.startDay(), r.endDay(), ignore)) {
            String conflicts = b.getSchedules().stream().filter(s -> s != ignore)
                    .filter(s -> s.overlaps(r.startDay(), r.endDay()))
                    .map(s -> s.getVessel().getName() + " (dias " + s.getStartDay() + "–" + s.getEndDay() + ")")
                    .collect(Collectors.joining(", "));
            throw new ScheduleConflictException("RN001", "O berço " + b.getCode()
                    + " já está ocupado no período: " + conflicts + ".",
                    findAvailableSlot(b, v, duration, r.startDay(), ignore));
        }
        if (!isVesselFree(v, r.startDay(), r.endDay(), ignore)) {
            throw new ScheduleConflictException("RN001", "O navio " + v.getName()
                    + " já está programado em outro berço nesse período.",
                    findAvailableSlot(b, v, duration, r.startDay(), ignore));
        }
    }

    /** Busca o próximo slot vago (RF011 - E1) respeitando sobreposição, maré e disponibilidade do navio. */
    public OptionalInt findAvailableSlot(Berth b, RegisteredVessel v, int duration, int fromDay, ShipSchedule ignore) {
        java.util.function.IntPredicate ok = start -> !isTideBlocked(v, start)
                && !isTideBlocked(v, start + duration)
                && isVesselFree(v, start, start + duration, ignore);
        OptionalInt slot = b.findFirstAvailableSlot(duration, fromDay, ignore, ok);
        return slot.isPresent() ? slot : b.findFirstAvailableSlot(duration, 1, ignore, ok);
    }

    private boolean isVesselFree(RegisteredVessel v, int start, int end, ShipSchedule ignore) {
        return schedules.stream().filter(s -> s != ignore)
                .noneMatch(s -> s.getVessel().equals(v) && s.overlaps(start, end));
    }

    /** RN004 — duração sugerida em dias para a tonelagem e produto informados. */
    public OptionalInt suggestedDuration(long tonnage, Berth berth) {
        if (berth == null || tonnage <= 0) {
            return OptionalInt.empty();
        }
        return rateProductFor(berth.getCategory()).map(p -> OptionalInt.of(p.daysFor(tonnage)))
                .orElse(OptionalInt.empty());
    }

    /** Lista de produtos já usados (navios, atracações e catálogo) para sugestão nos formulários. */
    public List<String> knownProducts() {
        java.util.TreeSet<String> names = new java.util.TreeSet<>();
        cargoCatalog.forEach(c -> names.add(c.getName()));
        vessels.forEach(v -> {
            names.add(v.getDefaultUnloadProduct());
            names.add(v.getDefaultLoadProduct());
        });
        schedules.forEach(s -> {
            names.add(s.getUnloadingProduct());
            names.add(s.getLoadingProduct());
        });
        return List.copyOf(names);
    }

    /** RN006 — atracações concluídas só podem ser alteradas com perfil de supervisor auditor. */
    private void checkHistoryLock(ShipSchedule s, boolean supervisor) {
        int today = currentOperationalDay();
        if (!supervisor && s.isConcludedBefore(today)) {
            throw new BusinessRuleException("RN006", String.format(
                    "A atracação do navio %s terminou no dia %d (antes do dia operacional %d). "
                            + "Alterar o histórico exige perfil de supervisor auditor.",
                    s.getVessel().getName(), s.getEndDay(), today));
        }
    }

    /** Dia operacional atual dentro do mês do Lineup (0 antes do mês, 31 depois). */
    public int currentOperationalDay() {
        LocalDate today = LocalDate.now();
        YearMonth ym = YearMonth.from(today);
        if (ym.equals(LINEUP_MONTH)) {
            return today.getDayOfMonth();
        }
        return ym.isBefore(LINEUP_MONTH) ? 0 : Berth.MONTH_DAYS + 1;
    }

    private void removeScheduleInternal(ShipSchedule s) {
        s.getBerth().removeSchedule(s);
        schedules.remove(s);
        gateway.deleteSchedule(s.getId());
    }

    // =================================================================== KPIs (RF001)

    public LineupKpis computeKpis(List<Berth> visible) {
        int ships = visible.stream().mapToInt(b -> b.getSchedules().size()).sum();
        long tons = visible.stream().mapToLong(Berth::getTotalTonnage).sum();
        int occupied = visible.stream().mapToInt(Berth::getOccupiedDays).sum();
        double occupancy = visible.isEmpty() ? 0 : 100.0 * occupied / (visible.size() * (double) Berth.MONTH_DAYS);
        return new LineupKpis(visible.size(), berths.size(), ships, tons, occupancy);
    }

    public String criticalDaysText() {
        TideTable t = activeTide.get();
        if (t == null || t.getCriticalDays().isEmpty()) {
            return "nenhum";
        }
        return t.getCriticalDays().stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    // =================================================================== utilidades

    private <T> void touch(ObservableList<T> list, T item) {
        int i = list.indexOf(item);
        if (i >= 0) {
            list.set(i, item); // dispara evento de atualização para as TableViews
        }
        bump();
    }

    private void bump() {
        dataVersion.set(dataVersion.get() + 1);
    }

    public ObservableList<Berth> getBerths() {
        return berths;
    }

    public ObservableList<RegisteredVessel> getVessels() {
        return vessels;
    }

    public ObservableList<CargoProduct> getCargoCatalog() {
        return cargoCatalog;
    }

    public ObservableList<ShipSchedule> getSchedules() {
        return schedules;
    }

    public ObservableList<TideTable> getTideTables() {
        return tideTables;
    }

    public ObjectProperty<TideTable> activeTideProperty() {
        return activeTide;
    }

    public TideTable getActiveTide() {
        return activeTide.get();
    }

    public ReadOnlyIntegerProperty dataVersionProperty() {
        return dataVersion.getReadOnlyProperty();
    }
}
