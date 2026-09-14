package com.nitditto.payload.dto;

import com.nitditto.enums.PaymentGateway;
import com.nitditto.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentDTO {
    private UUID id;

    private UUID userId;
    private String userName;
    private UUID bookingId;

    private PaymentStatus paymentStatus;
    private PaymentGateway paymentGateway;
    private Double amount;
    private String transactionId;
    private String gatewayPaymentId;
    private String gatewayOrderId;
    private String gatewaySignature;

    private String description;
    private String failureReason;
    private LocalDateTime initiatedAt;
    private LocalDateTime completedAt;

    private Boolean notificationSent;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
