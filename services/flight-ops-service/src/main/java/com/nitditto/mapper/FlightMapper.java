package com.nitditto.mapper;


import com.nitditto.model.Flight;
import com.nitditto.payload.request.FlightRequest;
import com.nitditto.payload.response.AircraftResponse;
import com.nitditto.payload.response.AirlineResponse;
import com.nitditto.payload.response.AirportResponse;
import com.nitditto.payload.response.FlightResponse;

public class FlightMapper {

  public static Flight toEntity(FlightRequest flightRequest) {
    if (flightRequest == null) {
      return null;
    }
    return Flight.builder()
        .flightNumber(flightRequest.getFlightNumber())
        .aircraftId(flightRequest.getAircraftId())
        .departureAirportId(flightRequest.getDepartureAirportId())
        .arrivalAirportId(flightRequest.getArrivalAirportId())
        .build();
  }

  public static FlightResponse toResponse(Flight flight, AircraftResponse aircraftResponse,
      AirlineResponse airlineResponse, AirportResponse departureAirport,
      AirportResponse arrivalAirport) {
    if (flight == null) {
      return null;
    }
    return FlightResponse.builder()
        .id(flight.getId())
        .flightNumber(flight.getFlightNumber())
        .airline(airlineResponse)
        .aircraft(aircraftResponse)
        .departureAirport(departureAirport)
        .arrivalAirport(arrivalAirport)
        .status(flight.getStatus())
        .createdAt(flight.getCreatedAt())
        .updatedAt(flight.getUpdatedAt())
        .build();
  }

  public static void updateEntity(FlightRequest flightRequest, Flight existingFlight) {
    if (flightRequest == null || existingFlight == null) {
      return;
    }
    if (flightRequest.getFlightNumber() != null) {
      existingFlight.setFlightNumber(flightRequest.getFlightNumber());
    }
    if (flightRequest.getAircraftId() != null) {
      existingFlight.setAircraftId(flightRequest.getAircraftId());
    }
    if (flightRequest.getDepartureAirportId() != null) {
      existingFlight.setDepartureAirportId(flightRequest.getDepartureAirportId());
    }
    if (flightRequest.getArrivalAirportId() != null) {
      existingFlight.setArrivalAirportId(flightRequest.getArrivalAirportId());
    }
    if (flightRequest.getStatus() != null) {
      existingFlight.setStatus(flightRequest.getStatus());
    }
  }
}
