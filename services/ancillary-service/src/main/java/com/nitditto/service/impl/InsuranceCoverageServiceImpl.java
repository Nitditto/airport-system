package com.nitditto.service.impl;

import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.mapper.InsuranceCoverageMapper;
import com.nitditto.model.Ancillary;
import com.nitditto.model.InsuranceCoverage;
import com.nitditto.payload.request.InsuranceCoverageRequest;
import com.nitditto.payload.response.InsuranceCoverageResponse;
import com.nitditto.repository.AncillaryRepository;
import com.nitditto.repository.InsuranceCoverageRepository;
import com.nitditto.service.InsuranceCoverageService;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InsuranceCoverageServiceImpl implements InsuranceCoverageService {

    private final AncillaryRepository ancillaryRepository;
    private final InsuranceCoverageRepository insuranceCoverageRepository;

    @Override
    @Transactional
    public InsuranceCoverageResponse createCoverage(final InsuranceCoverageRequest request) {
        final Ancillary ancillary = this.ancillaryRepository.findById(request.getAncillaryId())
                .orElseThrow(() -> new ResourceNotFoundException("Ancillary", "id", request.getAncillaryId()));
        final InsuranceCoverage insuranceCoverage = InsuranceCoverageMapper.toEntity(request);
        final InsuranceCoverage savedCoverage = this.insuranceCoverageRepository.save(insuranceCoverage);
        return InsuranceCoverageMapper.toResponse(savedCoverage, ancillary);
    }

    @Override
    @Transactional
    public InsuranceCoverageResponse updateCoverage(final UUID id, final InsuranceCoverageRequest request) {
        final InsuranceCoverage insuranceCoverage = this.insuranceCoverageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Insurance Coverage", "id", id));

        Ancillary ancillary = null;
        if (request.getAncillaryId() != null) {
            ancillary = this.ancillaryRepository.findById(request.getAncillaryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ancillary", "id", request.getAncillaryId()));
        }
        InsuranceCoverageMapper.updateEntityFromRequest(insuranceCoverage, request, ancillary);
        final InsuranceCoverage savedCoverage = this.insuranceCoverageRepository.save(insuranceCoverage);

        if (ancillary == null) {
            ancillary = this.ancillaryRepository.findById(savedCoverage.getAncillaryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ancillary", "id",
                            savedCoverage.getAncillaryId()));
        }
        return InsuranceCoverageMapper.toResponse(savedCoverage, ancillary);
    }

    @Override
    @Transactional
    public void deleteCoverage(final UUID id) {
        if (!this.insuranceCoverageRepository.existsById(id)) {
            throw new ResourceNotFoundException("Insurance Coverage", "id", id);
        }
        this.insuranceCoverageRepository.deleteById(id);
    }

    @Override
    public InsuranceCoverageResponse getCoverage(final UUID id) {
        final InsuranceCoverage insuranceCoverage = this.insuranceCoverageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Insurance Coverage", "id", id));
        final Ancillary ancillary = this.ancillaryRepository.findById(insuranceCoverage.getAncillaryId())
                .orElseThrow(() -> new ResourceNotFoundException("Ancillary", "id",
                        insuranceCoverage.getAncillaryId()));
        return InsuranceCoverageMapper.toResponse(insuranceCoverage, ancillary);
    }

    @Override
    public List<InsuranceCoverageResponse> getCoverageByAncillaryId(final UUID ancillaryId) {
        final Ancillary ancillary = this.ancillaryRepository.findById(ancillaryId)
                .orElseThrow(() -> new ResourceNotFoundException("Ancillary", "id", ancillaryId));
        return this.insuranceCoverageRepository.findByAncillaryId(ancillaryId)
                .stream().map(coverage -> InsuranceCoverageMapper.toResponse(coverage, ancillary))
                .collect(Collectors.toList());
    }

    @Override
    public List<InsuranceCoverageResponse> getActiveCoverageByAncillaryId(final UUID ancillaryId) {
        final Ancillary ancillary = this.ancillaryRepository.findById(ancillaryId)
                .orElseThrow(() -> new ResourceNotFoundException("Ancillary", "id", ancillaryId));
        return this.insuranceCoverageRepository.findByAncillaryIdAndActiveTrue(ancillaryId)
                .stream().map(coverage -> InsuranceCoverageMapper.toResponse(coverage, ancillary))
                .collect(Collectors.toList());
    }

    @Override
    public Page<InsuranceCoverageResponse> getAllCoverages(final Pageable pageable) {
        final Page<InsuranceCoverage> page = this.insuranceCoverageRepository.findAll(pageable);

        final List<UUID> ancillaryIds = page.getContent().stream()
                .map(InsuranceCoverage::getAncillaryId).distinct().collect(Collectors.toList());
        final Map<UUID, Ancillary> ancillaryById = this.ancillaryRepository.findAllById(ancillaryIds)
                .stream().collect(Collectors.toMap(Ancillary::getId, Function.identity()));

        return page.map(coverage -> InsuranceCoverageMapper.toResponse(
                coverage, ancillaryById.get(coverage.getAncillaryId())));
    }
}
