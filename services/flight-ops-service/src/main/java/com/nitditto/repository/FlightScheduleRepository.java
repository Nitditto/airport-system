package com.nitditto.repository;

import com.nitditto.model.FlightSchedule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlightScheduleRepository extends JpaRepository<FlightSchedule, UUID> {

  List<FlightSchedule> findByFlightIdIn(List<UUID> flightIds);

  boolean existsByFlightId(UUID flightId);

}
