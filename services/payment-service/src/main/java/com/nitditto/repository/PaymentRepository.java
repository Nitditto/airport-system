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

    // Payment đang chờ gần nhất của booking cho một cổng: để `initiate` gọi lặp trả lại link cũ.
    java.util.Optional<Payment> findFirstByBookingIdAndProviderAndStatusOrderByCreatedAtDesc(UUID bookingId,
            com.nitditto.enums.PaymentGateway provider, PaymentStatus status);

    /**
     * Payment còn chờ kết quả cần đối soát với cổng: tạo trong khoảng [from, to] và chưa hỏi gần đây. Hỏi lâu chưa hỏi trước
     * để payment "không rõ" không làm các payment khác bị đói lượt.
     */
    @org.springframework.data.jpa.repository.Query("select p from Payment p where p.provider = :provider "
            + "and p.status = :status and p.createdAt >= :from and p.createdAt <= :to "
            + "and (p.lastReconciledAt is null or p.lastReconciledAt < :askedBefore) "
            + "order by p.lastReconciledAt asc, p.createdAt asc")
    List<Payment> findReconcilable(@org.springframework.data.repository.query.Param("provider") com.nitditto.enums.PaymentGateway provider,
            @org.springframework.data.repository.query.Param("status") PaymentStatus status,
            @org.springframework.data.repository.query.Param("from") java.time.Instant from,
            @org.springframework.data.repository.query.Param("to") java.time.Instant to,
            @org.springframework.data.repository.query.Param("askedBefore") java.time.Instant askedBefore,
            Pageable pageable);

    List<Payment> findByBookingIdIn(List<UUID> bookingIds);

    Page<Payment> findByUserId(UUID userId, Pageable pageable);
}
