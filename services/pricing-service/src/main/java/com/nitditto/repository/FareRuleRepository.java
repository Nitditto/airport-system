package com.nitditto.repository;

import com.nitditto.model.FareRules;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FareRuleRepository extends JpaRepository<FareRules, Long> {
    FareRules findByFareId(Long fareId);

    List<FareRules> findByAirlineId(Long airlineId);

    boolean existsByFareId(Long fareId);
}
