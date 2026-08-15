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

  public static FlightInstance toEntity(FlightInstanceRequest flightInstanceRequest,
      Flight flight) {
    if (flight == null) {
      return null;
    }
    return FlightInstance.builder()
        .flight(flight)
        .airlineId(flight.getAirlineId())
        .scheduleId(flightInstanceRequest.getScheduleId())
        .departureAirportId(flightInstanceRequest.getDepartureAirportId())
        .arrivalAirportId(flightInstanceRequest.getArrivalAirportId())
        .departureDateTime(flightInstanceRequest.getDepartureDateTime())
        .arrivalDateTime(flightInstanceRequest.getArrivalDateTime())
        .status(FlightStatus.SCHEDULED)
        .minAdvanceBookingDays(flightInstanceRequest.getMinAdvanceBookingDays())
        .maxAdvanceBookingDays(flightInstanceRequest.getMaxAdvanceBookingDays())
        .isActive(flightInstanceRequest.getIsActive() != null ? flightInstanceRequest.getIsActive()
            : true)
        .build();
  }

  public static FlightInstanceResponse toResponse(FlightInstance flightInstance,
      AircraftResponse aircraftResponse,
      AirlineResponse airlineResponse,
      AirportResponse departureAirport,
      AirportResponse arrivalAirport) {

    if (flightInstance == null) {
      return null;
    }
    return FlightInstanceResponse.builder()
        .id(flightInstance.getId())
        .flightId(flightInstance.getFlight() != null ? flightInstance.getFlight().getId() : null)
        .flightNumber(flightInstance.getFlight() != null ?  flightInstance.getFlight().getFlightNumber() : null)
        .aircraftModal(aircraftResponse.getModel())
        .aircraftCode(aircraftResponse.getCode())
        .airlineId(flightInstance.getAirlineId())
        .airlineName(airlineResponse.getName())
        .airlineLogo(airlineResponse.getLogoUrl())
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

  public static void updateEntity(FlightInstanceRequest flightInstanceRequest, FlightInstance existing) {
    if (flightInstanceRequest == null ||  existing == null) {
      return;
    }
    if(flightInstanceRequest.getDepartureAirportId() != null){
      existing.setDepartureAirportId(flightInstanceRequest.getDepartureAirportId());
    }
    if(flightInstanceRequest.getArrivalAirportId() != null){
      existing.setArrivalAirportId(flightInstanceRequest.getArrivalAirportId());
    }
    if(flightInstanceRequest.getDepartureDateTime() != null){
      existing.setDepartureDateTime(flightInstanceRequest.getDepartureDateTime());
    }
    if(flightInstanceRequest.getArrivalDateTime() != null){
      existing.setArrivalDateTime(flightInstanceRequest.getArrivalDateTime());
    }
    if(flightInstanceRequest.getTotalSeats() != null){
      existing.setTotalSeats(flightInstanceRequest.getTotalSeats());
    }
    if(flightInstanceRequest.getAvailableSeats() != null){
      existing.setAvailableSeats(flightInstanceRequest.getAvailableSeats());
    }
    if(flightInstanceRequest.getStatus() != null){
      existing.setStatus(flightInstanceRequest.getStatus());
    }
    if(flightInstanceRequest.getMinAdvanceBookingDays() != null){
      existing.setMinAdvanceBookingDays(flightInstanceRequest.getMinAdvanceBookingDays());
    }
    if(flightInstanceRequest.getMaxAdvanceBookingDays() != null){
      existing.setMaxAdvanceBookingDays(flightInstanceRequest.getMaxAdvanceBookingDays());
    }
    if(flightInstanceRequest.getIsActive() != null){
      existing.setIsActive(flightInstanceRequest.getIsActive());
    }
  }
}
