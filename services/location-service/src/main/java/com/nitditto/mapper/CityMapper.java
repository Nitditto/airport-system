package com.nitditto.mapper;

import com.nitditto.model.City;
import com.nitditto.payload.request.CityRequest;
import com.nitditto.payload.response.CityResponse;

public class CityMapper {

  private CityMapper() {
  }

  public static City toEntity(CityRequest cityRequest) {
    if (cityRequest == null) {
      return null;
    }

    return City.builder()
        .name(cityRequest.getName())
        .cityCode(cityRequest.getCityCode())
        .countryCode(cityRequest.getCountryCode())
        .countryName(cityRequest.getCountryName())
        .regionCode(cityRequest.getRegionCode())
        .timeZoneId(cityRequest.getTimeZoneOffset())
        .build();
  }

  public static CityResponse toResponse(City city) {
    if (city == null) {
      return null;
    }
    return CityResponse.builder()
        .id(city.getId())
        .name(city.getName())
        .cityCode(city.getCityCode())
        .countryCode(city.getCountryCode())
        .countryName(city.getCountryName())
        .regionCode(city.getRegionCode())
        .build();
  }

  public static City updateEntity(City city, CityRequest cityRequest) {
    if (cityRequest.getName() != null){
      city.setName(cityRequest.getName().trim());
    }
    if(cityRequest.getCityCode() != null){
      city.setCityCode(cityRequest.getCityCode().trim().toUpperCase());
    }
    if(cityRequest.getCountryCode() != null){
      city.setCountryCode(cityRequest.getCountryCode().trim().toUpperCase());
    }
    if(cityRequest.getCountryName() != null){
      city.setCountryName(cityRequest.getCountryName().trim());
    }
    if(cityRequest.getRegionCode() != null){
      city.setRegionCode(cityRequest.getRegionCode().trim().toUpperCase());
    }
    return city;
  }
}
