package com.nitditto.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sự kiện nội bộ (Spring event) phát ra khi payment chuyển sang SUCCESS. Mang đủ dữ liệu để PaymentOutcomePublisher
 * dựng message Kafka mà không phải đọc lại DB sau commit.
 */
public record PaymentSucceededEvent(UUID paymentId, UUID bookingId, UUID userId, Double amount,
    String transactionId, String providerPaymentId, LocalDateTime paidAt) {

  public PaymentCompletedEvent toKafkaEvent() {
    return PaymentCompletedEvent.builder()
        .paymentId(this.paymentId)
        .bookingId(this.bookingId)
        .userId(this.userId)
        .amount(this.amount)
        .transactionId(this.transactionId)
        .providerPaymentId(this.providerPaymentId)
        .paidAt(this.paidAt)
        .build();
  }
}
