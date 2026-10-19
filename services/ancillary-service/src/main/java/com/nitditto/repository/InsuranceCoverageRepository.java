package com.nitditto.repository;

import com.nitditto.model.InsuranceCoverage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InsuranceCoverageRepository extends JpaRepository<InsuranceCoverage, UUID> {
    List<InsuranceCoverage> findByAncillaryId(UUID ancillaryId);

    List<InsuranceCoverage> findByAncillaryIdIn(List<UUID> ancillaryIds);

    List<InsuranceCoverage> findByAncillaryIdAndActiveTrue(UUID ancillaryId);

    void deleteByAncillaryId(UUID ancillaryId);
}
