package org.example.gateway;

import org.example.model.Berth;
import org.example.model.CargoProduct;
import org.example.model.RegisteredVessel;
import org.example.model.ShipSchedule;
import org.example.model.TideTable;

import java.util.List;

/**
 * Contrato de acesso a dados do módulo Portos.
 * <p>
 * Checkpoint 1: implementado por {@link MockPortDataGateway} (dados em memória).
 * Entrega final: será implementado por um {@code BackendApiClient} que faz as chamadas
 * HTTP REST/JSON ao Spring Boot (RN022) — sem alterar nenhuma tela.
 */
public interface PortDataGateway {

    List<Berth> fetchAllBerths();                 // GET    /api/port/berths

    void saveBerth(Berth berth);                  // POST / PUT /api/port/berths

    void deleteBerth(String code);                // DELETE /api/port/berths/{code}

    List<RegisteredVessel> fetchAllVessels();     // GET    /api/port/vessels

    void saveVessel(RegisteredVessel vessel);

    void deleteVessel(String imoCode);

    List<CargoProduct> fetchCargoCatalog();       // GET    /api/port/cargos

    void saveCargoProduct(CargoProduct product);

    void deleteCargoProduct(String code);

    List<TideTable> fetchTideTables();            // GET    /api/port/tides

    void saveTideTable(TideTable table);

    TideTable fetchActiveTide();                  // GET    /api/port/tides/active

    List<ShipSchedule> fetchAllSchedules();       // GET    /api/port/schedules

    void saveSchedule(ShipSchedule schedule);     // POST   /api/port/berths/{id}/schedules

    void deleteSchedule(long id);                 // DELETE /api/port/schedules/{id}

    long nextScheduleId();
}
