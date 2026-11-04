package com.nitditto.repository;

import com.nitditto.model.Booking;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking> {

    Page<Booking> findByUserId(UUID userId, Pageable pageable);

    long countByFlightInstanceId(UUID flightInstanceId);

    boolean existsByBookingReference(String bookingReference);

    // Khoá hàng để hai luồng xác nhận (webhook từ payment-service và tự đối soát khi đọc danh sách) xếp hàng, không xác nhận hai lần.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") UUID id);
}
