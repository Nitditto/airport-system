package com.nitditto.service.impl;


import com.nitditto.mapper.FlightInstanceMapper;
import com.nitditto.model.Flight;
import com.nitditto.model.FlightInstance;
import com.nitditto.payload.request.FlightInstanceRequest;
import com.nitditto.payload.response.AircraftResponse;
import com.nitditto.payload.response.AirlineResponse;
import com.nitditto.payload.response.AirportResponse;
import com.nitditto.payload.response.FlightInstanceResponse;
import com.nitditto.repository.FlightInstanceRepository;
import com.nitditto.repository.FlightRepository;
import com.nitditto.service.FlightInstanceService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FlightInstanceServiceImpl implements FlightInstanceService {

  private final FlightRepository flightRepository;
  private final FlightInstanceRepository flightInstanceRepository;

  @Override
  public FlightInstanceResponse createFlightInstance(Long airlineId,
      FlightInstanceRequest flightInstanceRequest) throws Exception {

    // todo: watch airlineId
    Flight flight = flightRepository.findById(flightInstanceRequest.getFlightId())
        .orElseThrow(() -> new Exception("Flight not found"));

    // todo: service to service communication
    AircraftResponse aircraftResponse = AircraftResponse.builder()
        .id(1L)
        .totalSeats(90)
        .build();

    FlightInstance flightInstance = FlightInstanceMapper.toEntity(flightInstanceRequest, flight);
    flightInstance.setTotalSeats(aircraftResponse.getTotalSeats());
    flightInstance.setAvailableSeats(aircraftResponse.getTotalSeats());

    FlightInstance saved = flightInstanceRepository.save(flightInstance);

    // todo: create seat instances
    return convertToFlightInstanceResponse(saved);
  }

  @Override
  public FlightInstanceResponse getFlightInstanceById(Long id) throws Exception {
    FlightInstance flightInstance = flightInstanceRepository.findById(id)
        .orElseThrow(() -> new Exception("flight instance not found with id" + id));
    return convertToFlightInstanceResponse(flightInstance);
  }

  @Override
  public Page<FlightInstanceResponse> getByAirlineId(Long airlineId, Long departureAirportId,
      Long arrivalAirportId, Long flightId, LocalDate onDate, Pageable pageable) {

    // todo: watch airlineId
    LocalDateTime start = onDate != null ? onDate.atStartOfDay() : null;
    LocalDateTime end = onDate != null ? onDate.plusDays(1).atStartOfDay() : null;

    return flightInstanceRepository.findByAirlineId(airlineId, departureAirportId, arrivalAirportId,
        flightId, start, end, pageable).map(this::convertToFlightInstanceResponse);
  }

  @Override
  public FlightInstanceResponse updateFlightInstance(Long id,
      FlightInstanceRequest flightInstanceRequest) throws Exception {
    FlightInstance existing = flightInstanceRepository.findById(id)
        .orElseThrow(() -> new Exception("flight instance not found"));
    FlightInstanceMapper.updateEntity(flightInstanceRequest, existing);
    return convertToFlightInstanceResponse(flightInstanceRepository.save(existing));
  }

  @Override
  public void deleteFlightInstance(Long id) throws Exception {
    FlightInstance existing = flightInstanceRepository.findById(id)
        .orElseThrow(() -> new Exception("flight instance not found"));
    flightInstanceRepository.delete(existing);
  }

  private FlightInstanceResponse convertToFlightInstanceResponse(FlightInstance flightInstance) {

    // todo: service to service communication
    AirlineResponse airlineResponse = AirlineResponse.builder().id(flightInstance.getAirlineId())
        .build();
    AirportResponse departureAirport = AirportResponse.builder()
        .id(flightInstance.getDepartureAirportId()).build();
    AirportResponse arrivalAirport = AirportResponse.builder()
        .id(flightInstance.getArrivalAirportId()).build();

    AircraftResponse aircraftResponse = AircraftResponse.builder()
        .id(flightInstance.getFlight().getAircraftId()).build();

    return FlightInstanceMapper.toResponse(flightInstance, aircraftResponse, airlineResponse,
        departureAirport, arrivalAirport);

  }
}
