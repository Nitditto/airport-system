package com.nitditto.repository;

import com.nitditto.model.Flight;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FlightRepository extends JpaRepository<Flight, UUID> {

  @Query("""
        select f from Flight f
        where f.airlineId = :airlineId
        and (:depId is null or f.departureAirportId = :depId)
        and (:arrId is null or f.arrivalAirportId = :arrId)
      """)
  Page<Flight> findByAirlineId(@Param("airlineId") UUID airlineId, @Param("depId") UUID depId,
      @Param("arrId") UUID arrId, Pageable pageable);

  @Query("select f.id from Flight f where f.airlineId = :airlineId")
  List<UUID> findIdsByAirlineId(@Param("airlineId") UUID airlineId);

  boolean existsByFlightNumber(String flightNumber);

  boolean existsByFlightNumberAndIdNot(String flightNumber, UUID id);

  Optional<Flight> findByAirlineIdAndId(UUID airlineId, UUID id);

}
