package com.nitditto.mapper;

import com.nitditto.model.Aircraft;
import com.nitditto.model.Airline;
import com.nitditto.payload.request.AircraftRequest;
import com.nitditto.payload.response.AircraftResponse;

public class AircraftMapper {

  public static Aircraft toEntity(AircraftRequest request, Airline airline) {
    if (request == null) {
      return null;
    }
    return Aircraft.builder()
        .code(request.getCode())
        .model(request.getModel())
        .manufacturer(request.getManufacturer())
        .seatingCapacity(request.getSeatingCapacity())
        .economySeats(request.getEconomySeats())
        .premiumEconomySeats(request.getPremiumEconomySeats())
        .businessSeats(request.getBusinessSeats())
        .firstClassSeats(request.getFirstClassSeats())
        .rangeKm(request.getRangeKm())
        .cruisingSpeedKmh(request.getCruisingSpeedKmh())
        .maxAltitudeFt(request.getMaxAltitudeFt())
        .yearOfManufacture(request.getYearOfManufacture())
        .registrationDate(request.getRegistrationDate())
        .nextMaintenanceDate(request.getNextMaintenanceDate())
        .status(request.getStatus())
        .isAvailable(request.getIsAvailable())
        .airline(airline)
        .currentAirportId(request.getCurrentAirportId())
        .build();
  }

  public static AircraftResponse toResponse(Aircraft aircraft) {
    if (aircraft == null) {
      return null;
    }
    return AircraftResponse.builder()
        .id(aircraft.getId())
        .code(aircraft.getCode())
        .model(aircraft.getModel())
        .manufacturer(aircraft.getManufacturer())
        .seatingCapacity(aircraft.getSeatingCapacity())
        .economySeats(aircraft.getEconomySeats())
        .premiumEconomySeats(aircraft.getPremiumEconomySeats())
        .businessSeats(aircraft.getBusinessSeats())
        .firstClassSeats(aircraft.getFirstClassSeats())
        .rangeKm(aircraft.getRangeKm())
        .cruisingSpeedKmh(aircraft.getCruisingSpeedKmh())
        .maxAltitudeFt(aircraft.getMaxAltitudeFt())
        .yearOfManufacture(aircraft.getYearOfManufacture())
        .registrationDate(aircraft.getRegistrationDate())
        .nextMaintenanceDate(aircraft.getNextMaintenanceDate())
        .status(aircraft.getStatus())
        .isAvailable(aircraft.getIsAvailable())
        // Airline info (same service - available directly)
        .airlineId(aircraft.getAirline() != null ? aircraft.getAirline().getId() : null)
        .airlineName(aircraft.getAirline() != null ? aircraft.getAirline().getName() : null)
        .airlineIataCode(aircraft.getAirline() != null ? aircraft.getAirline().getIataCode() : null)
        //Airport is cross-service only ID available here
        .currentAirportId(aircraft.getCurrentAirportId())
        // Computed fields
        .totalSeats(aircraft.getTotalSeats())
        .requiresMaintenance(aircraft.requiresMaintenance())
        .isOperational(aircraft.isOperational())
        // Audit
        .createdAt(aircraft.getCreatedAt())
        .updatedAt(aircraft.getUpdatedAt())
        .build();
  }

  public static void updateEntity(Aircraft aircraft, AircraftRequest request) {
    if (aircraft == null || request == null) {
      return;
    }
    aircraft.setCode(request.getCode());
    aircraft.setModel(request.getModel());
    aircraft.setManufacturer(request.getManufacturer());
    aircraft.setSeatingCapacity(request.getSeatingCapacity());
    aircraft.setEconomySeats(request.getEconomySeats());
    aircraft.setPremiumEconomySeats(request.getPremiumEconomySeats());
    aircraft.setBusinessSeats(request.getBusinessSeats());
    aircraft.setFirstClassSeats(request.getFirstClassSeats());
    aircraft.setRangeKm(request.getRangeKm());
    aircraft.setCruisingSpeedKmh(request.getCruisingSpeedKmh());
    aircraft.setMaxAltitudeFt(request.getMaxAltitudeFt());
    aircraft.setYearOfManufacture(request.getYearOfManufacture());
    aircraft.setRegistrationDate(request.getRegistrationDate());
    aircraft.setNextMaintenanceDate(request.getNextMaintenanceDate());
    aircraft.setStatus(request.getStatus());
    aircraft.setIsAvailable(request.getIsAvailable());
    aircraft.setCurrentAirportId(request.getCurrentAirportId());
  }
}
