package com.nitditto.repository;

import com.nitditto.model.FlightInstanceCabin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FlightInstanceCabinRepository extends JpaRepository<FlightInstanceCabin, UUID> {

    Page<FlightInstanceCabin> findByFlightInstanceId(UUID flightInstanceId, Pageable pageable);

    Optional<FlightInstanceCabin> findByFlightInstanceIdAndCabinClassId(UUID flightInstanceId, UUID cabinClassId);

    boolean existsByFlightInstanceIdAndCabinClassId(UUID flightInstanceId, UUID cabinClassId);

    boolean existsByFlightInstanceIdAndCabinClassIdAndIdNot(UUID flightInstanceId, UUID cabinClassId, UUID id);
}
