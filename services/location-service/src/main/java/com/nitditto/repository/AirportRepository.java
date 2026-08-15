package com.nitditto.repository;

import com.nitditto.model.Airport;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AirportRepository extends JpaRepository<Airport, Long> {

  Optional<Airport> findByIataCode(String iataCode);

  List<Airport> findByCityId(Long cityId);
}
