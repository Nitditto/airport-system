package com.nitditto.payment_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nitditto.enums.PaymentGateway;
import com.nitditto.enums.PaymentStatus;
import com.nitditto.model.Payment;
import com.nitditto.repository.PaymentRepository;
import com.nitditto.service.PendingPaymentReconciler;
import com.nitditto.service.gateway.VNPayQueryService;
import com.nitditto.service.gateway.VNPayQueryService.Outcome;
import com.nitditto.service.gateway.VNPayQueryService.Result;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Job đối soát payment VNPay trên MySQL thật ({@code airline_payment_test}). Chỉ lời gọi HTTP tới VNPay được mock.
 * Kiểm tra: chọn đúng payment cần hỏi (truy vấn thật), áp kết quả (thành công/thất bại/hết hạn), không động tới payment
 * đã chốt, và một payment lỗi không chặn các payment còn lại.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3306/airline_payment_test?createDatabaseIfNotExist=true",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "eureka.client.enabled=false",
    "spring.kafka.listener.auto-startup=false",
    "razorpay.api.key=rzp_test_dummy",
    "razorpay.api.secret=dummy",
    "vnpay.tmn-code=TESTTMN",
    "vnpay.hash-secret=reconcile-secret",
    "payment.reconcile.enabled=true",
    "payment.reconcile.initial-delay-ms=3600000"
})
class PendingPaymentReconcilerE2ETest {

  @Autowired
  private PendingPaymentReconciler reconciler;
  @Autowired
  private PaymentRepository paymentRepository;
  @Autowired
  private jakarta.persistence.EntityManager entityManager;
  @Autowired
  private org.springframework.transaction.support.TransactionTemplate transactionTemplate;
  @MockitoBean
  private VNPayQueryService queryService;
  // chặn gọi booking/Kafka thật khi payment chuyển trạng thái
  @MockitoBean
  private com.nitditto.client.BookingClient bookingClient;
  @MockitoBean
  private org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

  @BeforeEach
  void setUp() {
    reset(this.queryService);
    when(this.queryService.isConfigured()).thenReturn(true);
  }

  @AfterEach
  void cleanUp() {
    this.paymentRepository.deleteAll();
  }

  private Payment pending(final long ageMinutes) {
    final Payment p = Payment.builder().userId(UUID.randomUUID()).bookingId(UUID.randomUUID()).amount(250000.0)
        .provider(PaymentGateway.VNPAY).status(PaymentStatus.PENDING).build();
    final Payment saved = this.paymentRepository.saveAndFlush(p);
    // createdAt do Hibernate gán lúc insert và không cập nhật được qua entity: lùi bằng HQL để mô phỏng payment tạo từ lâu
    final Instant createdAt = Instant.now().minus(ageMinutes, ChronoUnit.MINUTES);
    this.transactionTemplate.executeWithoutResult(status -> this.entityManager
        .createQuery("update Payment p set p.createdAt = :t where p.id = :id").setParameter("t", createdAt)
        .setParameter("id", saved.getId()).executeUpdate());
    return this.paymentRepository.findById(saved.getId()).orElseThrow();
  }

  private static Result result(final Outcome outcome, final String amount) {
    return new Result(outcome, "00", "00", "ok", "14123456", amount);
  }

  private Payment reload(final Payment p) {
    return this.paymentRepository.findById(p.getId()).orElseThrow();
  }

  @Test
  void aPaymentPaidAtVnpayButNeverReportedBack_isConfirmed() {
    final Payment p = pending(10);
    when(this.queryService.query(any())).thenReturn(result(Outcome.PAID, "25000000"));

    assertThat(this.reconciler.reconcileOnce()).isEqualTo(1);

    final Payment after = reload(p);
    assertThat(after.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    assertThat(after.getPaidBookingId()).isEqualTo(p.getBookingId());
    assertThat(after.getTransactionId()).isEqualTo("14123456");
    assertThat(after.getLastReconciledAt()).isNotNull();
  }

  @Test
  void paidAtVnpayWithADifferentAmount_isNotConfirmed() {
    final Payment p = pending(10);
    when(this.queryService.query(any())).thenReturn(result(Outcome.PAID, "100"));

    this.reconciler.reconcileOnce();

    assertThat(reload(p).getStatus()).isEqualTo(PaymentStatus.PENDING);
  }

  @Test
  void aTransactionFailedAtVnpay_isMarkedFailed() {
    final Payment p = pending(10);
    when(this.queryService.query(any())).thenReturn(result(Outcome.FAILED, "25000000"));

    this.reconciler.reconcileOnce();

    assertThat(reload(p).getStatus()).isEqualTo(PaymentStatus.FAILED);
  }

  @Test
  void notFoundOrInProgress_staysPendingWhileTheLinkIsStillAlive_butFailsOnceItHasExpired() {
    final Payment fresh = pending(10);
    final Payment expired = pending(45);
    when(this.queryService.query(any())).thenReturn(result(Outcome.NOT_FOUND, null));

    this.reconciler.reconcileOnce();

    assertThat(reload(fresh).getStatus()).isEqualTo(PaymentStatus.PENDING);
    assertThat(reload(expired).getStatus()).isEqualTo(PaymentStatus.FAILED);
    assertThat(reload(expired).getFailureReason()).contains("expired");
  }

  @Test
  void anUnknownAnswer_changesNothing_butIsNotAskedAgainImmediately() {
    final Payment p = pending(10);
    when(this.queryService.query(any())).thenReturn(result(Outcome.UNKNOWN, null));

    assertThat(this.reconciler.reconcileOnce()).isEqualTo(1);
    assertThat(reload(p).getStatus()).isEqualTo(PaymentStatus.PENDING);

    assertThat(this.reconciler.reconcileOnce()).isZero(); // vừa hỏi, chưa tới hạn hỏi lại
    verify(this.queryService, times(1)).query(any());
  }

  @Test
  void tooYoungPaymentsPaymentsAlreadyDecidedAndOtherGatewaysAreNotAsked() {
    pending(0); // quá mới: khách có thể đang trả
    final Payment done = pending(10);
    done.setStatus(PaymentStatus.SUCCESS);
    this.paymentRepository.saveAndFlush(done);
    final Payment razorpay = pending(10);
    razorpay.setProvider(PaymentGateway.RAZORPAY);
    this.paymentRepository.saveAndFlush(razorpay);

    assertThat(this.reconciler.reconcileOnce()).isZero();
    verify(this.queryService, times(0)).query(any());
  }

  @Test
  void oneFailingPayment_doesNotStopTheOthers() {
    final Payment first = pending(10);
    final Payment second = pending(11);
    when(this.queryService.query(any())).thenThrow(new IllegalStateException("boom"))
        .thenReturn(result(Outcome.PAID, "25000000"));

    this.reconciler.reconcileOnce();

    final long confirmed = java.util.stream.Stream.of(first, second)
        .filter(p -> reload(p).getStatus() == PaymentStatus.SUCCESS).count();
    assertThat(confirmed).isEqualTo(1);
  }

  @Test
  void nothingIsAskedWhenVnpayIsNotConfigured() {
    pending(10);
    when(this.queryService.isConfigured()).thenReturn(false);

    assertThat(this.reconciler.reconcileOnce()).isZero();
  }
}
