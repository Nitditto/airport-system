package com.nitditto.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.nitditto.payload.request.FlightInstanceCabinRequest;
import com.nitditto.payload.response.FlightInstanceCabinResponse;

public interface FlightInstanceCabinService {
  FlightInstanceCabinResponse createFlightInstanceCabin(FlightInstanceCabinRequest request);

  /**
   * Sinh FlightInstanceCabin + SeatInstance cho mọi cabin class (có seat map và ghế) của tàu bay. Idempotent: cabin
   * class đã có FlightInstanceCabin thì bỏ qua. Trả về số seat instance đã tạo.
   */
  int generateForFlightInstance(UUID flightInstanceId, UUID flightId, UUID aircraftId);

  FlightInstanceCabinResponse getFlightInstanceCabinById(UUID id);

  Page<FlightInstanceCabinResponse> getByFlightInstanceId(UUID flightInstanceId, Pageable pageable);

  // requesterId (có thể null): ghế đang bị NGƯỜI KHÁC giữ chỗ được trả về với status HELD.
  FlightInstanceCabinResponse getByFlightInstanceIdAndCabinClassId(UUID flightInstanceId, UUID cabinClassId,
      UUID requesterId);

  FlightInstanceCabinResponse updateFlightInstanceCabin(UUID id, FlightInstanceCabinRequest request);

  void deleteFlightInstanceCabin(UUID id);
}
