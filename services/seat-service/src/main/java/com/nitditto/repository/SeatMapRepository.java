package com.nitditto.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nitditto.model.SeatMap;

public interface SeatMapRepository extends JpaRepository<SeatMap, UUID> {
    Optional<SeatMap> findByCabinClassId(UUID cabinClassId);

    List<SeatMap> findByCabinClassIdIn(List<UUID> cabinClassIds);

    boolean existsByCabinClassId(UUID cabinClassId);

    boolean existsByCabinClassIdAndIdNot(UUID cabinClassId, UUID id);

    void deleteByCabinClassId(UUID cabinClassId);
}
