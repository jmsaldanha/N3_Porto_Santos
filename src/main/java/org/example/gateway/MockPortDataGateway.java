package org.example.gateway;

import org.example.mock.SantosPortDataFactory.MockDataset;
import org.example.model.Berth;
import org.example.model.CargoProduct;
import org.example.model.RegisteredVessel;
import org.example.model.ShipSchedule;
import org.example.model.TideTable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Simula o backend guardando em memória os registros do banco lineup_santos (sem conexão com o PostgreSQL). */
public class MockPortDataGateway implements PortDataGateway {

    private final Map<String, Berth> berths = new LinkedHashMap<>();
    private final Map<String, RegisteredVessel> vessels = new LinkedHashMap<>();
    private final Map<String, CargoProduct> cargos = new LinkedHashMap<>();
    private final Map<String, TideTable> tides = new LinkedHashMap<>();
    private final Map<Long, ShipSchedule> schedules = new LinkedHashMap<>();
    private final AtomicLong scheduleSequence;
    private final TideTable activeTide;

    public MockPortDataGateway(MockDataset dataset) {
        dataset.berths().forEach(b -> berths.put(b.getCode(), b));
        dataset.vessels().forEach(v -> vessels.put(v.getImoCode(), v));
        dataset.cargoCatalog().forEach(c -> cargos.put(c.getCode(), c));
        dataset.tideTables().forEach(t -> tides.put(t.getMonth().toString(), t));
        dataset.schedules().forEach(s -> schedules.put(s.getId(), s));
        activeTide = dataset.activeTide();
        long maxId = schedules.keySet().stream().mapToLong(Long::longValue).max().orElse(0);
        scheduleSequence = new AtomicLong(maxId);
    }

    @Override
    public List<Berth> fetchAllBerths() {
        return new ArrayList<>(berths.values());
    }

    @Override
    public void saveBerth(Berth berth) {
        berths.put(berth.getCode(), berth);
    }

    @Override
    public void deleteBerth(String code) {
        berths.remove(code);
        schedules.values().removeIf(s -> s.getBerth().getCode().equals(code)); // RN024 cascata
    }

    @Override
    public List<RegisteredVessel> fetchAllVessels() {
        return new ArrayList<>(vessels.values());
    }

    @Override
    public void saveVessel(RegisteredVessel vessel) {
        vessels.put(vessel.getImoCode(), vessel);
    }

    @Override
    public void deleteVessel(String imoCode) {
        vessels.remove(imoCode);
    }

    @Override
    public List<CargoProduct> fetchCargoCatalog() {
        return new ArrayList<>(cargos.values());
    }

    @Override
    public void saveCargoProduct(CargoProduct product) {
        cargos.put(product.getCode(), product);
    }

    @Override
    public void deleteCargoProduct(String code) {
        cargos.remove(code);
    }

    @Override
    public List<TideTable> fetchTideTables() {
        return new ArrayList<>(tides.values());
    }

    @Override
    public void saveTideTable(TideTable table) {
        tides.put(table.getMonth().toString(), table);
    }

    @Override
    public TideTable fetchActiveTide() {
        return activeTide;
    }

    @Override
    public List<ShipSchedule> fetchAllSchedules() {
        return new ArrayList<>(schedules.values());
    }

    @Override
    public void saveSchedule(ShipSchedule schedule) {
        schedules.put(schedule.getId(), schedule);
    }

    @Override
    public void deleteSchedule(long id) {
        schedules.remove(id);
    }

    @Override
    public long nextScheduleId() {
        return scheduleSequence.incrementAndGet();
    }
}
