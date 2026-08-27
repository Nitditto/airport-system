package com.nitditto.repository;

import com.nitditto.model.BaggagePolicy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BaggagePolicyRepository extends JpaRepository<BaggagePolicy, UUID> {

    BaggagePolicy findByFareId(UUID fareId);

    List<BaggagePolicy> findByFareIdIn(List<UUID> fareIds);

    List<BaggagePolicy> findByAirlineId(UUID airlineId);

    boolean existsByFareId(UUID fareId);

    void deleteByFareId(UUID fareId);
}
