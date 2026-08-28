package com.nitditto.repository;

import com.nitditto.model.Airport;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AirportRepository extends JpaRepository<Airport, UUID> {

  Optional<Airport> findByIataCode(String iataCode);

  List<Airport> findByCityId(UUID cityId);
}
