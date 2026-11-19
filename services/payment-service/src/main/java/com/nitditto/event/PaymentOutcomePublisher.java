package com.nitditto.event;

import com.nitditto.payload.event.PaymentTransactionFailedEvent;
import com.nitditto.payload.event.PaymentTransactionSucceededEvent;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.nitditto.client.BookingClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Nơi DUY NHẤT xử lý tác dụng phụ sau khi kết quả thanh toán đã commit.
 *
 * <p>Thành công: ① gửi Kafka {@code payment.transaction.succeeded} (đường bền vững, booking-service retry được), rồi ② gọi HTTP
 * confirm-payment để người dùng thấy CONFIRMED ngay. Kafka đi trước để nếu HTTP treo/lỗi thì message vẫn đã được gửi.
 * booking-service.confirmPayment idempotent nên nhận cả hai đường là an toàn.
 *
 * <p>Thất bại: chỉ gửi Kafka {@code payment.transaction.failed}; không ai nhả ghế ở đây (booking-service là bên duy nhất chốt/nhả
 * ghế, ghế tự hết hạn giữ theo TTL).
 *
 * <p>Phải chạy AFTER_COMMIT: booking-service gọi ngược payment-service kiểm tra SUCCESS, gửi trước commit nó sẽ thấy
 * PENDING. Mọi lỗi chỉ ghi log, không ném ra: thanh toán đã ghi nhận xong, không được làm hỏng response cho người dùng.
 */
@Slf4j
@Component
public class PaymentOutcomePublisher {

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final BookingClient bookingClient;
  private final String succeededTopic;
  private final String failedTopic;

  public PaymentOutcomePublisher(final KafkaTemplate<String, Object> kafkaTemplate, final BookingClient bookingClient,
      @Value("${kafka.topics.payment-transaction-succeeded}") final String succeededTopic,
      @Value("${kafka.topics.payment-transaction-failed}") final String failedTopic) {
    this.kafkaTemplate = kafkaTemplate;
    this.bookingClient = bookingClient;
    this.succeededTopic = succeededTopic;
    this.failedTopic = failedTopic;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onSucceeded(final PaymentSucceededEvent event) {
    final PaymentTransactionSucceededEvent integration = event.toIntegrationEvent();
    this.publishQuietly(this.succeededTopic, event.bookingId(), integration.eventId(), integration);
    this.confirmBookingQuietly(event);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onFailed(final PaymentFailedEvent event) {
    final PaymentTransactionFailedEvent integration = event.toIntegrationEvent();
    this.publishQuietly(this.failedTopic, event.bookingId(), integration.eventId(), integration);
  }

  private void publishQuietly(final String topic, final UUID bookingId, final UUID eventId, final Object payload) {
    final String key = String.valueOf(bookingId);
    final String type = payload.getClass().getSimpleName();
    try {
      this.kafkaTemplate.send(topic, key, payload).whenComplete((result, ex) -> {
        if (ex != null) {
          log.error("Failed to publish {} eventId={} to {} (bookingId={})", type, eventId, topic, key, ex);
        } else {
          log.info("Published {} eventId={} to {}-{}@{} (bookingId={})", type, eventId, topic,
              result.getRecordMetadata().partition(), result.getRecordMetadata().offset(), key);
        }
      });
    } catch (final RuntimeException ex) {
      // vd. broker không phản hồi metadata trong max.block.ms
      log.error("Could not send {} eventId={} to {} (bookingId={}): {}", type, eventId, topic, key, ex.getMessage(), ex);
    }
  }

  private void confirmBookingQuietly(final PaymentSucceededEvent event) {
    try {
      this.bookingClient.confirmPayment(event.bookingId());
      log.info("Booking {} confirmed after payment {}", event.bookingId(), event.paymentId());
    } catch (final RuntimeException ex) {
      log.error("Payment {} succeeded but booking {} could not be confirmed via HTTP yet "
          + "(Kafka event / reconcile-on-read will retry): {}", event.paymentId(), event.bookingId(), ex.getMessage());
    }
  }
}
