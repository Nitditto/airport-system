package com.nitditto.booking_service;

import static org.mockito.Mockito.after;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nitditto.enums.BookingStatus;
import com.nitditto.event.KafkaTopics;
import com.nitditto.event.PaymentCompletedEvent;
import com.nitditto.event.PaymentFailedEvent;
import com.nitditto.exception.BusinessException;
import com.nitditto.service.BookingConfirmationService;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Tích hợp thật phía consumer: broker Kafka nhúng → ErrorHandlingDeserializer/JsonDeserializer (cấu hình yaml) →
 * PaymentEventConsumer → DefaultErrorHandler (KafkaConsumerConfig). BookingConfirmationService được mock để quan sát
 * số lần gọi (retry / không retry).
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3306/airline_booking_test?createDatabaseIfNotExist=true",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "eureka.client.enabled=false"
})
@EmbeddedKafka(partitions = 1, topics = {KafkaTopics.PAYMENT_COMPLETED, KafkaTopics.PAYMENT_FAILED},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class PaymentEventConsumerKafkaE2ETest {

  @Autowired
  private EmbeddedKafkaBroker broker;
  @MockitoBean
  private BookingConfirmationService bookingConfirmationService;

  private KafkaTemplate<String, Object> producer;

  @BeforeEach
  void setUp() {
    // Producer giống hệt payment-service: key String, value JsonSerializer (kèm header __TypeId__).
    final Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, this.broker.getBrokersAsString());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    this.producer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
  }

  @AfterEach
  void tearDown() {
    reset(this.bookingConfirmationService);
  }

  @Test
  void paymentCompleted_confirmsTheBooking() throws Exception {
    final UUID bookingId = UUID.randomUUID();
    when(this.bookingConfirmationService.confirmPayment(bookingId)).thenReturn(BookingStatus.CONFIRMED);

    this.send(KafkaTopics.PAYMENT_COMPLETED, completed(bookingId));

    verify(this.bookingConfirmationService, timeout(15_000)).confirmPayment(bookingId);
  }

  @Test
  void transientFailure_isRetried_untilItSucceeds() throws Exception {
    final UUID bookingId = UUID.randomUUID();
    when(this.bookingConfirmationService.confirmPayment(bookingId))
        .thenThrow(new RuntimeException("seat-service unavailable"))
        .thenThrow(new RuntimeException("seat-service unavailable"))
        .thenReturn(BookingStatus.CONFIRMED);

    this.send(KafkaTopics.PAYMENT_COMPLETED, completed(bookingId));

    // FixedBackOff(2s, 3 lần): lần 1 lỗi, lần 2 lỗi, lần 3 thành công
    verify(this.bookingConfirmationService, timeout(20_000).times(3)).confirmPayment(bookingId);
  }

  @Test
  void businessError_isNotRetried() throws Exception {
    final UUID bookingId = UUID.randomUUID();
    when(this.bookingConfirmationService.confirmPayment(bookingId))
        .thenThrow(new BusinessException("Booking is EXPIRED and cannot be confirmed"));

    this.send(KafkaTopics.PAYMENT_COMPLETED, completed(bookingId));

    verify(this.bookingConfirmationService, timeout(15_000)).confirmPayment(bookingId);
    // chờ lâu hơn một chu kỳ backoff (2s) để chắc chắn không có lần gọi thứ hai
    verify(this.bookingConfirmationService, after(5_000).times(1)).confirmPayment(bookingId);
  }

  @Test
  void paymentFailed_doesNotTouchTheBooking() throws Exception {
    final UUID bookingId = UUID.randomUUID();
    final UUID marker = UUID.randomUUID();
    when(this.bookingConfirmationService.confirmPayment(marker)).thenReturn(BookingStatus.CONFIRMED);

    this.send(KafkaTopics.PAYMENT_FAILED, PaymentFailedEvent.builder()
        .paymentId(UUID.randomUUID()).bookingId(bookingId).failureReason("cancelled").build());
    this.send(KafkaTopics.PAYMENT_COMPLETED, completed(marker));

    verify(this.bookingConfirmationService, timeout(15_000)).confirmPayment(marker);
    verify(this.bookingConfirmationService, after(2_000).never()).confirmPayment(bookingId);
  }

  private void send(final String topic, final Object event) throws Exception {
    final UUID key = event instanceof PaymentCompletedEvent c ? c.getBookingId()
        : ((PaymentFailedEvent) event).getBookingId();
    this.producer.send(topic, key.toString(), event).get();
  }

  private static PaymentCompletedEvent completed(final UUID bookingId) {
    return PaymentCompletedEvent.builder()
        .paymentId(UUID.randomUUID()).bookingId(bookingId).userId(UUID.randomUUID()).amount(250.0)
        .transactionId("14000001").paidAt(LocalDateTime.now()).build();
  }
}
