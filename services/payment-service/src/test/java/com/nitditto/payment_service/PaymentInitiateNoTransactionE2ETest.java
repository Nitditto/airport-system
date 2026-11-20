package com.nitditto.payment_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nitditto.client.UserClient;
import com.nitditto.enums.PaymentGateway;
import com.nitditto.enums.PaymentStatus;
import com.nitditto.exception.BusinessException;
import com.nitditto.model.Payment;
import com.nitditto.payload.dto.UserDTO;
import com.nitditto.payload.request.PaymentInitiateRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.PaymentInitiateResponse;
import com.nitditto.payload.response.PaymentLinkResponse;
import com.nitditto.repository.PaymentRepository;
import com.nitditto.service.PaymentService;
import com.nitditto.service.gateway.RazorpayService;
import com.razorpay.RazorpayException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * CLAUDE.md §8.5: không gọi mạng (user-service, cổng thanh toán) trong transaction DB đang mở. Chạy trên MySQL thật
 * ({@code airline_payment_test}); chỉ user-service và Razorpay được mock, và khi chúng được gọi ta ghi lại có transaction
 * nào đang mở trên luồng hay không, cũng như payment đã được commit chưa.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3306/airline_payment_test?createDatabaseIfNotExist=true",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "eureka.client.enabled=false",
    "spring.kafka.listener.auto-startup=false",
    "razorpay.api.key=rzp_test_dummy",
    "razorpay.api.secret=dummy",
    "vnpay.tmn-code=TESTTMN",
    "vnpay.hash-secret=initiate-secret"
})
class PaymentInitiateNoTransactionE2ETest {

  @Autowired
  private PaymentService paymentService;
  @Autowired
  private PaymentRepository paymentRepository;
  @MockitoBean
  private RazorpayService razorpayService;
  @MockitoBean
  private UserClient userClient;
  @MockitoBean
  private com.nitditto.client.BookingClient bookingClient;
  @MockitoBean
  private org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

  private final List<Boolean> transactionOpenDuringCalls = new ArrayList<>();
  private final List<Boolean> paymentCommittedDuringGatewayCall = new ArrayList<>();
  private final UUID userId = UUID.randomUUID();
  private final UUID bookingId = UUID.randomUUID();

  @BeforeEach
  void setUp() throws Exception {
    Mockito.reset(this.razorpayService, this.userClient);
    this.transactionOpenDuringCalls.clear();
    this.paymentCommittedDuringGatewayCall.clear();
    final UserDTO user = new UserDTO();
    user.setId(this.userId);
    user.setEmail("a@b.c");
    user.setFullName("A B");
    when(this.userClient.getUserById(any())).thenAnswer(inv -> {
      this.transactionOpenDuringCalls.add(TransactionSynchronizationManager.isActualTransactionActive());
      final ApiResponse<UserDTO> body = ApiResponse.success(user);
      return ResponseEntity.ok(body);
    });
  }

  @AfterEach
  void cleanUp() {
    this.paymentRepository.deleteAll();
  }

  private PaymentInitiateRequest request(final PaymentGateway gateway) {
    return PaymentInitiateRequest.builder().userId(this.userId).bookingId(this.bookingId).paymentGateway(gateway)
        .amount(250000.0).description("d").build();
  }

  @Test
  void razorpayInitiate_callsTheNetworkOutsideAnyTransaction_afterThePaymentIsAlreadyCommitted() throws Exception {
    when(this.razorpayService.createPaymentLink(any(), any())).thenAnswer(inv -> {
      this.transactionOpenDuringCalls.add(TransactionSynchronizationManager.isActualTransactionActive());
      final Payment arg = inv.getArgument(1);
      // đọc từ một luồng khác nghĩa là chỉ thấy dữ liệu ĐÃ COMMIT
      final boolean committed = java.util.concurrent.CompletableFuture
          .supplyAsync(() -> this.paymentRepository.findById(arg.getId()).isPresent()).get();
      this.paymentCommittedDuringGatewayCall.add(committed);
      return PaymentLinkResponse.builder().paymentLinkId("plink_1").paymentLinkUrl("https://rzp.test/pay").build();
    });

    final PaymentInitiateResponse response = this.paymentService.initiatePayment(request(PaymentGateway.RAZORPAY));

    assertThat(this.transactionOpenDuringCalls).containsExactly(false, false); // user-service rồi Razorpay
    assertThat(this.paymentCommittedDuringGatewayCall).containsExactly(true);
    final Payment saved = this.paymentRepository.findById(response.getPaymentId()).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
    assertThat(saved.getCheckoutUrl()).isEqualTo("https://rzp.test/pay");
    assertThat(saved.getProviderPaymentId()).isEqualTo("plink_1");
  }

  @Test
  void whenRazorpayFails_thePaymentIsMarkedFailed_notLeftPending_andTheErrorIsReported() throws Exception {
    when(this.razorpayService.createPaymentLink(any(), any()))
        .thenThrow(new RazorpayException("amount exceeds maximum amount allowed"));

    assertThatThrownBy(() -> this.paymentService.initiatePayment(request(PaymentGateway.RAZORPAY)))
        .isInstanceOf(BusinessException.class).hasMessageContaining("Failed to create payment link");

    final List<Payment> payments = this.paymentRepository.findAll();
    assertThat(payments).hasSize(1);
    assertThat(payments.get(0).getStatus()).isEqualTo(PaymentStatus.FAILED);
    assertThat(payments.get(0).getFailureReason()).contains("Could not create payment link");
    // không có event thất bại: booking-service tự bù trừ khi initiate ném lỗi
    verify(this.kafkaTemplate, never()).send(any(String.class), any(String.class), any());
  }

  @Test
  void whenTheUserCannotBeLoaded_noPaymentIsWritten() {
    when(this.userClient.getUserById(any())).thenReturn(ResponseEntity.ok(ApiResponse.success(null)));

    assertThatThrownBy(() -> this.paymentService.initiatePayment(request(PaymentGateway.RAZORPAY)))
        .isInstanceOf(BusinessException.class).hasMessageContaining("Could not load the paying user");

    assertThat(this.paymentRepository.findAll()).isEmpty();
  }

  @Test
  void vnpayInitiate_persistsTheLinkAndTheProviderCreateDate() {
    final PaymentInitiateResponse response = this.paymentService.initiatePayment(request(PaymentGateway.VNPAY));

    final Payment saved = this.paymentRepository.findById(response.getPaymentId()).orElseThrow();
    assertThat(saved.getCheckoutUrl()).contains("vnp_SecureHash=");
    assertThat(saved.getProviderCreateDate()).hasSize(14);
    assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
  }
}
