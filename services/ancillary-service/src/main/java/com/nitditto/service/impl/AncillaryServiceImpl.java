package com.nitditto.service.impl;

import com.nitditto.client.AirlineClient;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.mapper.AncillaryMapper;
import com.nitditto.mapper.InsuranceCoverageMapper;
import com.nitditto.model.Ancillary;
import com.nitditto.model.InsuranceCoverage;
import com.nitditto.payload.request.AncillaryRequest;
import com.nitditto.payload.response.AirlineResponse;
import com.nitditto.payload.response.AncillaryResponse;
import com.nitditto.payload.response.InsuranceCoverageResponse;
import com.nitditto.repository.AncillaryRepository;
import com.nitditto.repository.InsuranceCoverageRepository;
import com.nitditto.service.AncillaryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AncillaryServiceImpl implements AncillaryService {

  private final AncillaryRepository ancillaryRepository;
    private final InsuranceCoverageRepository insuranceCoverageRepository;
    private final AirlineClient airlineClient;

  @Override
    @Transactional
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

        return this.toResponseWithCoverages(ancillary);
  }

  @Override
    public List<AncillaryResponse> getByAirlineId(UUID userId) {
        AirlineResponse airlineResponse = airlineClient.getAirlineByOwner(userId).getData();
        final List<Ancillary> ancillaries = ancillaryRepository.findByAirlineId(airlineResponse.getId());
        if (ancillaries.isEmpty()) {
            return List.of();
            }

        final List<UUID> ancillaryIds = ancillaries.stream().map(Ancillary::getId).distinct().toList();
        final Map<UUID, List<InsuranceCoverage>> coveragesByAncillaryId =
                insuranceCoverageRepository.findByAncillaryIdIn(ancillaryIds).stream()
                        .collect(Collectors.groupingBy(InsuranceCoverage::getAncillaryId));

        final List<AncillaryResponse> responses = new ArrayList<>(ancillaries.size());
        for (final Ancillary ancillary : ancillaries) {
            final List<InsuranceCoverageResponse> coverageResponses =
                    coveragesByAncillaryId.getOrDefault(ancillary.getId(), List.of()).stream()
                            .map(coverage -> InsuranceCoverageMapper.toResponse(coverage, ancillary))
                            .toList();
            responses.add(AncillaryMapper.toResponse(ancillary, coverageResponses));
        }
        return responses;
  }

  @Override
    @Transactional
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
        return this.toResponseWithCoverages(updated);
  }

  @Override
    @Transactional
  public void deleteAncillary(UUID id) {
    Ancillary ancillary = ancillaryRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Ancillary", "id", id));
    ancillaryRepository.delete(ancillary);
  }

    private AncillaryResponse toResponseWithCoverages(final Ancillary ancillary) {
        final List<InsuranceCoverageResponse> coverages = insuranceCoverageRepository
                .findByAncillaryId(ancillary.getId()).stream()
                .map(coverage -> InsuranceCoverageMapper.toResponse(coverage, ancillary))
                .toList();
        return AncillaryMapper.toResponse(ancillary, coverages);
    }
}
