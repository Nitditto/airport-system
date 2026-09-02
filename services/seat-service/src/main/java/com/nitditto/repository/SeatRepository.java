package com.nitditto.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.nitditto.model.Seat;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    boolean existsBySeatMapId(UUID seatMapId);

    int countBySeatMapId(UUID seatMapId);

    Page<Seat> findBySeatMapId(UUID seatMapId, Pageable pageable);

    // Bản không phân trang: dùng khi cần TOÀN BỘ ghế của 1 seat map (vd. sinh SeatInstance),
    // không dùng để trả về client (mọi endpoint trả list phải phân trang).
    List<Seat> findBySeatMapId(UUID seatMapId);

    void deleteBySeatMapId(UUID seatMapId);

    void deleteByCabinClassId(UUID cabinClassId);
}
