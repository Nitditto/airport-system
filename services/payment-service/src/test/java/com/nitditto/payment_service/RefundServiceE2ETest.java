package com.nitditto.payment_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nitditto.enums.PaymentGateway;
import com.nitditto.enums.PaymentStatus;
import com.nitditto.enums.RefundStatus;
import com.nitditto.exception.BusinessException;
import com.nitditto.model.Payment;
import com.nitditto.model.Refund;
import com.nitditto.payload.request.RefundRequest;
import com.nitditto.payload.response.RefundResponse;
import com.nitditto.repository.PaymentRepository;
import com.nitditto.repository.RefundRepository;
import com.nitditto.service.RefundService;
import com.nitditto.service.gateway.VNPayRefundService;
import com.nitditto.service.gateway.VNPayRefundService.Outcome;
import com.nitditto.service.gateway.VNPayRefundService.Result;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Hoàn tiền trên MySQL thật ({@code airline_payment_test}, transaction + unique constraint thật). Chỉ phần HTTP tới VNPay
 * được mock, để kiểm tra: ghi nhận trước khi gọi cổng, idempotent, không hoàn hai lần dù hai request song song, và
 * quy tắc retry khi kết quả không rõ.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3306/airline_payment_test?createDatabaseIfNotExist=true",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "eureka.client.enabled=false",
    "spring.kafka.listener.auto-startup=false",
    "razorpay.api.key=rzp_test_dummy",
    "razorpay.api.secret=dummy",
    "vnpay.tmn-code=TESTTMN",
    "vnpay.hash-secret=refund-e2e-secret"
})
@AutoConfigureMockMvc
class RefundServiceE2ETest {

  private static final double PAID = 1_150_000.0;

  @Autowired
  private RefundService refundService;
  @Autowired
  private RefundRepository refundRepository;
  @Autowired
  private PaymentRepository paymentRepository;
  @Autowired
  private MockMvc mockMvc;
  @MockitoBean
  private VNPayRefundService vnPayRefundService;

  @BeforeEach
  void clean() {
    this.refundRepository.deleteAll();
    this.paymentRepository.deleteAll();
    reset(this.vnPayRefundService);
  }

  @AfterEach
  void cleanAfter() {
    this.refundRepository.deleteAll();
    this.paymentRepository.deleteAll();
  }

  @Test
  void success_recordsTheRefund_marksThePaymentRefunded_andSendsTheOriginalTransactionData() {
    final Payment payment = this.paidPayment(PaymentGateway.VNPAY);
    when(this.vnPayRefundService.refund(any())).thenReturn(ok("99887766"));

    final RefundResponse response = this.refundService.requestRefund(request(payment, 1_050_000.0));

    assertThat(response.getStatus()).isEqualTo(RefundStatus.SUCCESS);
    assertThat(response.getProviderTransactionNo()).isEqualTo("99887766");
    final Refund stored = this.refundRepository.findByPaymentId(payment.getId()).orElseThrow();
    assertThat(stored.getStatus()).isEqualTo(RefundStatus.SUCCESS);
    assertThat(stored.getAmount()).isEqualTo(1_050_000.0);
    assertThat(stored.getAttempts()).isEqualTo(1);
    final Payment after = this.paymentRepository.findById(payment.getId()).orElseThrow();
    assertThat(after.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    assertThat(after.getRefundId()).isEqualTo("99887766");

    final ArgumentCaptor<VNPayRefundService.Command> sent = ArgumentCaptor.forClass(VNPayRefundService.Command.class);
    verify(this.vnPayRefundService).refund(sent.capture());
    assertThat(sent.getValue().txnRef()).isEqualTo(payment.getId().toString());
    assertThat(sent.getValue().amount()).isEqualTo(1_050_000.0);
    assertThat(sent.getValue().fullRefund()).isFalse();
    assertThat(sent.getValue().transactionNo()).isEqualTo("14123456");
    assertThat(sent.getValue().transactionDate()).isEqualTo("20261120100000");
    assertThat(sent.getValue().requestId()).hasSize(32).isEqualTo(stored.getRequestId());
  }

  @Test
  void refundingTheWholeAmount_isSentAsAFullRefund() {
    final Payment payment = this.paidPayment(PaymentGateway.VNPAY);
    when(this.vnPayRefundService.refund(any())).thenReturn(ok("1"));

    this.refundService.requestRefund(request(payment, PAID));

    final ArgumentCaptor<VNPayRefundService.Command> sent = ArgumentCaptor.forClass(VNPayRefundService.Command.class);
    verify(this.vnPayRefundService).refund(sent.capture());
    assertThat(sent.getValue().fullRefund()).isTrue();
  }

  @Test
  void repeatingTheSameRequest_returnsTheExistingRefund_andNeverCallsTheGatewayAgain() {
    final Payment payment = this.paidPayment(PaymentGateway.VNPAY);
    when(this.vnPayRefundService.refund(any())).thenReturn(ok("99887766"));
    final RefundResponse first = this.refundService.requestRefund(request(payment, 1_050_000.0));

    final RefundResponse second = this.refundService.requestRefund(request(payment, 1_050_000.0));

    assertThat(second.getRefundId()).isEqualTo(first.getRefundId());
    assertThat(second.getStatus()).isEqualTo(RefundStatus.SUCCESS);
    verify(this.vnPayRefundService, times(1)).refund(any());
    assertThat(this.refundRepository.count()).isEqualTo(1);
  }

  @Test
  void aDifferentAmountForAnExistingRefund_isRejected() {
    final Payment payment = this.paidPayment(PaymentGateway.VNPAY);
    when(this.vnPayRefundService.refund(any())).thenReturn(ok("1"));
    this.refundService.requestRefund(request(payment, 1_050_000.0));

    assertThatThrownBy(() -> this.refundService.requestRefund(request(payment, 500_000.0)))
        .isInstanceOf(BusinessException.class).hasMessageContaining("different amount");
    verify(this.vnPayRefundService, times(1)).refund(any());
  }

  @Test
  void anAmountAbovePaid_isRejected_andNothingIsRecorded() {
    final Payment payment = this.paidPayment(PaymentGateway.VNPAY);

    assertThatThrownBy(() -> this.refundService.requestRefund(request(payment, PAID + 1)))
        .isInstanceOf(BusinessException.class).hasMessageContaining("exceeds");

    assertThat(this.refundRepository.count()).isZero();
    verify(this.vnPayRefundService, never()).refund(any());
  }

  @Test
  void aBookingWithoutASuccessfulPayment_cannotBeRefunded() {
    final Payment pending = this.paymentRepository.save(Payment.builder().userId(UUID.randomUUID())
        .bookingId(UUID.randomUUID()).amount(PAID).provider(PaymentGateway.VNPAY).status(PaymentStatus.PENDING)
        .build());

    assertThatThrownBy(() -> this.refundService.requestRefund(request(pending, 1000.0)))
        .isInstanceOf(BusinessException.class).hasMessageContaining("no successful payment");
  }

  @Test
  void aRejectedRefund_staysFailed_theCustomerIsNotMarkedRefunded_andCanBeRetriedFreely() {
    final Payment payment = this.paidPayment(PaymentGateway.VNPAY);
    when(this.vnPayRefundService.refund(any()))
        .thenReturn(new Result(Outcome.REJECTED, "91", "Transaction not found", null)).thenReturn(ok("777"));

    final RefundResponse failed = this.refundService.requestRefund(request(payment, 1_050_000.0));

    assertThat(failed.getStatus()).isEqualTo(RefundStatus.FAILED);
    assertThat(failed.getFailureReason()).contains("91").contains("Transaction not found");
    assertThat(this.paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.SUCCESS);
    final String firstRequestId = this.refundRepository.findByPaymentId(payment.getId()).orElseThrow().getRequestId();

    // REJECTED = chắc chắn chưa hoàn: retry không cần xác nhận
    final RefundResponse retried = this.refundService.retry(failed.getRefundId(), false);

    assertThat(retried.getStatus()).isEqualTo(RefundStatus.SUCCESS);
    final Refund stored = this.refundRepository.findById(failed.getRefundId()).orElseThrow();
    assertThat(stored.getAttempts()).isEqualTo(2);
    assertThat(stored.getRequestId()).isNotEqualTo(firstRequestId); // mỗi lần gọi một vnp_RequestId mới
    assertThat(this.paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.REFUNDED);
  }

  @Test
  void anUnknownOutcome_cannotBeRetriedWithoutAnExplicitConfirmation() {
    final Payment payment = this.paidPayment(PaymentGateway.VNPAY);
    when(this.vnPayRefundService.refund(any()))
        .thenReturn(new Result(Outcome.UNKNOWN, null, "Could not complete the VNPay call: ResourceAccessException", null))
        .thenReturn(ok("888"));
    final RefundResponse unknown = this.refundService.requestRefund(request(payment, 1_050_000.0));
    assertThat(unknown.getStatus()).isEqualTo(RefundStatus.FAILED);
    assertThat(unknown.getFailureReason()).startsWith("UNKNOWN OUTCOME");
    assertThat(this.refundRepository.findById(unknown.getRefundId()).orElseThrow().isOutcomeUnknown()).isTrue();

    assertThatThrownBy(() -> this.refundService.retry(unknown.getRefundId(), false))
        .isInstanceOf(BusinessException.class).hasMessageContaining("confirmNotRefunded=true");
    verify(this.vnPayRefundService, times(1)).refund(any()); // chưa gọi cổng lần hai

    final RefundResponse confirmed = this.refundService.retry(unknown.getRefundId(), true);

    assertThat(confirmed.getStatus()).isEqualTo(RefundStatus.SUCCESS);
    verify(this.vnPayRefundService, times(2)).refund(any());
  }

  @Test
  void aSuccessfulRefundCannotBeRetried_itJustReturnsTheResult() {
    final Payment payment = this.paidPayment(PaymentGateway.VNPAY);
    when(this.vnPayRefundService.refund(any())).thenReturn(ok("1"));
    final RefundResponse done = this.refundService.requestRefund(request(payment, 1000.0));

    final RefundResponse again = this.refundService.retry(done.getRefundId(), true);

    assertThat(again.getStatus()).isEqualTo(RefundStatus.SUCCESS);
    verify(this.vnPayRefundService, times(1)).refund(any());
  }

  @Test
  void aGatewayThatCannotRefund_isRecordedAsFailed_withoutCallingVnpay() {
    final Payment payment = this.paidPayment(PaymentGateway.RAZORPAY);

    final RefundResponse response = this.refundService.requestRefund(request(payment, 1000.0));

    assertThat(response.getStatus()).isEqualTo(RefundStatus.FAILED);
    assertThat(response.getFailureReason()).contains("not supported").contains("refund manually");
    verify(this.vnPayRefundService, never()).refund(any());
  }

  @Test
  void twoRequestsAtTheSameTime_callTheGatewayExactlyOnce() throws Exception {
    final Payment payment = this.paidPayment(PaymentGateway.VNPAY);
    when(this.vnPayRefundService.refund(any())).thenAnswer(invocation -> {
      Thread.sleep(400); // giữ cuộc gọi cổng đủ lâu để request thứ hai chen vào
      return ok("555");
    });
    final ExecutorService pool = Executors.newFixedThreadPool(2);
    final CountDownLatch start = new CountDownLatch(1);
    try {
      final Future<RefundResponse> a = pool.submit(() -> {
        start.await();
        return this.refundService.requestRefund(request(payment, 1_050_000.0));
      });
      final Future<RefundResponse> b = pool.submit(() -> {
        start.await();
        return this.refundService.requestRefund(request(payment, 1_050_000.0));
      });
      start.countDown();

      final RefundResponse ra = a.get(20, TimeUnit.SECONDS);
      final RefundResponse rb = b.get(20, TimeUnit.SECONDS);

      assertThat(ra.getRefundId()).isEqualTo(rb.getRefundId());
    } finally {
      pool.shutdownNow();
    }
    verify(this.vnPayRefundService, times(1)).refund(any());
    assertThat(this.refundRepository.count()).isEqualTo(1);
  }

  @Test
  void list_filtersByStatus() {
    final Payment ok = this.paidPayment(PaymentGateway.VNPAY);
    final Payment bad = this.paidPayment(PaymentGateway.VNPAY);
    when(this.vnPayRefundService.refund(any())).thenReturn(ok("1"))
        .thenReturn(new Result(Outcome.REJECTED, "99", "boom", null));
    this.refundService.requestRefund(request(ok, 1000.0));
    this.refundService.requestRefund(request(bad, 1000.0));

    assertThat(this.refundService.list(RefundStatus.FAILED, PageRequest.of(0, 10)).getContent())
        .extracting(RefundResponse::getBookingId).containsExactly(bad.getBookingId());
    assertThat(this.refundService.list(null, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);
  }

  // ---- HTTP: validation và đường dẫn ----

  @Test
  void endpoint_refundsAndReturnsTheResult() throws Exception {
    final Payment payment = this.paidPayment(PaymentGateway.VNPAY);
    when(this.vnPayRefundService.refund(any())).thenReturn(ok("42"));

    this.mockMvc.perform(post("/api/payments/refund").contentType(MediaType.APPLICATION_JSON)
            .content("{\"bookingId\":\"%s\",\"amount\":1050000,\"reason\":\"cancelled\"}".formatted(payment.getBookingId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.amount").value(1050000.0))
        .andExpect(jsonPath("$.data.providerTransactionNo").value("42"));
  }

  @Test
  void endpoint_rejectsAnInvalidAmount_with400() throws Exception {
    this.mockMvc.perform(post("/api/payments/refund").contentType(MediaType.APPLICATION_JSON)
            .content("{\"bookingId\":\"%s\",\"amount\":-5}".formatted(UUID.randomUUID())))
        .andExpect(status().isBadRequest());
    this.mockMvc.perform(post("/api/payments/refund").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void endpoint_listsFailedRefunds_andRetriesOne() throws Exception {
    final Payment payment = this.paidPayment(PaymentGateway.VNPAY);
    when(this.vnPayRefundService.refund(any()))
        .thenReturn(new Result(Outcome.REJECTED, "91", "nope", null)).thenReturn(ok("9"));
    final RefundResponse failed = this.refundService.requestRefund(request(payment, 1000.0));

    this.mockMvc.perform(get("/api/payments/refunds").param("status", "FAILED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].refundId").value(failed.getRefundId().toString()));
    this.mockMvc.perform(post("/api/payments/refunds/{id}/retry", failed.getRefundId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("SUCCESS"));
  }

  // ---- helpers ----

  private Payment paidPayment(final PaymentGateway provider) {
    return this.paymentRepository.save(Payment.builder().userId(UUID.randomUUID()).bookingId(UUID.randomUUID())
        .amount(PAID).provider(provider).status(PaymentStatus.SUCCESS).transactionId("14123456")
        .providerCreateDate("20261120100000").build());
  }

  private static RefundRequest request(final Payment payment, final double amount) {
    return RefundRequest.builder().bookingId(payment.getBookingId()).amount(amount).reason("Booking cancelled")
        .requestedBy("user-1").build();
  }

  private static Result ok(final String transactionNo) {
    return new Result(Outcome.SUCCESS, "00", "Confirm Success", transactionNo);
  }
}
