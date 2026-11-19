package com.nitditto.payload.event;

import java.time.Instant;
import java.util.UUID;

/** Integration event: một lần thanh toán thất bại (topic {@code kafka.topics.payment-transaction-failed}, key = bookingId). */
public record PaymentTransactionFailedEvent(
    UUID eventId,
    Instant occurredAt,
    UUID paymentId,
    UUID bookingId,
    UUID userId,
    Double amount,
    String transactionId,
    String failureReason) {
}
