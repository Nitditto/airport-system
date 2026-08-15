package com.nitditto.service;

import com.nitditto.enums.FlightStatus;
import com.nitditto.payload.request.FlightRequest;
import com.nitditto.payload.response.FlightResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FlightService {

  FlightResponse createFlight(Long airlineId, FlightRequest flightRequest) throws Exception;

  Page<FlightResponse> getFlightsByAirline(Long airlineId, Long departureAirportId,
      Long arrivalAirportId, Pageable pageable);

  FlightResponse getFlightById(Long id) throws Exception;

  FlightResponse updateFlight(Long id, FlightRequest flightRequest) throws Exception;

  FlightResponse changeStatus(Long id, FlightStatus flightStatus) throws Exception;

  void deleteFlight(Long airlineId, Long id) throws Exception;
}
