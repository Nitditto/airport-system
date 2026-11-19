package com.nitditto.model;

import com.nitditto.entity.BaseEntity;
import com.nitditto.enums.PaymentGateway;
import com.nitditto.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "payments", uniqueConstraints = {
        // MySQL không có unique một phần: cột này chỉ được gán khi SUCCESS nên NULL ở các dòng khác (NULL không đụng nhau).
        @UniqueConstraint(name = "uk_payment_paid_booking", columnNames = "paid_booking_id")
}, indexes = {
        @Index(name = "idx_payment_booking_status", columnList = "booking_id, status"),
        @Index(name = "idx_payment_user", columnList = "user_id"),
        @Index(name = "idx_payment_status_created", columnList = "status, created_at")
})
@Entity
public class Payment extends BaseEntity {

    @Column(name = "user_id", columnDefinition = "binary(16)", nullable = false)
    private UUID userId;

    @Column(name = "booking_id", columnDefinition = "binary(16)", nullable = false)
    private UUID bookingId;

    @Column(name = "amount")
    private Double amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider")
    private PaymentGateway provider;

    @Column(name = "provider_payment_id")
    private String providerPaymentId;

    @Column(name = "transaction_id")
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PaymentStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "refund_id")
    private String refundId;

    /** vnp_CreateDate gửi lúc tạo link VNPay (yyyyMMddHHmmss, GMT+7): VNPay đòi lại đúng giá trị này khi hoàn tiền. */
    @Column(name = "provider_create_date", length = 14)
    private String providerCreateDate;

    /** Link thanh toán đã tạo, để gọi lại `initiate` trả đúng link cũ thay vì tạo payment mới. */
    @Column(name = "checkout_url", length = 2048)
    private String checkoutUrl;

    /**
     * Bằng bookingId khi payment này là payment thành công của booking, NULL nếu không. Unique: DB bảo đảm một booking
     * chỉ có một payment thành công kể cả khi hai kết quả thanh toán về song song.
     */
    @Column(name = "paid_booking_id", columnDefinition = "binary(16)")
    private UUID paidBookingId;

    /** Lần gần nhất job đối soát tra cứu payment này ở cổng thanh toán: để không hỏi lại dồn dập. */
    @Column(name = "last_reconciled_at")
    private Instant lastReconciledAt;

}
