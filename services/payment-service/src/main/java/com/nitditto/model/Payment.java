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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "payments", indexes = {
        @Index(name = "idx_payment_booking_status", columnList = "booking_id, status"),
        @Index(name = "idx_payment_user", columnList = "user_id")
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

}
