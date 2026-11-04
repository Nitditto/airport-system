package com.nitditto.payment_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.nitditto.client.BookingClient;
import com.nitditto.enums.PaymentGateway;
import com.nitditto.enums.PaymentStatus;
import com.nitditto.event.KafkaTopics;
import com.nitditto.model.Payment;
import com.nitditto.payload.dto.PaymentDTO;
import com.nitditto.repository.PaymentRepository;
import com.nitditto.service.PaymentService;
import com.nitditto.service.gateway.VNPayService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Tích hợp thật: PaymentServiceImpl (transaction MySQL {@code airline_payment_test}) → commit → PaymentOutcomePublisher
 * (AFTER_COMMIT) → broker Kafka nhúng. BookingClient (HTTP) được mock để kiểm tra thứ tự Kafka → HTTP.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3306/airline_payment_test?createDatabaseIfNotExist=true",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "eureka.client.enabled=false",
    "vnpay.tmn-code=TESTTMN",
    "vnpay.hash-secret=" + PaymentOutcomeKafkaE2ETest.SECRET,
    "razorpay.api.key=rzp_test_dummy",
    "razorpay.api.secret=dummy"
})
@EmbeddedKafka(partitions = 3, topics = {KafkaTopics.PAYMENT_COMPLETED, KafkaTopics.PAYMENT_FAILED},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class PaymentOutcomeKafkaE2ETest {

  static final String SECRET = "kafka-e2e-hash-secret";
  private static final Duration TIMEOUT = Duration.ofSeconds(15);

  @Autowired
  private PaymentService paymentService;
  @Autowired
  private PaymentRepository paymentRepository;
  @Autowired
  private EmbeddedKafkaBroker broker;
  @MockitoBean
  private BookingClient bookingClient;

  private Consumer<String, String> consumer;

  @BeforeEach
  void setUp() {
    this.paymentRepository.deleteAll();
    final Map<String, Object> props = KafkaTestUtils.consumerProps(this.broker, "payment-e2e-" + UUID.randomUUID(), true);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    this.consumer = new KafkaConsumer<>(props);
    this.consumer.subscribe(List.of(KafkaTopics.PAYMENT_COMPLETED, KafkaTopics.PAYMENT_FAILED));
  }

  @AfterEach
  void tearDown() {
    this.consumer.close();
    this.paymentRepository.deleteAll();
  }

  @Test
  void signedVnpaySuccess_publishesPaymentCompletedKeyedByBooking_thenConfirmsBookingOverHttp() {
    final Payment payment = this.pendingPayment();

    final PaymentDTO result = this.paymentService.checkPaymentVNPay(vnpResult(payment, "00"));

    assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
    final ConsumerRecord<String, String> record = this.awaitRecord(KafkaTopics.PAYMENT_COMPLETED, payment);
    assertThat(record.key()).isEqualTo(payment.getBookingId().toString());
    assertThat(record.value())
        .contains(payment.getId().toString())
        .contains(payment.getBookingId().toString())
        .contains("14000001");
    // header __TypeId__ cho consumer JsonDeserializer phía booking-service
    assertThat(new String(record.headers().lastHeader("__TypeId__").value(), StandardCharsets.UTF_8))
        .isEqualTo("com.nitditto.event.PaymentCompletedEvent");
    verify(this.bookingClient, timeout(5000)).confirmPayment(payment.getBookingId());
  }

  @Test
  void repeatedSuccessCallback_publishesOnlyOnce() {
    final Payment payment = this.pendingPayment();

    this.paymentService.checkPaymentVNPay(vnpResult(payment, "00"));
    this.paymentService.checkPaymentVNPay(vnpResult(payment, "00"));

    this.awaitRecord(KafkaTopics.PAYMENT_COMPLETED, payment);
    assertThat(this.recordsFor(payment, Duration.ofSeconds(3))).isZero();
    verify(this.bookingClient, timeout(5000).times(1)).confirmPayment(payment.getBookingId());
  }

  @Test
  void signedVnpayCancel_publishesPaymentFailed_andNeverConfirmsBooking() throws Exception {
    final Payment payment = this.pendingPayment();

    final PaymentDTO result = this.paymentService.checkPaymentVNPay(vnpResult(payment, "24"));

    assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
    final ConsumerRecord<String, String> record = this.awaitRecord(KafkaTopics.PAYMENT_FAILED, payment);
    assertThat(record.key()).isEqualTo(payment.getBookingId().toString());
    assertThat(record.value()).contains(payment.getId().toString());
    Thread.sleep(1000);
    verify(this.bookingClient, never()).confirmPayment(any());
  }

  @Test
  void bookingServiceDown_stillPublishesKafka_andPaymentStaysSuccessful() {
    org.mockito.Mockito.when(this.bookingClient.confirmPayment(any()))
        .thenThrow(new RuntimeException("connect timed out"));
    final Payment payment = this.pendingPayment();

    final PaymentDTO result = this.paymentService.checkPaymentVNPay(vnpResult(payment, "00"));

    assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
    assertThat(this.awaitRecord(KafkaTopics.PAYMENT_COMPLETED, payment).key()).isEqualTo(payment.getBookingId().toString());
    assertThat(this.paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.SUCCESS);
    final InOrder order = inOrder(this.bookingClient);
    order.verify(this.bookingClient).confirmPayment(payment.getBookingId());
  }

  // Mỗi test có consumer group riêng đọc từ earliest nên thấy cả message của test trước: lọc theo key (bookingId).
  private ConsumerRecord<String, String> awaitRecord(final String topic, final Payment payment) {
    final String key = payment.getBookingId().toString();
    final long deadline = System.nanoTime() + TIMEOUT.toNanos();
    while (System.nanoTime() < deadline) {
      for (final ConsumerRecord<String, String> record : KafkaTestUtils.getRecords(this.consumer,
          Duration.ofMillis(500))) {
        if (record.topic().equals(topic) && key.equals(record.key())) {
          return record;
        }
      }
    }
    throw new AssertionError("No record on topic " + topic + " for booking " + key + " within " + TIMEOUT);
  }

  private long recordsFor(final Payment payment, final Duration window) {
    final String key = payment.getBookingId().toString();
    long count = 0;
    for (final ConsumerRecord<String, String> record : KafkaTestUtils.getRecords(this.consumer, window)) {
      if (key.equals(record.key())) {
        count++;
      }
    }
    return count;
  }

  private Payment pendingPayment() {
    return this.paymentRepository.save(Payment.builder()
        .userId(UUID.randomUUID())
        .bookingId(UUID.randomUUID())
        .amount(250.0)
        .provider(PaymentGateway.VNPAY)
        .status(PaymentStatus.PENDING)
        .build());
  }

  private static Map<String, String> vnpResult(final Payment payment, final String responseCode) {
    final Map<String, String> fields = new HashMap<>();
    fields.put("vnp_TxnRef", payment.getId().toString());
    fields.put("vnp_Amount", "25000");
    fields.put("vnp_ResponseCode", responseCode);
    fields.put("vnp_TransactionNo", "14000001");
    final StringBuilder data = new StringBuilder();
    new TreeMap<>(fields).forEach((k, v) -> data.append(k).append('=')
        .append(URLEncoder.encode(v, StandardCharsets.US_ASCII)).append('&'));
    data.setLength(data.length() - 1);
    fields.put("vnp_SecureHash", VNPayService.hmacSHA512(SECRET, data.toString()));
    return fields;
  }
}
