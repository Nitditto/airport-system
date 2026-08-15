package com.nitditto.service.impl;

import com.nitditto.mapper.AircraftMapper;
import com.nitditto.model.Aircraft;
import com.nitditto.model.Airline;
import com.nitditto.payload.request.AircraftRequest;
import com.nitditto.payload.response.AircraftResponse;
import com.nitditto.repository.AircraftRepository;
import com.nitditto.repository.AirlineRepository;
import com.nitditto.service.AircraftService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AircraftServiceImpl implements AircraftService {

  private final AircraftRepository aircraftRepository;
  private final AirlineRepository airlineRepository;

  @Override
  public AircraftResponse createAircraft(AircraftRequest aircraftRequest, Long ownerId)
      throws Exception {
    Airline airline = airlineRepository.findByOwnerId(ownerId).orElseThrow(() ->
        new Exception("Airline not exist for this ownerId"));

    Aircraft aircraft = AircraftMapper.toEntity(aircraftRequest, airline);

    if (aircraftRepository.existsByCode(aircraft.getCode())) {
      throw new Exception("code already exist with another aircraft");
    }
    if (aircraft.getSeatingCapacity() < aircraft.getTotalSeats()) {
      throw new Exception("seating capacity can't exceed to total seating capacity");
    }
    return AircraftMapper.toResponse(aircraftRepository.save(aircraft));
  }


  @Override
  public AircraftResponse getAircraftById(Long id) throws Exception {
    Aircraft aircraft = aircraftRepository.findById(id)
        .orElseThrow(() -> new Exception("Aircraft not exist with id"));
    return AircraftMapper.toResponse(aircraft);
  }

  @Override
  public List<AircraftResponse> listAllAircraftByOwner(Long ownerId) throws Exception {
    Airline airline = airlineRepository.findByOwnerId(ownerId)
        .orElseThrow(() -> new Exception("this owner dont have airline"));
    return aircraftRepository.findByAirlineId(airline.getId())
        .stream()
        .map(AircraftMapper::toResponse).toList();
  }

  @Override
  public AircraftResponse updateAircraft(Long id, AircraftRequest aircraftRequest, Long ownerId)
      throws Exception {
    Airline airline = airlineRepository.findByOwnerId(ownerId)
        .orElseThrow(() -> new Exception("this owner dont have airline"));
    Aircraft aircraft = aircraftRepository.findByIdAndAirlineId(id, airline.getId());
    if (aircraft == null) {
      throw new Exception("aircraft not exist with id");
    }
    if (aircraftRequest.getCode() != null && !aircraft.getCode().equals(aircraftRequest.getCode())
        && aircraftRepository.existsByCode(
        aircraftRequest.getCode())) {
      throw new Exception("code already exist with another aircraft");
    }
    AircraftMapper.updateEntity(aircraft, aircraftRequest);
    return AircraftMapper.toResponse(aircraftRepository.save(aircraft));
  }

  @Override
  public void deleteAircraft(Long id, Long ownerId) throws Exception {
    Airline airline = airlineRepository.findByOwnerId(ownerId)
        .orElseThrow(() -> new Exception("this owner dont have airline"));
    Aircraft aircraft = aircraftRepository.findByIdAndAirlineId(id, airline.getId());
    if (aircraft == null) {
      throw new Exception("aircraft not exist with id");
    }
    aircraftRepository.delete(aircraft);
  }
}
