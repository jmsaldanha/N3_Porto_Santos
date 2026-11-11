package org.example.dto;

import org.example.model.Berth;
import org.example.model.RegisteredVessel;
import org.example.model.ScheduleStatus;

/** Pedido de programação de atracação (RF011) — POST /api/port/berths/{id}/schedules. */
public record ShipScheduleRequest(Berth berth, RegisteredVessel vessel, int startDay, int endDay, long tonnage,
                                  String unloadingProduct, String loadingProduct, ScheduleStatus status) {

    public ShipScheduleRequest withStartDay(int newStart) {
        int duration = endDay - startDay;
        return new ShipScheduleRequest(berth, vessel, newStart, newStart + duration, tonnage,
                unloadingProduct, loadingProduct, status);
    }
}
