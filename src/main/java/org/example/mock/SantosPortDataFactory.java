package org.example.mock;

import org.example.model.Berth;
import org.example.model.CargoCategory;
import org.example.model.CargoDirection;
import org.example.model.CargoProduct;
import org.example.model.RegisteredVessel;
import org.example.model.ScheduleStatus;
import org.example.model.ShipSchedule;
import org.example.model.TideMeasurement;
import org.example.model.TideTable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fábrica de dados mock do Checkpoint 1.
 * <p>
 * Os dados NÃO são inventados: são lidos do script oficial do professor
 * ({@code schema_and_data_postgres.sql}, banco lineup_santos) embutido em
 * {@code src/main/resources/org/example/seed/}. São carregadas apenas as tabelas do módulo
 * Portos: berths, ship_schedules, registered_vessels, port_cargo_catalog,
 * tide_tables e tide_measurements.
 */
public final class SantosPortDataFactory {

    public static final String SEED_RESOURCE = "/org/example/seed/schema_and_data_postgres.sql";

    public record MockDataset(List<Berth> berths, List<RegisteredVessel> vessels, List<CargoProduct> cargoCatalog,
                              List<TideTable> tideTables, TideTable activeTide, List<ShipSchedule> schedules) {
    }

    private SantosPortDataFactory() {
    }

    public static MockDataset create() {
        try (InputStream in = SantosPortDataFactory.class.getResourceAsStream(SEED_RESOURCE)) {
            Objects.requireNonNull(in, "Script do banco não encontrado em " + SEED_RESOURCE);
            return fromSql(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static MockDataset fromSql(String sql) {
        SqlSeedReader seed = new SqlSeedReader(sql);

        List<CargoProduct> catalog = new ArrayList<>();
        for (Map<String, String> r : seed.rows("port_cargo_catalog")) {
            String rate = r.get("average_rate_tons_day");               // ex.: "36.000 t/dia"
            String number = rate.replaceAll("[^0-9,]", "").replace(',', '.');
            String unit = rate.replaceAll("^[0-9.,\\s]+", "").strip();
            catalog.add(new CargoProduct(r.get("product_code"), r.get("product_name"),
                    CargoDirection.fromText(r.get("operation_direction")),
                    CargoCategory.fromCode(r.get("cargo_profile")), Double.parseDouble(number), unit));
        }

        List<Berth> berths = new ArrayList<>();
        Map<String, Berth> berthById = new HashMap<>();
        for (Map<String, String> r : seed.rows("berths")) {
            Berth b = new Berth(r.get("id"), r.get("name"), r.get("sector_name"), meters(r.get("max_draft")),
                    CargoCategory.fromCode(r.get("category")), Integer.parseInt(r.get("display_order")));
            berths.add(b);
            berthById.put(b.getCode(), b);
        }
        berths.sort(Comparator.comparingInt(Berth::getDisplayOrder));

        List<RegisteredVessel> vessels = new ArrayList<>();
        Map<String, RegisteredVessel> vesselByName = new HashMap<>();
        for (Map<String, String> r : seed.rows("registered_vessels")) {
            RegisteredVessel v = new RegisteredVessel(r.get("imo_code"), r.get("ship_name"), r.get("vessel_class"),
                    meters(r.get("max_draft_meters")), Long.parseLong(r.get("default_tonnage")), null,
                    r.get("default_unload_product"), r.get("default_load_product"));
            vessels.add(v);
            vesselByName.put(v.getName(), v);
        }

        List<ShipSchedule> schedules = new ArrayList<>();
        long id = 1;
        for (Map<String, String> r : seed.rows("ship_schedules")) {
            Berth berth = berthById.get(r.get("berth_id"));
            RegisteredVessel vessel = vesselByName.get(r.get("ship_name"));
            if (berth == null || vessel == null) {
                continue; // registro órfão: ignorado
            }
            ShipSchedule s = new ShipSchedule(id++, berth, vessel,
                    (int) Math.round(Double.parseDouble(r.get("start_day"))),
                    (int) Math.round(Double.parseDouble(r.get("end_day"))),
                    Long.parseLong(r.get("tonnage")), r.get("unloading_product"), r.get("loading_product"),
                    ScheduleStatus.fromLabel(r.get("status")));
            berth.addSchedule(s);
            schedules.add(s);
        }

        Map<String, List<TideMeasurement>> measurementsByTable = new HashMap<>();
        Map<String, YearMonth> monthByTable = new LinkedHashMap<>();
        Map<String, String> sourceByTable = new HashMap<>();
        String activeId = null;
        for (Map<String, String> r : seed.rows("tide_tables")) {
            monthByTable.put(r.get("id"), parseMonthLabel(r.get("month_label")));
            sourceByTable.put(r.get("id"), "Banco lineup_santos — " + r.get("source_file_name"));
            if ("TRUE".equalsIgnoreCase(r.get("active_month"))) {
                activeId = r.get("id");
            }
        }
        for (Map<String, String> r : seed.rows("tide_measurements")) {
            String tableId = r.get("tide_table_id");
            YearMonth ym = monthByTable.get(tableId);
            if (ym == null) {
                continue;
            }
            LocalDate date = ym.atDay(Integer.parseInt(r.get("day_number")));
            measurementsByTable.computeIfAbsent(tableId, k -> new ArrayList<>()).add(new TideMeasurement(
                    date.atTime(LocalTime.parse(r.get("time_str"))), Double.parseDouble(r.get("height_meters"))));
        }
        List<TideTable> tides = new ArrayList<>();
        TideTable active = null;
        for (var e : monthByTable.entrySet()) {
            List<TideMeasurement> ms = measurementsByTable.get(e.getKey());
            if (ms == null || ms.isEmpty()) {
                continue;
            }
            TideTable t = new TideTable(e.getValue(), sourceByTable.get(e.getKey()), ms);
            tides.add(t);
            if (e.getKey().equals(activeId)) {
                active = t;
            }
        }
        return new MockDataset(berths, vessels, catalog, tides, active, schedules);
    }

    /** "14.2m" → 14.2 */
    private static double meters(String text) {
        return Double.parseDouble(text.toLowerCase().replace("m", "").replace(',', '.').strip());
    }

    /** "Novembro / 2026" → 2026-11 */
    static YearMonth parseMonthLabel(String label) {
        String[] parts = label.split("/");
        String month = parts[0].strip().toLowerCase();
        int year = Integer.parseInt(parts[1].replaceAll("\\D", ""));
        String[] names = {"janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto",
                "setembro", "outubro", "novembro", "dezembro"};
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(month)) {
                return YearMonth.of(year, i + 1);
            }
        }
        throw new IllegalArgumentException("Mês inválido na tábua de marés: " + label);
    }
}
