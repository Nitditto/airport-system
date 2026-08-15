package com.nitditto.repository;

import com.nitditto.model.Aircraft;
import com.nitditto.payload.response.AircraftResponse;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AircraftRepository extends JpaRepository<Aircraft, Long> {

  List<Aircraft> findByAirlineId(Long airlineId);

  boolean existsByCode(String code);

  Aircraft findByIdAndAirlineId(Long id, Long airlineId);
}
