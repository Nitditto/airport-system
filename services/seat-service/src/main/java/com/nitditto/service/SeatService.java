package com.nitditto.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.nitditto.payload.request.SeatRequest;
import com.nitditto.payload.response.SeatResponse;

public interface SeatService {

    void generateSeats(UUID seatMapId);

    Page<SeatResponse> getSeatsBySeatMapId(UUID seatMapId, Pageable pageable);

    SeatResponse getSeatById(UUID seatId);

    SeatResponse updateSeat(UUID seatId, SeatRequest seatRequest);
}
