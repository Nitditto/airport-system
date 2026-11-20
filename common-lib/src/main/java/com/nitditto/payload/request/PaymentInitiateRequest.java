package com.nitditto.payload.request;

import com.nitditto.enums.PaymentGateway;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInitiateRequest {

    @NotNull(message = "User ID is mandatory")
    private UUID userId;

    @NotNull(message = "Booking ID is required")
    private UUID bookingId;

    @NotNull(message = "Payment gateway is mandatory")
    private PaymentGateway paymentGateway;

    @NotNull(message = "Amount is mandatory")
    @Positive(message = "Amount must be positive")
    private Double amount;

    private String description;

    /**
     * Thời điểm booking hết hạn thanh toán (booking-service tính từ {@code booking.payment-window-minutes}). Link thanh toán
     * của cổng hết hạn đúng lúc này để khách không trả được sau khi booking đã hết hạn. Null = dùng thời hạn mặc định của cổng.
     */
    private java.time.Instant expiresAt;
}
