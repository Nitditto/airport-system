package com.nitditto.repository;

import com.nitditto.model.FlightInstanceCabin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface FlightInstanceCabinRepository extends JpaRepository<FlightInstanceCabin, UUID> {

    /**
     * Cộng/trừ số ghế đã bán ngay trong câu UPDATE (khoá hàng của DB), không đọc-sửa-ghi ở Java: hai giao dịch chốt
     * ghế khác nhau cùng cabin không ghi đè nhau. Kẹp về 0 thay vì lỗi để free() vẫn chạy khi bộ đếm đã lệch.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update FlightInstanceCabin c
               set c.bookedSeats = case when c.bookedSeats + :delta < 0 then 0 else c.bookedSeats + :delta end,
                   c.updatedAt = :now
             where c.id = :id""")
    int adjustBookedSeats(@Param("id") UUID id, @Param("delta") int delta, @Param("now") Instant now);

    Page<FlightInstanceCabin> findByFlightInstanceId(UUID flightInstanceId, Pageable pageable);

    Optional<FlightInstanceCabin> findByFlightInstanceIdAndCabinClassId(UUID flightInstanceId, UUID cabinClassId);

    boolean existsByFlightInstanceIdAndCabinClassId(UUID flightInstanceId, UUID cabinClassId);

    boolean existsByFlightInstanceIdAndCabinClassIdAndIdNot(UUID flightInstanceId, UUID cabinClassId, UUID id);
}
