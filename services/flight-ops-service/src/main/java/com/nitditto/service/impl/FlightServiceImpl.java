package com.nitditto.service.impl;

import com.nitditto.client.AirlineClient;
import com.nitditto.client.LocationClient;
import com.nitditto.enums.FlightStatus;
import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.mapper.FlightMapper;
import com.nitditto.model.Flight;
import com.nitditto.payload.request.FlightRequest;
import com.nitditto.payload.response.AircraftResponse;
import com.nitditto.payload.response.AirlineResponse;
import com.nitditto.payload.response.AirportResponse;
import com.nitditto.payload.response.FlightResponse;
import com.nitditto.repository.FlightInstanceRepository;
import com.nitditto.repository.FlightRepository;
import com.nitditto.repository.FlightScheduleRepository;
import com.nitditto.service.FlightService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlightServiceImpl implements FlightService {

  private final FlightRepository flightRepository;
  private final FlightScheduleRepository flightScheduleRepository;
  private final FlightInstanceRepository flightInstanceRepository;
  private final AirlineClient airlineClient;
  private final LocationClient locationClient;

  @Transactional
  @Override
    public FlightResponse createFlight(final UUID userId, final FlightRequest flightRequest) {

    // todo: watch airlineId
        AirlineResponse airlineResponse = airlineClient.getAirlineByOwner(userId).getData();
    if (this.flightRepository.existsByFlightNumber(flightRequest.getFlightNumber())) {
      throw new BusinessException("flight with already exist");
    }
    final Flight flight = FlightMapper.toEntity(flightRequest);
        flight.setAirlineId(airlineResponse.getId());
    final Flight savedFlight = this.flightRepository.save(flight);

    return this.convertToFLightResponse(savedFlight);
  }

  @Override
    public Page<FlightResponse> getFlightsByAirline(final UUID userId, final UUID departureAirportId,
      final UUID arrivalAirportId, final Pageable pageable) {
    // todo: watch airlineId
        // todo: N+1 qua Feign - cần batch-by-ids cho AirlineClient/LocationClient
        AirlineResponse airlineResponse = airlineClient.getAirlineByOwner(userId).getData();
        return this.flightRepository.findByAirlineId(airlineResponse.getId(),
        departureAirportId,
        arrivalAirportId,
        pageable).map(this::convertToFLightResponse);
  }

  @Override
  public FlightResponse getFlightById(final UUID id) {
    final Flight flight = this.flightRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Flight", "id", id));

    return this.convertToFLightResponse(flight);
  }

  @Transactional
  @Override
  public FlightResponse updateFlight(final UUID id, final FlightRequest flightRequest) {
    final Flight existingFlight = this.flightRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Flight", "id", id));
    if (flightRequest.getFlightNumber() != null
        && this.flightRepository.existsByFlightNumberAndIdNot(flightRequest.getFlightNumber(), id)) {
      throw new BusinessException("flight with already exist");
    }

    FlightMapper.updateEntity(flightRequest, existingFlight);
    final Flight savedFlight = this.flightRepository.save(existingFlight);
    return this.convertToFLightResponse(savedFlight);
  }

  @Transactional
  @Override
  public FlightResponse changeStatus(final UUID id, final FlightStatus flightStatus) {

    final Flight existingFlight = this.flightRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Flight", "id", id));
    existingFlight.setStatus(flightStatus);
    final Flight updated = this.flightRepository.save(existingFlight);
    return this.convertToFLightResponse(updated);
  }

  @Transactional
  @Override
    public void deleteFlight(final UUID userId, final UUID id) {
        AirlineResponse airlineResponse = airlineClient.getAirlineByOwner(userId).getData();
        final Flight existingFlight = this.flightRepository.findByAirlineIdAndId(airlineResponse.getId(), id)
        .orElseThrow(() -> new ResourceNotFoundException("Flight", "id", id));

    if (this.flightScheduleRepository.existsByFlightId(id)
        || this.flightInstanceRepository.existsByFlightId(id)) {
      throw new BusinessException("Cannot delete flight that still has schedules or instances");
    }

    this.flightRepository.delete(existingFlight);
  }

  private FlightResponse convertToFLightResponse(final Flight flight) {
    final AircraftResponse aircraftResponse = this.airlineClient.getAircraftById(flight.getAircraftId()).getData();
    final AirlineResponse airlineResponse = this.airlineClient.getAirlineById(flight.getAirlineId()).getData();
    return this.buildFlightResponse(flight, aircraftResponse, airlineResponse);
  }

  private FlightResponse buildFlightResponse(final Flight flight, final AircraftResponse aircraftResponse,
      final AirlineResponse airlineResponse) {
    final AirportResponse departureAirport = locationClient.getAirportById(flight.getDepartureAirportId()).getData();
    final AirportResponse arrivalAirport = locationClient.getAirportById(flight.getArrivalAirportId()).getData();
    return FlightMapper.toResponse(flight, aircraftResponse, airlineResponse, departureAirport,
        arrivalAirport);
  }
}
