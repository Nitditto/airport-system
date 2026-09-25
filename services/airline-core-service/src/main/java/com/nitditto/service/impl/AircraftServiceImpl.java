package com.nitditto.service.impl;

import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.mapper.AircraftMapper;
import com.nitditto.model.Aircraft;
import com.nitditto.model.Airline;
import com.nitditto.payload.request.AircraftRequest;
import com.nitditto.payload.response.AircraftResponse;
import com.nitditto.repository.AircraftRepository;
import com.nitditto.repository.AirlineRepository;
import com.nitditto.service.AircraftService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AircraftServiceImpl implements AircraftService {

  private final AircraftRepository aircraftRepository;
  private final AirlineRepository airlineRepository;

  @Override
  @Transactional
  public AircraftResponse createAircraft(final AircraftRequest aircraftRequest, final UUID ownerId) {
    final Airline airline = this.airlineRepository.findByOwnerId(ownerId)
        .orElseThrow(() -> new ResourceNotFoundException("Airline", "ownerId", ownerId));

    if (this.aircraftRepository.existsByCode(aircraftRequest.getCode())) {
      throw new BusinessException("code already exist with another aircraft");
    }

    final Aircraft aircraft = AircraftMapper.toEntity(aircraftRequest, airline.getId());

    if (aircraft.getSeatingCapacity() < aircraft.getTotalSeats()) {
      throw new BusinessException("seating capacity can't exceed to total seating capacity");
    }
    final Aircraft saved = this.aircraftRepository.save(aircraft);
    return AircraftMapper.toResponse(saved, airline);
  }

  @Override
  public AircraftResponse getAircraftById(final UUID id) {
    final Aircraft aircraft = this.aircraftRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Aircraft", "id", id));
    final Airline airline = this.airlineRepository.findById(aircraft.getAirlineId()).orElse(null);
    return AircraftMapper.toResponse(aircraft, airline);
  }

  @Override
  public Map<UUID, AircraftResponse> getAircraftsByIds(final List<UUID> ids) {
    final List<Aircraft> aircrafts = this.aircraftRepository.findAllById(ids);

    final List<UUID> airlineIds = aircrafts.stream().map(Aircraft::getAirlineId).distinct().toList();
    final Map<UUID, Airline> airlineById = this.airlineRepository.findAllById(airlineIds).stream()
        .collect(Collectors.toMap(Airline::getId, Function.identity()));

    return aircrafts.stream()
        .collect(Collectors.toMap(Aircraft::getId,
            aircraft -> AircraftMapper.toResponse(aircraft, airlineById.get(aircraft.getAirlineId()))));
  }

  @Override
  public List<AircraftResponse> listAllAircraftByOwner(final UUID ownerId) {
    final Airline airline = this.airlineRepository.findByOwnerId(ownerId)
        .orElseThrow(() -> new ResourceNotFoundException("Airline", "ownerId", ownerId));
    return this.aircraftRepository.findByAirlineId(airline.getId())
        .stream()
        .map(aircraft -> AircraftMapper.toResponse(aircraft, airline))
        .toList();
  }

  @Override
  @Transactional
  public AircraftResponse updateAircraft(final UUID id, final AircraftRequest aircraftRequest,
      final UUID ownerId) {
    final Airline airline = this.airlineRepository.findByOwnerId(ownerId)
        .orElseThrow(() -> new ResourceNotFoundException("Airline", "ownerId", ownerId));
    final Aircraft aircraft = this.aircraftRepository.findByIdAndAirlineId(id, airline.getId());
    if (aircraft == null) {
      throw new ResourceNotFoundException("Aircraft", "id", id);
    }
    if (aircraftRequest.getCode() != null && !aircraft.getCode().equals(aircraftRequest.getCode())
        && this.aircraftRepository.existsByCode(aircraftRequest.getCode())) {
      throw new BusinessException("code already exist with another aircraft");
    }
    AircraftMapper.updateEntity(aircraft, aircraftRequest);
    final Aircraft saved = this.aircraftRepository.save(aircraft);
    return AircraftMapper.toResponse(saved, airline);
  }

  @Override
  @Transactional
  public void deleteAircraft(final UUID id, final UUID ownerId) {
    final Airline airline = this.airlineRepository.findByOwnerId(ownerId)
        .orElseThrow(() -> new ResourceNotFoundException("Airline", "ownerId", ownerId));
    final Aircraft aircraft = this.aircraftRepository.findByIdAndAirlineId(id, airline.getId());
    if (aircraft == null) {
      throw new ResourceNotFoundException("Aircraft", "id", id);
    }
    this.aircraftRepository.delete(aircraft);
  }
}
