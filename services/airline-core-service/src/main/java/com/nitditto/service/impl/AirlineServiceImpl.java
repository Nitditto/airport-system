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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AirlineServiceImpl implements AirlineService {

  private final AirlineRepository airlineRepository;
  private final AircraftRepository aircraftRepository;

  @Override
  @Transactional
  public AirlineResponse createAirline(final AirlineRequest airlineRequest, final UUID ownerId) {
    final Airline airline = AirlineMapper.toEntity(airlineRequest, ownerId);
    final Airline savedAirline = this.airlineRepository.save(airline);
    log.info("Created airline id={} ownerId={}", savedAirline.getId(), ownerId);

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
  @Transactional
  public AirlineResponse updateAirline(final AirlineRequest airlineRequest, final UUID ownerId) {
    final Airline airline = this.airlineRepository.findByOwnerId(ownerId)
        .orElseThrow(() -> new ResourceNotFoundException("Airline", "ownerId", ownerId));

    AirlineMapper.updateEntity(airline, airlineRequest);
    final Airline savedAirline = this.airlineRepository.save(airline);
    log.info("Updated airline id={}", savedAirline.getId());
    return AirlineMapper.toResponse(savedAirline);
  }

  @Override
  @Transactional
  public void deleteAirline(final UUID id, final UUID ownerId) {
    final Airline airline = this.airlineRepository.findByOwnerId(ownerId)
        .orElseThrow(() -> new ResourceNotFoundException("Airline", "ownerId", ownerId));

    // Gỡ @ManyToOne khỏi Aircraft đồng nghĩa mất luôn FK constraint thật ở MySQL từng chặn
    // việc này - phải tự kiểm tra để tránh Aircraft mồ côi trỏ vào airlineId không còn tồn tại.
    if (!this.aircraftRepository.findByAirlineId(airline.getId()).isEmpty()) {
      throw new BusinessException("Cannot delete airline that still has aircraft");
    }

    this.airlineRepository.delete(airline);
    log.info("Deleted airline id={}", id);
  }

  @Override
  @Transactional
  public AirlineResponse changeStatusByAdmin(final UUID airlineId, final AirlineStatus status) {
    final Airline airline = this.airlineRepository.findById(airlineId)
        .orElseThrow(() -> new ResourceNotFoundException("Airline", "id", airlineId));
    airline.setStatus(status);
    final Airline updatedAirline = this.airlineRepository.save(airline);
    log.info("Changed airline id={} status={}", airlineId, status);
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
