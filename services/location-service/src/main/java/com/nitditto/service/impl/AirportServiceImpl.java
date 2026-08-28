package com.nitditto.service.impl;

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
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AirportServiceImpl implements AirportService {

  private final AirportRepository airportRepository;
  private final CityRepository cityRepository;

  @Override
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
  public AirportResponse getAirportById(final UUID id) {
    final Airport airport = this.airportRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Airport", "id", id));
    return this.toResponses(List.of(airport)).get(0);
  }

  @Override
  public List<AirportResponse> getAllAirports() {
    // TODO(Nhóm C): chưa phân trang - findAll() không giới hạn số dòng, xem CLAUDE.md "Repository & Query Rules"
    return this.toResponses(this.airportRepository.findAll());
  }

  @Override
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
  public void deleteAirportById(final UUID id) {
    if (!this.airportRepository.existsById(id)) {
      throw new ResourceNotFoundException("Airport", "id", id);
    }
    this.airportRepository.deleteById(id);
  }

  @Override
  public List<AirportResponse> getAirportsByCityId(final UUID cityId) {
    return this.toResponses(this.airportRepository.findByCityId(cityId));
  }

  private List<AirportResponse> toResponses(final List<Airport> airports) {
    if (airports.isEmpty()) {
      return List.of();
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
