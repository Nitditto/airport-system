package com.nitditto.location_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nitditto.config.AirportBatchCache;
import com.nitditto.model.Airport;
import com.nitditto.model.City;
import com.nitditto.payload.request.AirportRequest;
import com.nitditto.payload.response.AirportResponse;
import com.nitditto.repository.AirportRepository;
import com.nitditto.repository.CityRepository;
import com.nitditto.service.AirportService;
import com.nitditto.service.impl.AirportServiceImpl;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** Kiểm tra hành vi cache-aside bằng cache in-memory; không cần Redis thật. */
@SpringJUnitConfig(AirportServiceCacheTest.TestConfig.class)
class AirportServiceCacheTest {

  @Configuration
  @EnableCaching
  static class TestConfig {
    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager();
    }

    @Bean
    AirportRepository airportRepository() {
      return mock(AirportRepository.class);
    }

    @Bean
    CityRepository cityRepository() {
      return mock(CityRepository.class);
    }

    @Bean
    AirportBatchCache airportBatchCache() {
      return mock(AirportBatchCache.class);
    }

    @Bean
    AirportService airportService(final AirportRepository airports, final CityRepository cities,
        final AirportBatchCache batchCache) {
      return new AirportServiceImpl(airports, cities, batchCache);
    }
  }

  @Autowired
  private AirportService airportService;
  @Autowired
  private AirportRepository airportRepository;
  @Autowired
  private CityRepository cityRepository;
  @Autowired
  private CacheManager cacheManager;
  @Autowired
  private AirportBatchCache batchCache;

  private final UUID airportId = UUID.randomUUID();
  private final UUID cityId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    reset(this.airportRepository, this.cityRepository, this.batchCache);
    this.cacheManager.getCacheNames().forEach(name -> this.cacheManager.getCache(name).clear());

    final City city = mock(City.class);
    when(city.getId()).thenReturn(this.cityId);
    final Airport airport = mock(Airport.class);
    when(airport.getId()).thenReturn(this.airportId);
    when(airport.getCityId()).thenReturn(this.cityId);
    when(airport.getName()).thenReturn("Noi Bai");
    when(this.airportRepository.findById(this.airportId)).thenReturn(Optional.of(airport));
    when(this.airportRepository.findAllById(List.of(this.airportId))).thenReturn(List.of(airport));
    when(this.airportRepository.existsById(this.airportId)).thenReturn(true);
    when(this.cityRepository.findAllById(any())).thenReturn(List.of(city));
  }

  @Test
  void getAirportById_secondCallHitsCache() {
    final AirportResponse first = this.airportService.getAirportById(this.airportId);
    final AirportResponse second = this.airportService.getAirportById(this.airportId);

    assertEquals(first, second);
    verify(this.airportRepository, times(1)).findById(this.airportId);
  }

  @Test
  void write_evictsCache() {
    this.airportService.getAirportById(this.airportId);

    this.airportService.deleteAirportById(this.airportId);
    this.airportService.getAirportById(this.airportId);

    verify(this.airportRepository, times(2)).findById(this.airportId);
  }

  @Test
  void getAirportsByIds_usesOneBatchReadAndOneBatchWrite() {
    final AirportResponse cached = AirportResponse.builder().id(this.airportId).iataCode("HAN").build();
    final UUID otherId = UUID.randomUUID();
    final Airport other = mock(Airport.class);
    when(other.getId()).thenReturn(otherId);
    when(other.getCityId()).thenReturn(this.cityId);
    when(other.getName()).thenReturn("Tan Son Nhat");
    when(this.airportRepository.findAllById(List.of(otherId))).thenReturn(List.of(other));
    when(this.batchCache.getAll(List.of(this.airportId, otherId)))
        .thenReturn(new java.util.HashMap<>(java.util.Map.of(this.airportId, cached)));

    final var result = this.airportService.getAirportsByIds(List.of(this.airportId, otherId));

    assertEquals(2, result.size());
    assertEquals(cached, result.get(this.airportId));
    verify(this.batchCache, times(1)).getAll(any());
    verify(this.batchCache, times(1)).putAll(any());
    verify(this.airportRepository, times(1)).findAllById(List.of(otherId));
  }
}
