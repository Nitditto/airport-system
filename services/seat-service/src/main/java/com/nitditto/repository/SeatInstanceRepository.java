package com.nitditto.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nitditto.model.SeatInstance;

public interface SeatInstanceRepository extends JpaRepository<SeatInstance, UUID> {

    List<SeatInstance> findByFlightInstanceCabinId(UUID flightInstanceCabinId);

    boolean existsByFlightInstanceCabinId(UUID flightInstanceCabinId);

    void deleteByFlightInstanceCabinId(UUID flightInstanceCabinId);
}
