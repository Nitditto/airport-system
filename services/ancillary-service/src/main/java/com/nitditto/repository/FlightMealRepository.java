package com.nitditto.repository;

import com.nitditto.model.FlightMeal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FlightMealRepository extends JpaRepository<FlightMeal, UUID> {

    List<FlightMeal> findByFlightId(UUID flightId);

    boolean existsByFlightIdAndMealId(UUID flightId, UUID mealId);

    boolean existsByFlightIdAndMealIdAndIdNot(UUID flightId, UUID mealId, UUID id);
}
