package com.nitditto.repository;

import com.nitditto.model.FlightInstance;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlightInstanceRepository extends JpaRepository<FlightInstance, Long> {

  @Query("""
          select fi from FlightInstance fi
          where fi.airlineId = :airlineId
          and (:departureAirportId is null or fi.departureAirportId = :departureAirportId)
          and (:arrivalAirportId is null or fi.arrivalAirportId = :arrivalAirportId)
          and (:flightId is null or fi.flight.id = :flightId)
          and (:dayStart is null or fi.departureDateTime >= :dayStart)
          and (:dayEnd is null or fi.arrivalDateTime <= :dayEnd)
      """)
  Page<FlightInstance> findByAirlineId(@Param("airlineId") Long airlineId,
      @Param("departureAirportId") Long departureAirportId,
      @Param("arrivalAirportId") Long arrivalAirportId,
      @Param("flightId") Long flightId,
      @Param("dayStart") LocalDateTime dayStart,
      @Param("dayEnd") LocalDateTime dayEnd,
      Pageable pageable);
}
