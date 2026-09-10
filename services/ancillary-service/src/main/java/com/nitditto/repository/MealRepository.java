package com.nitditto.repository;

import com.nitditto.model.Meal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MealRepository extends JpaRepository<Meal, UUID> {

    List<Meal> findByAirlineId(UUID airlineId);
    boolean existsByCodeAndAirlineId(String code, UUID airlineId);
    boolean existsByAirlineIdAndCodeAndIdNot(UUID airlineId, String code, UUID id);
  
}
