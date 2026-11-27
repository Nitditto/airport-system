package com.nitditto.service.impl;

import com.nitditto.config.AirportBatchCache;
import com.nitditto.config.EvictLocationCaches;
import com.nitditto.config.RedisCacheConfig;
import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.mapper.AirportMapper;
import com.nitditto.model.Airport;
import com.nitditto.model.City;
import com.nitditto.payload.request.AirportRequest;
import com.nitditto.payload.response.AirportResponse;
import com.nitditto.repository.AirportRepository;
import com.nitditto.repository.CityRepository;
import com.nitditto.service.AirportService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AirportServiceImpl implements AirportService {

  private static final int MAX_BATCH_SIZE = 200;

  private final AirportRepository airportRepository;
  private final CityRepository cityRepository;
  private final AirportBatchCache airportBatchCache;

  @Override
  @Transactional
  @EvictLocationCaches
  public AirportResponse createAirport(final AirportRequest airportRequest) {
    if (this.airportRepository.findByIataCode(airportRequest.getIataCode()).isPresent()) {
      throw new BusinessException("Airport with Iata Code Already Exist");
    }

    final City city = this.cityRepository.findById(airportRequest.getCityId())
        .orElseThrow(() -> new ResourceNotFoundException("City", "id", airportRequest.getCityId()));

    final Airport airport = AirportMapper.toEntity(airportRequest);
    final Airport savedAirport = this.airportRepository.save(airport);

    return AirportMapper.toResponse(savedAirport, city);
  }

  @Override
  @Cacheable(cacheNames = RedisCacheConfig.AIRPORT, key = "#id")
  public AirportResponse getAirportById(final UUID id) {
    final Airport airport = this.airportRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Airport", "id", id));
    return this.toResponses(List.of(airport)).get(0);
  }

  @Override
  public Map<UUID, AirportResponse> getAirportsByIds(final List<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      return Map.of();
    }
    final List<UUID> distinctIds = ids.stream().filter(Objects::nonNull).distinct().toList();
    if (distinctIds.size() > MAX_BATCH_SIZE) {
      throw new BusinessException("Batch size must not exceed " + MAX_BATCH_SIZE);
    }
    // Dùng chung cache "airport" với getAirportById: 1 MGET cho cả lô, chỉ id thiếu mới xuống DB rồi ghi lại 1 pipeline.
    final Map<UUID, AirportResponse> responseById = this.airportBatchCache.getAll(distinctIds);
    final List<UUID> missedIds = distinctIds.stream().filter(id -> !responseById.containsKey(id)).toList();
    if (!missedIds.isEmpty()) {
      final List<AirportResponse> loaded = this.toResponses(this.airportRepository.findAllById(missedIds));
      for (final AirportResponse response : loaded) {
        responseById.put(response.getId(), response);
      }
      this.airportBatchCache.putAll(loaded);
    }
    return responseById;
  }

  @Override
  @Cacheable(cacheNames = RedisCacheConfig.AIRPORTS)
  public List<AirportResponse> getAllAirports() {
    // TODO(Nhóm C): chưa phân trang - findAll() không giới hạn số dòng, xem CLAUDE.md "Repository & Query Rules"
    return this.toResponses(this.airportRepository.findAll());
  }

  @Override
  @Transactional
  @EvictLocationCaches
  public AirportResponse updateAirport(final UUID id, final AirportRequest airportRequest) {
    final Airport existingAirport = this.airportRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Airport", "id", id));
    if (airportRequest.getIataCode() != null
        && !existingAirport.getIataCode().equals(airportRequest.getIataCode())
        && this.airportRepository.findByIataCode(airportRequest.getIataCode()).isPresent()) {
      throw new BusinessException("Airport Iata Code Already Exist");
    }
    AirportMapper.updateEntity(airportRequest, existingAirport);
    final Airport updatedAirport = this.airportRepository.save(existingAirport);
    return this.toResponses(List.of(updatedAirport)).get(0);
  }

  @Override
  @Transactional
  @EvictLocationCaches
  public void deleteAirportById(final UUID id) {
    if (!this.airportRepository.existsById(id)) {
      throw new ResourceNotFoundException("Airport", "id", id);
    }
    this.airportRepository.deleteById(id);
  }

  @Override
  @Cacheable(cacheNames = RedisCacheConfig.AIRPORTS_BY_CITY, key = "#cityId")
  public List<AirportResponse> getAirportsByCityId(final UUID cityId) {
    return this.toResponses(this.airportRepository.findByCityId(cityId));
  }

  private List<AirportResponse> toResponses(final List<Airport> airports) {
    if (airports.isEmpty()) {
      // ArrayList (không phải List.of): Jackson default typing bỏ type id của lớp final nên cache không đọc lại được.
      return new ArrayList<>();
    }

    final List<UUID> cityIds = airports.stream().map(Airport::getCityId).distinct().toList();
    final Map<UUID, City> cityById = this.cityRepository.findAllById(cityIds)
        .stream().collect(Collectors.toMap(City::getId, Function.identity()));

    final List<AirportResponse> responses = new ArrayList<>(airports.size());
    for (final Airport airport : airports) {
      responses.add(AirportMapper.toResponse(airport, cityById.get(airport.getCityId())));
    }
    return responses;
  }
}
