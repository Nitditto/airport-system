package com.nitditto.service;

import com.nitditto.payload.request.FlightInstanceRequest;
import com.nitditto.payload.response.FlightInstanceResponse;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FlightInstanceService {

  FlightInstanceResponse createFlightInstance(UUID airlineId,
      FlightInstanceRequest flightInstanceRequest);

  FlightInstanceResponse getFlightInstanceById(UUID id);

  Page<FlightInstanceResponse> getByAirlineId(UUID airlineId,
      UUID departureAirportId,
      UUID arrivalAirportId,
      UUID flightId,
      LocalDate onDate,
      Pageable pageable);

  FlightInstanceResponse updateFlightInstance(UUID id, FlightInstanceRequest flightInstanceRequest);

  void deleteFlightInstance(UUID id);
}
