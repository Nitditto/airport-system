package com.nitditto.repository;

import com.nitditto.model.FareRules;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FareRuleRepository extends JpaRepository<FareRules, UUID> {
    FareRules findByFareId(UUID fareId);

    List<FareRules> findByFareIdIn(List<UUID> fareIds);

    List<FareRules> findByAirlineId(UUID airlineId);

    boolean existsByFareId(UUID fareId);

    void deleteByFareId(UUID fareId);
}
