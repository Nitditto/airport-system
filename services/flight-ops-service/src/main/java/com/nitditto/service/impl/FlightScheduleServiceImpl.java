package com.nitditto.service.impl;

import com.nitditto.enums.FlightStatus;
import com.nitditto.mapper.FlightScheduleMapper;
import com.nitditto.model.Flight;
import com.nitditto.model.FlightSchedule;
import com.nitditto.payload.request.FlightInstanceRequest;
import com.nitditto.payload.request.FlightScheduleRequest;
import com.nitditto.payload.response.AirportResponse;
import com.nitditto.payload.response.FlightScheduleResponse;
import com.nitditto.repository.FlightRepository;
import com.nitditto.repository.FlightScheduleRepository;
import com.nitditto.service.FlightInstanceService;
import com.nitditto.service.FlightScheduleService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FlightScheduleServiceImpl implements FlightScheduleService {


  private final FlightRepository flightRepository;
  private final FlightScheduleRepository flightScheduleRepository;
  private final FlightInstanceService flightInstanceService;

  @Transactional(rollbackFor = Exception.class)
  @Override
  public FlightScheduleResponse createFlightSchedule(Long airlineId,
      FlightScheduleRequest flightScheduleRequest) throws Exception {

    // todo watch for airlineId
    if (flightScheduleRequest.getOperatingDays() == null || flightScheduleRequest.getOperatingDays().isEmpty()) {
      throw new Exception("Operating days cannot be empty");
    }

    Flight flight = flightRepository.findById(flightScheduleRequest.getFlightId())
        .orElseThrow(() -> new Exception("Flight not found with given id"));

    if (flightScheduleRequest.getEndDate().isBefore(flightScheduleRequest.getStartDate())) {
      throw new Exception("End date cannot be before start date");
    }
    FlightSchedule flightSchedule = FlightScheduleMapper.toEntity(flightScheduleRequest, flight);
    FlightSchedule savedSchedule = flightScheduleRepository.save(flightSchedule);

    // create flight instance for saved scheduled
    // 11/03 to 10/04
    List<DayOfWeek> operatingDays = savedSchedule.getOperatingDays();
    LocalDate startDate = savedSchedule.getStartDate();
    LocalDate endDate = savedSchedule.getEndDate();

    FlightInstanceRequest flightInstanceRequest = FlightInstanceRequest.builder()
        .scheduleId(savedSchedule.getId())
        .flightId(flight.getId())
        .arrivalAirportId(flight.getArrivalAirportId())
        .departureAirportId(flight.getDepartureAirportId())
        .status(FlightStatus.SCHEDULED)
        .build();

    for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
      if (operatingDays.contains(date.getDayOfWeek())) {
        LocalDateTime departureDateTime = LocalDateTime.of(date, savedSchedule.getDepartureTime());
        LocalDateTime arrivalDateTime = LocalDateTime.of(date, savedSchedule.getArrivalTime());
        if (arrivalDateTime.isBefore(departureDateTime)) {
          arrivalDateTime = arrivalDateTime.plusDays(1);
        }
        flightInstanceRequest.setDepartureDateTime(departureDateTime);
        flightInstanceRequest.setArrivalDateTime(arrivalDateTime);
        flightInstanceService.createFlightInstance(airlineId, flightInstanceRequest);
      }
    }
    return convertToFlightScheduleResponse(savedSchedule);
  }

  @Override
  public FlightScheduleResponse getFlightScheduleById(Long id) throws Exception {
    FlightSchedule flightSchedule = flightScheduleRepository.findById(id)
        .orElseThrow(() -> new Exception("Flight schedule not found with id"));
    return convertToFlightScheduleResponse(flightSchedule);
  }

  @Override
  public List<FlightScheduleResponse> getFlightScheduleByAirline(Long airlineId) {
    // todo: watch airlineId
    List<FlightSchedule> schedules = flightScheduleRepository.findByFlightAirlineId(airlineId);
    return schedules.stream().map(this::convertToFlightScheduleResponse).toList();
  }

  @Override
  public FlightScheduleResponse updateFlightSchedule(Long id,
      FlightScheduleRequest flightScheduleRequest) throws Exception {
    FlightSchedule flightSchedule = flightScheduleRepository.findById(id)
        .orElseThrow(() -> new Exception("Flight schedule not found with id"));
    FlightScheduleMapper.updateEntity(flightScheduleRequest, flightSchedule);
    FlightSchedule updatedSchedule = flightScheduleRepository.save(flightSchedule);

    return convertToFlightScheduleResponse(updatedSchedule);
  }

  @Override
  public void deleteFlightScheduleById(Long id) throws Exception {
    FlightSchedule flightSchedule = flightScheduleRepository.findById(id)
        .orElseThrow(() -> new Exception("Flight schedule not found with id"));

    flightScheduleRepository.delete(flightSchedule);
  }

  private FlightScheduleResponse convertToFlightScheduleResponse(FlightSchedule flightSchedule) {
    // todo: service to service communication
    AirportResponse departureAirport = AirportResponse.builder()
        .id(flightSchedule.getDepartureAirportId())
        .build();
    AirportResponse arrivalAirport = AirportResponse.builder()
        .id(flightSchedule.getArrivalAirportId())
        .build();

    return FlightScheduleMapper.toResponse(
        flightSchedule, arrivalAirport, departureAirport
    );
  }
}
