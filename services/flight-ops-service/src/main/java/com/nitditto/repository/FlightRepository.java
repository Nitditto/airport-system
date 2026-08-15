package com.nitditto.repository;

import com.nitditto.model.Flight;
import com.nitditto.payload.response.FlightResponse;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FlightRepository extends JpaRepository<Flight, Long> {

  @Query("""
        select f from Flight f
        where f.airlineId = :airlineId 
        and (:depId is null or f.departureAirportId = :depId)
        and (:arrId is null or f.arrivalAirportId = :arrId)
      """)
  Page<Flight> findByAirlineId(@Param("airlineId") Long airlineId, @Param("depId") Long depId,
      @Param("arrId") Long arrId, Pageable pageable);

  boolean existsByFlightNumber(String flightNumber);

  boolean existsByFlightNumberAndIdNot(String flightNumber, Long id);
  Optional<Flight> findByAirlineIdAndId(Long airlineId, Long id);

}
