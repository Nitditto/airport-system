package com.nitditto.service;


import com.nitditto.payload.request.InsuranceCoverageRequest;
import com.nitditto.payload.response.InsuranceCoverageResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface InsuranceCoverageService {

    InsuranceCoverageResponse createCoverage(InsuranceCoverageRequest request);

    InsuranceCoverageResponse updateCoverage(UUID id, InsuranceCoverageRequest request);

    void deleteCoverage(UUID id);

    InsuranceCoverageResponse getCoverage(UUID id);

    List<InsuranceCoverageResponse> getCoverageByAncillaryId(UUID ancillaryId);

    List<InsuranceCoverageResponse> getActiveCoverageByAncillaryId(UUID ancillaryId);

    Page<InsuranceCoverageResponse> getAllCoverages(Pageable pageable);
}
