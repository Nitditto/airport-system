package com.nitditto.mapper;

import com.nitditto.enums.FlightStatus;
import com.nitditto.model.Flight;
import com.nitditto.model.FlightInstance;
import com.nitditto.payload.request.FlightInstanceRequest;
import com.nitditto.payload.response.AircraftResponse;
import com.nitditto.payload.response.AirlineResponse;
import com.nitditto.payload.response.AirportResponse;
import com.nitditto.payload.response.FlightInstanceResponse;

public class FlightInstanceMapper {

  private FlightInstanceMapper() {
  }

  public static FlightInstance toEntity(final FlightInstanceRequest flightInstanceRequest,
      final Flight flight) {
    if (flight == null) {
      return null;
    }
    return FlightInstance.builder()
        .flightId(flight.getId())
        .airlineId(flight.getAirlineId())
        .scheduleId(flightInstanceRequest.getScheduleId())
        .departureAirportId(flightInstanceRequest.getDepartureAirportId() != null
            ? flightInstanceRequest.getDepartureAirportId()
            : (flight != null ? flight.getDepartureAirportId() : null))
        .arrivalAirportId(flightInstanceRequest.getArrivalAirportId() != null
            ? flightInstanceRequest.getArrivalAirportId()
            : (flight != null ? flight.getArrivalAirportId() : null))
        .departureDateTime(flightInstanceRequest.getDepartureDateTime())
        .arrivalDateTime(flightInstanceRequest.getArrivalDateTime())
        .status(FlightStatus.SCHEDULED)
        .minAdvanceBookingDays(flightInstanceRequest.getMinAdvanceBookingDays())
        .maxAdvanceBookingDays(flightInstanceRequest.getMaxAdvanceBookingDays())
        .isActive(flightInstanceRequest.getIsActive() != null ? flightInstanceRequest.getIsActive()
            : true)
        .build();
  }

  public static FlightInstanceResponse toResponse(final FlightInstance flightInstance,
      final Flight flight,
      final AircraftResponse aircraftResponse,
      final AirlineResponse airlineResponse,
      final AirportResponse departureAirport,
      final AirportResponse arrivalAirport) {

    if (flightInstance == null) {
      return null;
    }
    return FlightInstanceResponse.builder()
        .id(flightInstance.getId())
        .flightId(flightInstance.getFlightId())
        .flightNumber(flight != null ? flight.getFlightNumber() : null)
        // FE tra hạng ghế theo aircraftId của chuyến; thiếu trường này thì tìm chuyến lỗi 400 (cabin-classes/aircraft/null).
        .aircraftId(aircraftResponse != null && aircraftResponse.getId() != null ? aircraftResponse.getId()
            : (flight != null ? flight.getAircraftId() : null))
        .aircraftModal(aircraftResponse != null ? aircraftResponse.getModel() : null)
        .aircraftCode(aircraftResponse != null ? aircraftResponse.getCode() : null)
        .airlineId(flightInstance.getAirlineId())
        .airlineName(airlineResponse != null ? airlineResponse.getName() : null)
        .airlineLogo(airlineResponse != null ? airlineResponse.getLogoUrl() : null)
        .departureAirport(departureAirport)
        .arrivalAirport(arrivalAirport)
        .departureDateTime(flightInstance.getDepartureDateTime())
        .arrivalDateTime(flightInstance.getArrivalDateTime())
        .formatedDuration(flightInstance.getFormatedDuration())
        .totalSeats(flightInstance.getTotalSeats())
        .availableSeats(flightInstance.getAvailableSeats())
        .status(flightInstance.getStatus())
        .minAdvanceBookingDays(flightInstance.getMinAdvanceBookingDays())
        .maxAdvanceBookingDays(flightInstance.getMaxAdvanceBookingDays())
        .isActive(flightInstance.getIsActive())
        .build();
  }

  public static void updateEntity(final FlightInstanceRequest flightInstanceRequest,
      final FlightInstance existing) {
    if (flightInstanceRequest == null || existing == null) {
      return;
    }
    if (flightInstanceRequest.getDepartureAirportId() != null) {
      existing.setDepartureAirportId(flightInstanceRequest.getDepartureAirportId());
    }
    if (flightInstanceRequest.getArrivalAirportId() != null) {
      existing.setArrivalAirportId(flightInstanceRequest.getArrivalAirportId());
    }
    if (flightInstanceRequest.getDepartureDateTime() != null) {
      existing.setDepartureDateTime(flightInstanceRequest.getDepartureDateTime());
    }
    if (flightInstanceRequest.getArrivalDateTime() != null) {
      existing.setArrivalDateTime(flightInstanceRequest.getArrivalDateTime());
    }
    if (flightInstanceRequest.getTotalSeats() != null) {
      existing.setTotalSeats(flightInstanceRequest.getTotalSeats());
    }
    if (flightInstanceRequest.getAvailableSeats() != null) {
      existing.setAvailableSeats(flightInstanceRequest.getAvailableSeats());
    }
    if (flightInstanceRequest.getStatus() != null) {
      existing.setStatus(flightInstanceRequest.getStatus());
    }
    if (flightInstanceRequest.getMinAdvanceBookingDays() != null) {
      existing.setMinAdvanceBookingDays(flightInstanceRequest.getMinAdvanceBookingDays());
    }
    if (flightInstanceRequest.getMaxAdvanceBookingDays() != null) {
      existing.setMaxAdvanceBookingDays(flightInstanceRequest.getMaxAdvanceBookingDays());
    }
    if (flightInstanceRequest.getIsActive() != null) {
      existing.setIsActive(flightInstanceRequest.getIsActive());
    }
  }
}
