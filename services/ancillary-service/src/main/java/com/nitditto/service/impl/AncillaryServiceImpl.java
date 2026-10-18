package com.nitditto.service.impl;

import com.nitditto.client.AirlineClient;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.mapper.AncillaryMapper;
import com.nitditto.model.Ancillary;
import com.nitditto.payload.request.AncillaryRequest;
import com.nitditto.payload.response.AirlineResponse;
import com.nitditto.payload.response.AncillaryResponse;
import com.nitditto.repository.AncillaryRepository;
import com.nitditto.service.AncillaryService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AncillaryServiceImpl implements AncillaryService {

  private final AncillaryRepository ancillaryRepository;
  private final AirlineClient airlineClient;

  @Override
  public AncillaryResponse createAncillary(UUID userId, AncillaryRequest request) {

    AirlineResponse airlineResponse = airlineClient.getAirlineByOwner(userId).getData();

    Ancillary ancillary = Ancillary.builder()
        .type(request.getType())
        .subType(request.getSubType())
        .rfisc(request.getRfisc())
        .name(request.getName())
        .description(request.getDescription())
        .metadata(request.getMetadata())
        .displayOrder(request.getDisplayOrder())
        .airlineId(airlineResponse.getId())
        .build();

    Ancillary saved = ancillaryRepository.save(ancillary);
    return AncillaryMapper.toResponse(saved, null);
  }

  @Override
  public AncillaryResponse getById(UUID id) {
    Ancillary ancillary = ancillaryRepository.findById(id).orElseThrow(
        () -> new ResourceNotFoundException("Ancillary", "id", id));

    // todo: fetch insurance coverages by ancillary
    return AncillaryMapper.toResponse(ancillary, null);
  }

  @Override
  public List<AncillaryResponse> getByAirlineId(UUID userId) {
    AirlineResponse airlineResponse = airlineClient.getAirlineByOwner(userId).getData();
    return ancillaryRepository.findByAirlineId(airlineResponse.getId())
        .stream().map(
            ancillary -> {
              // todo: fetch insurance coverages by ancillary

              return AncillaryMapper.toResponse(ancillary, null);
            }

        )
        .collect(Collectors.toList());
  }

  @Override
  public AncillaryResponse updateAncillary(UUID id, AncillaryRequest request) {
    Ancillary ancillary = ancillaryRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Ancillary", "id", id));

    ancillary.setType(request.getType());
    ancillary.setSubType(request.getSubType());
    ancillary.setRfisc(request.getRfisc());
    ancillary.setName(request.getName());
    ancillary.setDescription(request.getDescription());
    ancillary.setMetadata(request.getMetadata());
    ancillary.setDisplayOrder(request.getDisplayOrder());

    Ancillary updated = ancillaryRepository.save(ancillary);
    // todo: fetch insurance converages by ancillary
    return AncillaryMapper.toResponse(updated, null);
  }

  @Override
  public void deleteAncillary(UUID id) {
    Ancillary ancillary = ancillaryRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Ancillary", "id", id));
    ancillaryRepository.delete(ancillary);
  }
}
