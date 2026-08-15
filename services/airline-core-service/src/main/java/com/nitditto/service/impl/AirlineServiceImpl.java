package com.nitditto.service.impl;

import com.nitditto.enums.AirlineStatus;
import com.nitditto.mapper.AirlineMapper;
import com.nitditto.model.Airline;
import com.nitditto.payload.request.AirlineRequest;
import com.nitditto.payload.response.AirlineDropDownItem;
import com.nitditto.payload.response.AirlineResponse;
import com.nitditto.repository.AirlineRepository;
import com.nitditto.service.AirlineService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AirlineServiceImpl implements AirlineService {

  private final AirlineRepository airlineRepository;

  @Override
  public AirlineResponse createAirline(AirlineRequest airlineRequest, Long ownerId) {
    Airline airline = AirlineMapper.toEntity(airlineRequest, ownerId);
    Airline savedAirline = airlineRepository.save(airline);

    return AirlineMapper.toResponse(savedAirline);
  }

  @Override
  public AirlineResponse getAirlineByOwner(Long ownerId) throws Exception {
    Airline airline = airlineRepository.findByOwnerId(ownerId)
        .orElseThrow(() -> new Exception("airline not found with ownerId " + ownerId));

    return AirlineMapper.toResponse(airline);
  }

  @Override
  public AirlineResponse getAirlineById(Long airlineId) throws Exception {
    Airline airline = airlineRepository.findById(airlineId)
        .orElseThrow(() -> new Exception("airline not found with id " + airlineId));

    return AirlineMapper.toResponse(airline);
  }

  @Override
  public Page<AirlineResponse> getAllAirlines(Pageable pageable) {
    return airlineRepository.findAll(pageable).map(AirlineMapper::toResponse);
  }

  @Override
  public AirlineResponse updateAirline(AirlineRequest airlineRequest, Long ownerId)
      throws Exception {
    Airline airline = airlineRepository.findByOwnerId(ownerId)
        .orElseThrow(() -> new Exception("airline not found with ownerId " + ownerId));

    AirlineMapper.updateEntity(airline, airlineRequest);
    Airline savedAirline = airlineRepository.save(airline);
    return AirlineMapper.toResponse(savedAirline);
  }

  @Override
  public void deleteAirline(Long id, Long ownerId) throws Exception {
    Airline airline = airlineRepository.findByOwnerId(ownerId)
        .orElseThrow(() -> new Exception("airline not found with ownerId " + ownerId));

    airlineRepository.delete(airline);
  }

  @Override
  public AirlineResponse changeStatusByAdmin(Long airlineId, AirlineStatus status)
      throws Exception {
    Airline airline = airlineRepository.findById(airlineId)
        .orElseThrow(() -> new Exception("airline not found with airlineId " + airlineId));
    airline.setStatus(status);
    Airline updatedAirline = airlineRepository.save(airline);
    return AirlineMapper.toResponse(updatedAirline);
  }

  @Override
  public List<AirlineDropDownItem> getAirlineDropDown() {
    return airlineRepository.findByStatus(AirlineStatus.ACTIVE)
        .stream()
        .map(a -> AirlineDropDownItem.builder()
            .iataCode(a.getIataCode())
            .icaoCode(a.getIcaoCode())
            .name(a.getName())
            .logoUrl(a.getLogoUrl())
            .build()).toList();
  }
}
