package com.nitditto.service.impl;

import com.nitditto.mapper.CityMapper;
import com.nitditto.model.City;
import com.nitditto.payload.request.CityRequest;
import com.nitditto.payload.response.CityResponse;
import com.nitditto.repository.CityRepository;
import com.nitditto.service.CityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CityServiceImpl implements CityService {


  private final CityRepository cityRepository;

  @Override
  public CityResponse createCity(CityRequest cityRequest) throws Exception {

    if (cityRepository.existsByCityCode(cityRequest.getCityCode())) {
      throw new Exception("City with given code already exist");
    }
    City city = CityMapper.toEntity(cityRequest);
    City result = cityRepository.save(city);
    return CityMapper.toResponse(result);
  }

  @Override
  public CityResponse getCityById(Long id) throws Exception {
    City city = cityRepository.findById(id)
        .orElseThrow(() -> new Exception("City not exit with given id"));
    return CityMapper.toResponse(city);
  }

  @Override
  public CityResponse updateCity(Long id, CityRequest cityRequest) throws Exception {
    City city = cityRepository.findById(id)
        .orElseThrow(() -> new Exception("City not exit with given id"));
    if (cityRepository.existsByCityCodeAndIdNot(cityRequest.getCityCode(), id)) {
      throw new Exception("City with given code already exist");
    }
    City updatedCity = cityRepository.save(CityMapper.updateEntity(city, cityRequest));
    return CityMapper.toResponse(updatedCity);

  }

  @Override
  public void deleteCityById(Long id) throws Exception {
    City city = cityRepository.findById(id)
        .orElseThrow(() -> new Exception("City not exit with given id"));
    cityRepository.delete(city);
  }

  @Override
  public Page<CityResponse> getAllCities(Pageable pageable) {
    return cityRepository.findAll(pageable).map(CityMapper::toResponse);
  }

  @Override
  public Page<CityResponse> searchCities(String keyword, Pageable pageable) {
    return cityRepository.searchByKeyword(keyword, pageable).map(CityMapper::toResponse);
  }

  @Override
  public Page<CityResponse> getCitiesByCountryCode(String countryCode, Pageable pageable) {
    return cityRepository.findByCityCodeIgnoreCase(countryCode, pageable)
        .map(CityMapper::toResponse);
  }

  @Override
  public boolean cityExists(String cityCode) {
    return cityRepository.existsByCityCode(cityCode);
  }

}
