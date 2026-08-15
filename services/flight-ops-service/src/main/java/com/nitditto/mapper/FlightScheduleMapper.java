package com.nitditto.mapper;

import com.nitditto.model.Flight;
import com.nitditto.model.FlightSchedule;
import com.nitditto.payload.request.FlightScheduleRequest;
import com.nitditto.payload.response.AirportResponse;
import com.nitditto.payload.response.FlightScheduleResponse;

public class FlightScheduleMapper {

  public static FlightSchedule toEntity(FlightScheduleRequest flightScheduleRequest,
      Flight flight) {

    if (flightScheduleRequest == null || flight == null) {
      return null;
    }

    return FlightSchedule.builder()
        .flight(flight)
        .departureAirportId(flight.getDepartureAirportId())
        .arrivalAirportId(flight.getArrivalAirportId())
        .departureTime(flightScheduleRequest.getDepartureTime())
        .arrivalTime(flightScheduleRequest.getArrivalTime())
        .startDate(flightScheduleRequest.getStartDate())
        .endDate(flightScheduleRequest.getEndDate())
        .operatingDays(flightScheduleRequest.getOperatingDays())
        .isActive(flightScheduleRequest.getIsActive() != null ? flightScheduleRequest.getIsActive()
            : true)
        .build();
  }

  public static FlightScheduleResponse toResponse(FlightSchedule flightSchedule, AirportResponse arrival,
      AirportResponse departure) {
    if (flightSchedule == null) {
      return null;
    }
    return FlightScheduleResponse.builder()
        .id(flightSchedule.getId())
        .flightId(flightSchedule.getFlight() != null ? flightSchedule.getFlight().getId() : null)
        .flightNumber(
            flightSchedule.getFlight() != null ? flightSchedule.getFlight().getFlightNumber()
                : null)
        .departureAirport(departure)
        .arrivalAirport(arrival)
        .departureTime(flightSchedule.getDepartureTime())
        .arrivalTime(flightSchedule.getArrivalTime())
        .startDate(flightSchedule.getStartDate())
        .endDate(flightSchedule.getEndDate())
        .operatingDays(flightSchedule.getOperatingDays())
        .isActive(flightSchedule.getIsActive())
        .build();
  }

  public static void updateEntity(FlightScheduleRequest flightScheduleRequest,
      FlightSchedule existingFlightSchedule) {
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
      existingFlightSchedule.setOperatingDays(flightScheduleRequest.getOperatingDays());
    }
    if (flightScheduleRequest.getIsActive() != null) {
      existingFlightSchedule.setIsActive(flightScheduleRequest.getIsActive());
    }
  }
}
