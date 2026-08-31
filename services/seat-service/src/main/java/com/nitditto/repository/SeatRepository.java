package com.nitditto.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.nitditto.model.Seat;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    boolean existsBySeatMapId(UUID seatMapId);

    Page<Seat> findBySeatMapId(UUID seatMapId, Pageable pageable);

    void deleteBySeatMapId(UUID seatMapId);

    void deleteByCabinClassId(UUID cabinClassId);
}
