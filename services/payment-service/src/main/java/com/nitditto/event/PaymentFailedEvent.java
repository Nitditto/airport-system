package com.nitditto.event;

import com.nitditto.payload.event.EventIds;
import com.nitditto.payload.event.PaymentTransactionFailedEvent;
import java.time.Instant;
import java.util.UUID;

/** Domain event nội bộ (Spring event) khi một lần thanh toán chuyển sang FAILED. */
public record PaymentFailedEvent(UUID paymentId, UUID bookingId, UUID userId, Double amount, String transactionId,
    String failureReason) {

  /** eventId sinh tại thời điểm publish (CLAUDE.md §9). */
  public PaymentTransactionFailedEvent toIntegrationEvent() {
    return new PaymentTransactionFailedEvent(EventIds.newEventId(), Instant.now(), this.paymentId, this.bookingId,
        this.userId, this.amount, this.transactionId, this.failureReason);
  }
}
