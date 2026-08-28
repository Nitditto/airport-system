package com.nitditto.repository;

import com.nitditto.model.Aircraft;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AircraftRepository extends JpaRepository<Aircraft, UUID> {

  List<Aircraft> findByAirlineId(UUID airlineId);

  boolean existsByCode(String code);

  Aircraft findByIdAndAirlineId(UUID id, UUID airlineId);
}
