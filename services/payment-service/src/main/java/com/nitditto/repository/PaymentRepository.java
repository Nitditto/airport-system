package com.nitditto.repository;

import com.nitditto.enums.PaymentStatus;
import com.nitditto.model.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    boolean existsByBookingIdAndStatus(UUID bookingId, PaymentStatus status);

    // Payment đã thu tiền của booking (SUCCESS, hoặc REFUNDED nếu đã hoàn): dùng để hoàn tiền / tra cứu idempotent.
    List<Payment> findByBookingIdAndStatusIn(UUID bookingId, java.util.Collection<PaymentStatus> statuses);

    List<Payment> findByBookingIdIn(List<UUID> bookingIds);

    Page<Payment> findByUserId(UUID userId, Pageable pageable);
}
