package com.nitditto.event;

import com.nitditto.payload.event.EventIds;
import com.nitditto.payload.event.PaymentTransactionSucceededEvent;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain event nội bộ (Spring event) khi payment chuyển sang SUCCESS. Mang đủ dữ liệu để PaymentOutcomePublisher dựng
 * integration event Kafka mà không phải đọc lại DB sau commit.
 */
public record PaymentSucceededEvent(UUID paymentId, UUID bookingId, UUID userId, Double amount,
    String transactionId, String providerPaymentId, LocalDateTime paidAt) {

  /** eventId sinh tại thời điểm publish (CLAUDE.md §9). */
  public PaymentTransactionSucceededEvent toIntegrationEvent() {
    return new PaymentTransactionSucceededEvent(EventIds.newEventId(), Instant.now(), this.paymentId, this.bookingId,
        this.userId, this.amount, this.transactionId, this.providerPaymentId, this.paidAt);
  }
}
