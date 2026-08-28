package com.nitditto.service;

import com.nitditto.payload.request.CityRequest;
import com.nitditto.payload.response.CityResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CityService {

  CityResponse createCity(CityRequest cityRequest);

  CityResponse getCityById(UUID id);

  CityResponse updateCity(UUID id, CityRequest cityRequest);

  void deleteCityById(UUID id);

  Page<CityResponse> getAllCities(Pageable pageable);

  Page<CityResponse> searchCities(String keyword, Pageable pageable);

  Page<CityResponse> getCitiesByCountryCode(String countryCode, Pageable pageable);

  boolean cityExists(String cityCode);

}
