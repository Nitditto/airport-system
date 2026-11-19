package com.nitditto.payment_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nitditto.client.BookingClient;
import com.nitditto.payload.event.PaymentTransactionFailedEvent;
import com.nitditto.payload.event.PaymentTransactionSucceededEvent;
import com.nitditto.event.PaymentFailedEvent;
import com.nitditto.event.PaymentOutcomePublisher;
import com.nitditto.event.PaymentSucceededEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class PaymentOutcomePublisherTest {

  private static final String SUCCEEDED = "payment.transaction.succeeded";
  private static final String FAILED = "payment.transaction.failed";

  @Mock
  private KafkaTemplate<String, Object> kafkaTemplate;
  @Mock
  private BookingClient bookingClient;

  private PaymentOutcomePublisher publisher;

  private final UUID bookingId = UUID.randomUUID();
  private final PaymentSucceededEvent succeeded = new PaymentSucceededEvent(UUID.randomUUID(), this.bookingId,
      UUID.randomUUID(), 250.0, "TXN-1", "PROVIDER-1", LocalDateTime.now());

  @BeforeEach
  void setUp() {
    this.publisher = new PaymentOutcomePublisher(this.kafkaTemplate, this.bookingClient, SUCCEEDED, FAILED);
  }

  @Test
  void onSucceeded_sendsKafkaKeyedByBooking_beforeConfirmingOverHttp() {
    when(this.kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(pending());

    this.publisher.onSucceeded(this.succeeded);

    final InOrder order = inOrder(this.kafkaTemplate, this.bookingClient);
    order.verify(this.kafkaTemplate).send(eq(SUCCEEDED), eq(this.bookingId.toString()),
        any(PaymentTransactionSucceededEvent.class));
    order.verify(this.bookingClient).confirmPayment(this.bookingId);
  }

  @Test
  void onSucceeded_stillConfirmsOverHttp_whenKafkaSendFails() {
    when(this.kafkaTemplate.send(anyString(), anyString(), any()))
        .thenThrow(new RuntimeException("metadata not available after 3000 ms"));

    assertThatCode(() -> this.publisher.onSucceeded(this.succeeded)).doesNotThrowAnyException();

    verify(this.bookingClient).confirmPayment(this.bookingId);
  }

  @Test
  void onSucceeded_doesNotThrow_whenBookingServiceIsDown() {
    when(this.kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(pending());
    when(this.bookingClient.confirmPayment(this.bookingId)).thenThrow(new RuntimeException("connect timed out"));

    assertThatCode(() -> this.publisher.onSucceeded(this.succeeded)).doesNotThrowAnyException();
  }

  @Test
  void onFailed_onlyPublishesKafka_neverConfirmsBooking() {
    when(this.kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(pending());
    final PaymentFailedEvent failed = new PaymentFailedEvent(UUID.randomUUID(), this.bookingId, UUID.randomUUID(),
        250.0, null, "cancelled");

    this.publisher.onFailed(failed);

    final ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
    verify(this.kafkaTemplate).send(eq(FAILED), eq(this.bookingId.toString()), sent.capture());
    assertThat(sent.getValue()).isInstanceOfSatisfying(PaymentTransactionFailedEvent.class, e -> {
      assertThat(e.eventId()).isNotNull();
      assertThat(e.occurredAt()).isNotNull();
      assertThat(e.paymentId()).isEqualTo(failed.paymentId());
      assertThat(e.failureReason()).isEqualTo("cancelled");
    });
    verify(this.bookingClient, never()).confirmPayment(any());
  }

  private static CompletableFuture<SendResult<String, Object>> pending() {
    return new CompletableFuture<>();
  }
}
