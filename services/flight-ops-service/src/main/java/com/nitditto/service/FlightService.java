package com.nitditto.service;

import com.nitditto.enums.FlightStatus;
import com.nitditto.payload.request.FlightRequest;
import com.nitditto.payload.response.FlightResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FlightService {

  FlightResponse createFlight(UUID userId, FlightRequest flightRequest);

  Page<FlightResponse> getFlightsByAirline(UUID userId, UUID departureAirportId,
      UUID arrivalAirportId, Pageable pageable);

  FlightResponse getFlightById(UUID id);

  FlightResponse updateFlight(UUID id, FlightRequest flightRequest);

  FlightResponse changeStatus(UUID id, FlightStatus flightStatus);

  void deleteFlight(UUID userId, UUID id);
}
