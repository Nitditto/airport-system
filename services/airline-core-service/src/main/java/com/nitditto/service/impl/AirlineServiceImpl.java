package com.nitditto.service.impl;

import com.nitditto.enums.AirlineStatus;
import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.mapper.AirlineMapper;
import com.nitditto.model.Airline;
import com.nitditto.payload.request.AirlineRequest;
import com.nitditto.payload.response.AirlineDropDownItem;
import com.nitditto.payload.response.AirlineResponse;
import com.nitditto.repository.AircraftRepository;
import com.nitditto.repository.AirlineRepository;
import com.nitditto.service.AirlineService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AirlineServiceImpl implements AirlineService {

  private final AirlineRepository airlineRepository;
  private final AircraftRepository aircraftRepository;

  @Override
  public AirlineResponse createAirline(final AirlineRequest airlineRequest, final UUID ownerId) {
    final Airline airline = AirlineMapper.toEntity(airlineRequest, ownerId);
    final Airline savedAirline = this.airlineRepository.save(airline);

    return AirlineMapper.toResponse(savedAirline);
  }

  @Override
  public AirlineResponse getAirlineByOwner(final UUID ownerId) {
    final Airline airline = this.airlineRepository.findByOwnerId(ownerId)
        .orElseThrow(() -> new ResourceNotFoundException("Airline", "ownerId", ownerId));

    return AirlineMapper.toResponse(airline);
  }

  @Override
  public AirlineResponse getAirlineById(final UUID airlineId) {
    final Airline airline = this.airlineRepository.findById(airlineId)
        .orElseThrow(() -> new ResourceNotFoundException("Airline", "id", airlineId));

    return AirlineMapper.toResponse(airline);
  }

  @Override
  public Page<AirlineResponse> getAllAirlines(final Pageable pageable) {
    return this.airlineRepository.findAll(pageable).map(AirlineMapper::toResponse);
  }

  @Override
  public AirlineResponse updateAirline(final AirlineRequest airlineRequest, final UUID ownerId) {
    final Airline airline = this.airlineRepository.findByOwnerId(ownerId)
        .orElseThrow(() -> new ResourceNotFoundException("Airline", "ownerId", ownerId));

    AirlineMapper.updateEntity(airline, airlineRequest);
    final Airline savedAirline = this.airlineRepository.save(airline);
    return AirlineMapper.toResponse(savedAirline);
  }

  @Override
  public void deleteAirline(final UUID id, final UUID ownerId) {
    final Airline airline = this.airlineRepository.findByOwnerId(ownerId)
        .orElseThrow(() -> new ResourceNotFoundException("Airline", "ownerId", ownerId));

    // Gỡ @ManyToOne khỏi Aircraft đồng nghĩa mất luôn FK constraint thật ở MySQL từng chặn
    // việc này - phải tự kiểm tra để tránh Aircraft mồ côi trỏ vào airlineId không còn tồn tại.
    if (!this.aircraftRepository.findByAirlineId(airline.getId()).isEmpty()) {
      throw new BusinessException("Cannot delete airline that still has aircraft");
    }

    this.airlineRepository.delete(airline);
  }

  @Override
  public AirlineResponse changeStatusByAdmin(final UUID airlineId, final AirlineStatus status) {
    final Airline airline = this.airlineRepository.findById(airlineId)
        .orElseThrow(() -> new ResourceNotFoundException("Airline", "id", airlineId));
    airline.setStatus(status);
    final Airline updatedAirline = this.airlineRepository.save(airline);
    return AirlineMapper.toResponse(updatedAirline);
  }

  @Override
  public List<AirlineDropDownItem> getAirlineDropDown() {
    return this.airlineRepository.findByStatus(AirlineStatus.ACTIVE)
        .stream()
        .map(a -> AirlineDropDownItem.builder()
            .id(a.getId())
            .iataCode(a.getIataCode())
            .icaoCode(a.getIcaoCode())
            .name(a.getName())
            .logoUrl(a.getLogoUrl())
            .build()).toList();
  }
}
