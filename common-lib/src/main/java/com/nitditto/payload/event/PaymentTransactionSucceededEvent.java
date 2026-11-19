package com.nitditto.payload.event;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Integration event: một payment đã thanh toán thành công và đã commit (topic
 * {@code kafka.topics.payment-transaction-succeeded}). Key = {@code bookingId} để mọi event của cùng một booking (thành
 * công / thất bại) nằm cùng partition, giữ đúng thứ tự.
 */
public record PaymentTransactionSucceededEvent(
    UUID eventId,
    Instant occurredAt,
    UUID paymentId,
    UUID bookingId,
    UUID userId,
    Double amount,
    String transactionId,
    String providerPaymentId,
    LocalDateTime paidAt) {
}
