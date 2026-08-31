package com.nitditto.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nitditto.model.SeatMap;

public interface SeatMapRepository extends JpaRepository<SeatMap, UUID> {
    Optional<SeatMap> findByCabinClassId(UUID cabinClassId);

    boolean existsByCabinClassId(UUID cabinClassId);

    boolean existsByCabinClassIdAndIdNot(UUID cabinClassId, UUID id);

    void deleteByCabinClassId(UUID cabinClassId);
}
