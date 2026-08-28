package com.nitditto.mapper;

import com.nitditto.model.Airport;
import com.nitditto.model.City;
import com.nitditto.payload.request.AirportRequest;
import com.nitditto.payload.response.AirportResponse;

public class AirportMapper {

  private AirportMapper() {
  }

  public static Airport toEntity(final AirportRequest airportRequest) {
    if (airportRequest == null) {
      return null;
    }
    return Airport.builder()
        .iataCode(airportRequest.getIataCode())
        .name(airportRequest.getName())
        .cityId(airportRequest.getCityId())
        .address(airportRequest.getAddress())
        .geoCode(airportRequest.getGeoCode())
        .build();
  }

  public static AirportResponse toResponse(final Airport airport, final City city) {
    if (airport == null) {
      return null;
    }
    return AirportResponse.builder()
        .id(airport.getId())
        .iataCode(airport.getIataCode())
        .name(airport.getName())
        .detailedName(buildDetailedName(airport, city))
        .address(airport.getAddress())
        .city(CityMapper.toResponse(city))
        .geoCode(airport.getGeoCode())
        .build();
  }

  public static void updateEntity(final AirportRequest airportRequest, final Airport existingAirport) {
    if (airportRequest == null || existingAirport == null) {
      return;
    }
    if (airportRequest.getIataCode() != null) {
      existingAirport.setIataCode(airportRequest.getIataCode());
    }
    if (airportRequest.getName() != null) {
      existingAirport.setName(airportRequest.getName());
    }
    if (airportRequest.getCityId() != null) {
      existingAirport.setCityId(airportRequest.getCityId());
    }
    if (airportRequest.getAddress() != null) {
      existingAirport.setAddress(airportRequest.getAddress());
    }
    if (airportRequest.getGeoCode() != null) {
      existingAirport.setGeoCode(airportRequest.getGeoCode());
    }
  }

  // Airport không còn giữ object City (đã gỡ @ManyToOne, chỉ còn cityId thô),
  // nên logic hiển thị cần City phải nhận qua tham số thay vì airport.getCity() như trước.
  private static String buildDetailedName(final Airport airport, final City city) {
    if (city != null && city.getCountryCode() != null) {
      return airport.getName().toUpperCase() + "/" + city.getCityCode();
    }
    return airport.getName().toUpperCase();
  }
}
