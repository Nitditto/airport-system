package com.nitditto.service.impl;

import com.nitditto.enums.FlightStatus;
import com.nitditto.mapper.FlightMapper;
import com.nitditto.model.Flight;
import com.nitditto.payload.response.AircraftResponse;
import com.nitditto.payload.response.AirlineResponse;
import com.nitditto.payload.response.AirportResponse;
import com.nitditto.repository.FlightRepository;
import com.nitditto.service.FlightService;

import lombok.RequiredArgsConstructor;

import com.nitditto.payload.request.FlightRequest;
import com.nitditto.payload.response.FlightResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FlightServiceImpl implements FlightService {

  private final FlightRepository flightRepository;

  @Transactional
  @Override
  public FlightResponse createFlight(Long airlineId, FlightRequest flightRequest) throws Exception {

    // todo: watch airlineId
    if (flightRepository.existsByFlightNumber(flightRequest.getFlightNumber())) {
      throw new Exception("flight with already exist");
    }
    Flight flight = FlightMapper.toEntity(flightRequest);
    flight.setAirlineId(airlineId);
    Flight savedFlight = flightRepository.save(flight);

    return convertToFLightResponse(savedFlight);
  }

  @Override
  public Page<FlightResponse> getFlightsByAirline(Long airlineId, Long departureAirportId,
      Long arrivalAirportId, Pageable pageable) {
    // todo: watch airlineId
    return flightRepository.findByAirlineId(airlineId,
        departureAirportId,
        arrivalAirportId,
        pageable).map(this::convertToFLightResponse);
  }

  @Override
  public FlightResponse getFlightById(Long id) throws Exception {
    Flight flight = flightRepository.findById(id).orElseThrow(
        () -> new Exception("flight not found with id")
    );

    return convertToFLightResponse(flight);
  }

  @Transactional
  @Override
  public FlightResponse updateFlight(Long id, FlightRequest flightRequest) throws Exception {
    Flight existingFlight = flightRepository.findById(id).orElseThrow(
        () -> new Exception("flight not found with id")
    );
    if (flightRequest.getFlightNumber() != null && flightRepository.existsByFlightNumberAndIdNot(
        flightRequest.getFlightNumber(), id)) {
      throw new Exception("flight with already exist");
    }

    FlightMapper.updateEntity(flightRequest, existingFlight);
    Flight savedFlight = flightRepository.save(existingFlight);
    return convertToFLightResponse(savedFlight);
  }

  @Transactional
  @Override
  public FlightResponse changeStatus(Long id, FlightStatus flightStatus) throws Exception {

    Flight existingFlight = flightRepository.findById(id).orElseThrow(
        () -> new Exception("flight not found with id")
    );
    existingFlight.setStatus(flightStatus);
    Flight updated = flightRepository.save(existingFlight);
    return convertToFLightResponse(updated);
  }

  @Transactional
  @Override
  public void deleteFlight(Long airlineId, Long id) throws Exception {

    // todo: watch airlineId
    Flight existingFlight = flightRepository.findByAirlineIdAndId(airlineId, id).orElseThrow(
        () -> new Exception("flight not found with id")
    );

    flightRepository.delete(existingFlight);
  }

  public FlightResponse convertToFLightResponse(Flight flight) {

    // todo: service to service communication
    AircraftResponse aircraftResponse = AircraftResponse.builder().id(flight.getAircraftId())
        .build();
    AirlineResponse airlineResponse = AirlineResponse.builder().id(flight.getAirlineId()).build();
    AirportResponse departureAirport = AirportResponse.builder().id(flight.getDepartureAirportId())
        .build();
    AirportResponse arrivalAirport = AirportResponse.builder().id(flight.getArrivalAirportId())
        .build();
    return FlightMapper.toResponse(flight, aircraftResponse, airlineResponse, departureAirport,
        arrivalAirport);
  }
}
