package com.nitditto.repository;

import com.nitditto.model.FlightSchedule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlightScheduleRepository extends JpaRepository<FlightSchedule, Long> {

  List<FlightSchedule> findByFlightAirlineId(Long airlineId);


}
