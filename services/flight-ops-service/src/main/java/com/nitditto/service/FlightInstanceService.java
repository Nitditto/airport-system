package com.nitditto.service;

import com.nitditto.payload.request.FlightInstanceRequest;
import com.nitditto.payload.response.FlightInstanceResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FlightInstanceService {

  FlightInstanceResponse createFlightInstance(UUID userId,
      FlightInstanceRequest flightInstanceRequest);

  void createFlightInstances(UUID airlineId, List<FlightInstanceRequest> flightInstanceRequests);

  FlightInstanceResponse getFlightInstanceById(UUID id);

  Page<FlightInstanceResponse> getByAirlineId(UUID userId,
      UUID departureAirportId,
      UUID arrivalAirportId,
      UUID flightId,
      LocalDate onDate,
      Pageable pageable);

  FlightInstanceResponse updateFlightInstance(UUID id, FlightInstanceRequest flightInstanceRequest);

  void deleteFlightInstance(UUID id);
}
