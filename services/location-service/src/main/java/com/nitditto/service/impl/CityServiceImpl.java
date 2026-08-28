package com.nitditto.service.impl;

import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.mapper.CityMapper;
import com.nitditto.model.City;
import com.nitditto.payload.request.CityRequest;
import com.nitditto.payload.response.CityResponse;
import com.nitditto.repository.AirportRepository;
import com.nitditto.repository.CityRepository;
import com.nitditto.service.CityService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CityServiceImpl implements CityService {

  private final CityRepository cityRepository;
  private final AirportRepository airportRepository;

  @Override
  @Transactional
  public CityResponse createCity(final CityRequest cityRequest) {

    if (this.cityRepository.existsByCityCode(cityRequest.getCityCode())) {
      throw new BusinessException("City with given code already exist");
    }
    final City city = CityMapper.toEntity(cityRequest);
    final City result = this.cityRepository.save(city);
    return CityMapper.toResponse(result);
  }

  @Override
  public CityResponse getCityById(final UUID id) {
    final City city = this.cityRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("City", "id", id));
    return CityMapper.toResponse(city);
  }

  @Override
  @Transactional
  public CityResponse updateCity(final UUID id, final CityRequest cityRequest) {
    final City city = this.cityRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("City", "id", id));
    if (this.cityRepository.existsByCityCodeAndIdNot(cityRequest.getCityCode(), id)) {
      throw new BusinessException("City with given code already exist");
    }
    final City updatedCity = this.cityRepository.save(CityMapper.updateEntity(city, cityRequest));
    return CityMapper.toResponse(updatedCity);
  }

  @Override
  @Transactional
  public void deleteCityById(final UUID id) {
    if (!this.cityRepository.existsById(id)) {
      throw new ResourceNotFoundException("City", "id", id);
    }
    // Gỡ @ManyToOne khỏi Airport đồng nghĩa mất luôn FK constraint thật ở MySQL từng chặn
    // việc này - phải tự kiểm tra để tránh Airport mồ côi trỏ vào cityId không còn tồn tại.
    if (!this.airportRepository.findByCityId(id).isEmpty()) {
      throw new BusinessException("Cannot delete city that still has airports");
    }
    this.cityRepository.deleteById(id);
  }

  @Override
  public Page<CityResponse> getAllCities(final Pageable pageable) {
    return this.cityRepository.findAll(pageable).map(CityMapper::toResponse);
  }

  @Override
  public Page<CityResponse> searchCities(final String keyword, final Pageable pageable) {
    return this.cityRepository.searchByKeyword(keyword, pageable).map(CityMapper::toResponse);
  }

  @Override
  public Page<CityResponse> getCitiesByCountryCode(final String countryCode, final Pageable pageable) {
    return this.cityRepository.findByCityCodeIgnoreCase(countryCode, pageable)
        .map(CityMapper::toResponse);
  }

  @Override
  public boolean cityExists(final String cityCode) {
    return this.cityRepository.existsByCityCode(cityCode);
  }

}
