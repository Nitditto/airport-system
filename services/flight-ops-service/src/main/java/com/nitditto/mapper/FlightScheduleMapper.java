package com.nitditto.mapper;

import com.nitditto.model.Flight;
import com.nitditto.model.FlightSchedule;
import com.nitditto.payload.request.FlightScheduleRequest;
import com.nitditto.payload.response.AirportResponse;
import com.nitditto.payload.response.FlightScheduleResponse;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FlightScheduleMapper {

  private FlightScheduleMapper() {
  }

  public static FlightSchedule toEntity(final FlightScheduleRequest flightScheduleRequest,
      final Flight flight) {

    if (flightScheduleRequest == null || flight == null) {
      return null;
    }

    final Set<DayOfWeek> operatingDays = flightScheduleRequest.getOperatingDays() != null
        ? new HashSet<>(flightScheduleRequest.getOperatingDays())
        : new HashSet<>();

    return FlightSchedule.builder()
        .flightId(flight.getId())
        .departureAirportId(flight.getDepartureAirportId())
        .arrivalAirportId(flight.getArrivalAirportId())
        .departureTime(flightScheduleRequest.getDepartureTime())
        .arrivalTime(flightScheduleRequest.getArrivalTime())
        .startDate(flightScheduleRequest.getStartDate())
        .endDate(flightScheduleRequest.getEndDate())
        .operatingDays(operatingDays)
        .isActive(flightScheduleRequest.getIsActive() != null ? flightScheduleRequest.getIsActive()
            : true)
        .build();
  }

  public static FlightScheduleResponse toResponse(final FlightSchedule flightSchedule,
      final Flight flight, final AirportResponse arrival, final AirportResponse departure) {
    if (flightSchedule == null) {
      return null;
    }
    final List<DayOfWeek> operatingDays = flightSchedule.getOperatingDays() != null
        ? new ArrayList<>(flightSchedule.getOperatingDays())
        : new ArrayList<>();

    return FlightScheduleResponse.builder()
        .id(flightSchedule.getId())
        .flightId(flightSchedule.getFlightId())
        .flightNumber(flight != null ? flight.getFlightNumber() : null)
        .departureAirport(departure)
        .arrivalAirport(arrival)
        .departureTime(flightSchedule.getDepartureTime())
        .arrivalTime(flightSchedule.getArrivalTime())
        .startDate(flightSchedule.getStartDate())
        .endDate(flightSchedule.getEndDate())
        .operatingDays(operatingDays)
        .isActive(flightSchedule.getIsActive())
        .build();
  }

  public static void updateEntity(final FlightScheduleRequest flightScheduleRequest,
      final FlightSchedule existingFlightSchedule) {
    if (flightScheduleRequest == null || existingFlightSchedule == null) {
      return;
    }
    if (flightScheduleRequest.getDepartureTime() != null) {
      existingFlightSchedule.setDepartureTime(flightScheduleRequest.getDepartureTime());
    }
    if (flightScheduleRequest.getArrivalTime() != null) {
      existingFlightSchedule.setArrivalTime(flightScheduleRequest.getArrivalTime());
    }
    if (flightScheduleRequest.getStartDate() != null) {
      existingFlightSchedule.setStartDate(flightScheduleRequest.getStartDate());
    }
    if (flightScheduleRequest.getEndDate() != null) {
      existingFlightSchedule.setEndDate(flightScheduleRequest.getEndDate());
    }
    if (flightScheduleRequest.getOperatingDays() != null) {
      existingFlightSchedule.setOperatingDays(new HashSet<>(flightScheduleRequest.getOperatingDays()));
    }
    if (flightScheduleRequest.getIsActive() != null) {
      existingFlightSchedule.setIsActive(flightScheduleRequest.getIsActive());
    }
  }
}
