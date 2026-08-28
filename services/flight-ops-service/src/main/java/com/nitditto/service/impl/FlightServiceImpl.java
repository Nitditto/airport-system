package com.nitditto.service.impl;

import com.nitditto.enums.FlightStatus;
import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.mapper.FlightMapper;
import com.nitditto.model.Flight;
import com.nitditto.payload.response.AircraftResponse;
import com.nitditto.payload.response.AirlineResponse;
import com.nitditto.payload.response.AirportResponse;
import com.nitditto.repository.FlightInstanceRepository;
import com.nitditto.repository.FlightRepository;
import com.nitditto.repository.FlightScheduleRepository;
import com.nitditto.service.FlightService;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.nitditto.payload.request.FlightRequest;
import com.nitditto.payload.response.FlightResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlightServiceImpl implements FlightService {

  private final FlightRepository flightRepository;
  private final FlightScheduleRepository flightScheduleRepository;
  private final FlightInstanceRepository flightInstanceRepository;

  @Transactional
  @Override
  public FlightResponse createFlight(final UUID airlineId, final FlightRequest flightRequest) {

    // todo: watch airlineId
    if (this.flightRepository.existsByFlightNumber(flightRequest.getFlightNumber())) {
      throw new BusinessException("flight with already exist");
    }
    final Flight flight = FlightMapper.toEntity(flightRequest);
    flight.setAirlineId(airlineId);
    final Flight savedFlight = this.flightRepository.save(flight);
    log.info("Created flight id={} flightNumber={} airlineId={}", savedFlight.getId(),
        savedFlight.getFlightNumber(), airlineId);

    return this.convertToFLightResponse(savedFlight);
  }

  @Override
  public Page<FlightResponse> getFlightsByAirline(final UUID airlineId, final UUID departureAirportId,
      final UUID arrivalAirportId, final Pageable pageable) {
    // todo: watch airlineId
    return this.flightRepository.findByAirlineId(airlineId,
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
    log.info("Updated flight id={}", id);
    return this.convertToFLightResponse(savedFlight);
  }

  @Transactional
  @Override
  public FlightResponse changeStatus(final UUID id, final FlightStatus flightStatus) {

    final Flight existingFlight = this.flightRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Flight", "id", id));
    existingFlight.setStatus(flightStatus);
    final Flight updated = this.flightRepository.save(existingFlight);
    log.info("Changed flight id={} status={}", id, flightStatus);
    return this.convertToFLightResponse(updated);
  }

  @Transactional
  @Override
  public void deleteFlight(final UUID airlineId, final UUID id) {

    // todo: watch airlineId
    final Flight existingFlight = this.flightRepository.findByAirlineIdAndId(airlineId, id)
        .orElseThrow(() -> new ResourceNotFoundException("Flight", "id", id));

    // Gỡ @ManyToOne khỏi FlightSchedule/FlightInstance đồng nghĩa mất luôn FK constraint thật
    // ở MySQL từng chặn việc này - phải tự kiểm tra để tránh dữ liệu mồ côi.
    if (this.flightScheduleRepository.existsByFlightId(id)
        || this.flightInstanceRepository.existsByFlightId(id)) {
      throw new BusinessException("Cannot delete flight that still has schedules or instances");
    }

    this.flightRepository.delete(existingFlight);
    log.info("Deleted flight id={}", id);
  }

  private FlightResponse convertToFLightResponse(final Flight flight) {

    // todo: service to service communication
    final AircraftResponse aircraftResponse = AircraftResponse.builder().id(flight.getAircraftId())
        .build();
    final AirlineResponse airlineResponse =
        AirlineResponse.builder().id(flight.getAirlineId()).build();
    final AirportResponse departureAirport =
        AirportResponse.builder().id(flight.getDepartureAirportId()).build();
    final AirportResponse arrivalAirport =
        AirportResponse.builder().id(flight.getArrivalAirportId()).build();
    return FlightMapper.toResponse(flight, aircraftResponse, airlineResponse, departureAirport,
        arrivalAirport);
  }
}
