package com.nitditto.service;

import java.util.UUID;

import com.nitditto.payload.request.SeatMapRequest;
import com.nitditto.payload.response.SeatMapResponse;

public interface SeatMapService {
    SeatMapResponse createSeatMap(UUID userId, SeatMapRequest seatMapRequest);

    SeatMapResponse getSeatMapById(UUID seatMapId);

    SeatMapResponse getSeatMapByCabinClassId(UUID cabinClassId);

    SeatMapResponse updateSeatMap(UUID seatMapId, SeatMapRequest seatMapRequest);

    void deleteSeatMap(UUID seatMapId);

}
