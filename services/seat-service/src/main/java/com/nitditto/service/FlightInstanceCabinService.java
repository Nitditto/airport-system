package com.nitditto.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.nitditto.payload.request.FlightInstanceCabinRequest;
import com.nitditto.payload.response.FlightInstanceCabinResponse;

public interface FlightInstanceCabinService {
  FlightInstanceCabinResponse createFlightInstanceCabin(FlightInstanceCabinRequest request);

  FlightInstanceCabinResponse getFlightInstanceCabinById(UUID id);

  Page<FlightInstanceCabinResponse> getByFlightInstanceId(UUID flightInstanceId, Pageable pageable);

  FlightInstanceCabinResponse getByFlightInstanceIdAndCabinClassId(UUID flightInstanceId, UUID cabinClassId);

  FlightInstanceCabinResponse updateFlightInstanceCabin(UUID id, FlightInstanceCabinRequest request);

  void deleteFlightInstanceCabin(UUID id);
}
