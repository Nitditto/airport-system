package com.nitditto.service.impl;

import com.nitditto.mapper.AirportMapper;
import com.nitditto.model.Airport;
import com.nitditto.model.City;
import com.nitditto.payload.request.AirportRequest;
import com.nitditto.payload.response.AirportResponse;
import com.nitditto.repository.AirportRepository;
import com.nitditto.repository.CityRepository;
import com.nitditto.service.AirportService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AirportServiceImpl implements AirportService {

  private final AirportRepository airportRepository;
  private final CityRepository cityRepository;

  @Override
  public AirportResponse createAirport(AirportRequest airportRequest) throws Exception {

    if (airportRepository.findByIataCode(airportRequest.getIataCode()).isPresent()) {
      throw new Exception("Airport with Iata Code Already Exist");
    }

    City city = cityRepository.findById(airportRequest.getCityId())
        .orElseThrow(() -> new Exception("City Not Found"));

    Airport airport = AirportMapper.toEntity(airportRequest);
    airport.setCity(city);
    Airport savedAirport = airportRepository.save(airport);

    return AirportMapper.toResponse(savedAirport);
  }

  @Override
  public AirportResponse getAirportById(Long id) throws Exception {
    Airport airport = airportRepository.findById(id).orElseThrow(
        () -> new Exception("Airport not exist with provided id")
    );
    return AirportMapper.toResponse(airport);
  }

  @Override
  public List<AirportResponse> getAllAirports() {
    return airportRepository.findAll().stream()
        .map(AirportMapper::toResponse)
        .collect(Collectors.toList());
  }

  @Override
  public AirportResponse updateAirport(Long id, AirportRequest airportRequest) throws Exception {
    Airport existingAirport = airportRepository.findById(id).orElseThrow(
        () -> new Exception("Airport not exist with id " + id)
    );
    if (airportRequest.getIataCode() != null && !existingAirport.getIataCode()
        .equals(airportRequest.getIataCode()) && airportRepository.findByIataCode(
        airportRequest.getIataCode()).isPresent()) {
      throw new Exception("Airport Iata Code Already Exist");
    }
    AirportMapper.updateEntity(airportRequest, existingAirport);
    Airport updatedAirport = airportRepository.save(existingAirport);
    return AirportMapper.toResponse(updatedAirport);
  }

  @Override
  public void deleteAirportById(Long id) throws Exception {
    Airport airport = airportRepository.findById(id).orElseThrow(
        () -> new Exception("Airport not exists with provided id")
    );
    airportRepository.delete(airport);
  }

  @Override
  public List<AirportResponse> getAirportsByCityId(Long cityId) {
    return airportRepository.findByCityId(cityId).stream()
        .map(AirportMapper::toResponse)
        .collect(Collectors.toList());
  }
}
