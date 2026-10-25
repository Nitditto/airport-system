package com.nitditto.payment_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nitditto.enums.PaymentGateway;
import com.nitditto.enums.PaymentStatus;
import com.nitditto.payload.response.PaymentGatewayResponse;
import com.nitditto.event.PaymentSucceededEvent;
import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ForbiddenException;
import com.nitditto.model.Payment;
import com.nitditto.payload.dto.PaymentDTO;
import com.nitditto.payload.request.PaymentVerifyRequest;
import com.nitditto.repository.PaymentRepository;
import com.nitditto.service.gateway.RazorpayService;
import com.nitditto.service.gateway.VNPayService;
import com.nitditto.service.impl.PaymentServiceImpl;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

  private static final String SECRET = "test-hash-secret";

  @Mock
  private PaymentRepository paymentRepository;
  @Mock
  private RazorpayService razorpayService;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  private PaymentServiceImpl service;

  private final UUID bookingId = UUID.randomUUID();
  private Payment payment;

  @BeforeEach
  void setUp() {
    final VNPayService vnPayService = new VNPayService();
    ReflectionTestUtils.setField(vnPayService, "vnpTmnCode", "TESTTMN");
    ReflectionTestUtils.setField(vnPayService, "vnpHashSecret", SECRET);
    this.service = new PaymentServiceImpl(this.paymentRepository, this.razorpayService, vnPayService,
        this.eventPublisher);

    this.payment = Payment.builder().userId(UUID.randomUUID()).bookingId(this.bookingId).amount(250.0)
        .status(PaymentStatus.PENDING).build();
    this.payment.setId(UUID.randomUUID());
  }

  @Test
  void getAvailableGateways_reflectsWhichGatewaysAreConfigured() {
    when(this.razorpayService.isConfigured()).thenReturn(false);

    assertThat(this.service.getAvailableGateways())
        .extracting(PaymentGatewayResponse::getCode, PaymentGatewayResponse::isEnabled)
        .containsExactly(tuple(PaymentGateway.RAZORPAY, false), tuple(PaymentGateway.VNPAY, true));
  }

  @Test
  void checkPaymentVNPay_rejectsSuccessResultWithoutSignature_soNobodyCanFakeAPayment() {
    final Map<String, String> params = vnpResult("00", 25000);
    params.remove("vnp_SecureHash");

    assertThatThrownBy(() -> this.service.checkPaymentVNPay(params))
        .isInstanceOf(BusinessException.class).hasMessageContaining("VNPay result parameters are required");

    assertThat(this.payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    verify(this.eventPublisher, never()).publishEvent(any(Object.class));
  }

  @Test
  void checkPaymentVNPay_rejectsAmountTamperedAfterSigning() {
    final Map<String, String> params = vnpResult("00", 25000);
    params.put("vnp_Amount", "100"); // đổi số tiền sau khi ký

    assertThatThrownBy(() -> this.service.checkPaymentVNPay(params))
        .isInstanceOf(BusinessException.class).hasMessageContaining("Invalid VNPay signature");

    assertThat(this.payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
  }

  @Test
  void checkPaymentVNPay_rejectsAValidlySignedResultWhoseAmountDiffersFromThePayment() {
    givenPaymentFound();

    assertThatThrownBy(() -> this.service.checkPaymentVNPay(vnpResult("00", 100)))
        .isInstanceOf(BusinessException.class).hasMessageContaining("amount does not match");

    assertThat(this.payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    verify(this.eventPublisher, never()).publishEvent(any(Object.class));
  }

  @Test
  void checkPaymentVNPay_marksSuccess_andNotifiesBookingOnce_whenSignedResultIsSuccessful() {
    givenPaymentFound();

    final PaymentDTO result = this.service.checkPaymentVNPay(vnpResult("00", 25000));

    assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
    final ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(this.eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue()).isEqualTo(new PaymentSucceededEvent(this.payment.getId(), this.bookingId));
  }

  @Test
  void checkPaymentVNPay_calledAgainAfterSuccess_doesNotNotifyAgain() {
    givenPaymentFound();

    this.service.checkPaymentVNPay(vnpResult("00", 25000));
    this.service.checkPaymentVNPay(vnpResult("00", 25000));

    verify(this.eventPublisher, times(1)).publishEvent(any(Object.class));
  }

  @Test
  void checkPaymentVNPay_neverDowngradesASuccessfulPayment_onALaterFailedCallback() {
    givenPaymentFound();
    this.payment.setStatus(PaymentStatus.SUCCESS);

    final PaymentDTO result = this.service.checkPaymentVNPay(vnpResult("24", 25000));

    assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
  }

  @Test
  void checkPaymentVNPay_marksFailed_withoutNotifyingBooking_whenSignedResultIsACancel() {
    givenPaymentFound();

    final PaymentDTO result = this.service.checkPaymentVNPay(vnpResult("24", 25000));

    assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
    verify(this.eventPublisher, never()).publishEvent(any(Object.class));
  }

  @Test
  void checkPaymentVNPay_statusLookupWithoutGatewayResult_isRejected_andNeverTouchesTheDatabase() {
    assertThatThrownBy(() -> this.service.checkPaymentVNPay(Map.of("paymentId", this.payment.getId().toString())))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("VNPay result parameters are required");
    assertThatThrownBy(() -> this.service.checkPaymentVNPay(Map.of("bookingId", this.bookingId.toString())))
        .isInstanceOf(BusinessException.class);

    verify(this.paymentRepository, never()).findById(any());
    verify(this.paymentRepository, never()).findByBookingIdIn(any());
  }

  @Test
  void checkPaymentVNPay_withInvalidSignature_isRejectedBeforeAnyLookup_soPaymentsCannotBeProbed() {
    final Map<String, String> params = new HashMap<>(vnpResult("00", 25000));
    params.put("vnp_SecureHash", "deadbeef");

    assertThatThrownBy(() -> this.service.checkPaymentVNPay(params))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Invalid VNPay signature");

    verify(this.paymentRepository, never()).findById(any());
  }

  @Test
  void verifyPayment_vnpayWithoutParameters_isRejected() {
    givenPaymentFound();
    final PaymentVerifyRequest request = PaymentVerifyRequest.builder()
        .vnpTxnRef(this.payment.getId().toString()).vnpResponseCode("00").build();

    assertThatThrownBy(() -> this.service.verifyPayment(request, this.payment.getUserId()))
        .isInstanceOf(BusinessException.class);

    assertThat(this.payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
  }

  @Test
  void verifyPayment_vnpayByAnotherUser_isForbidden_andLeavesThePaymentUntouched() {
    givenPaymentFound();
    final PaymentVerifyRequest request = PaymentVerifyRequest.builder()
        .vnpTxnRef(this.payment.getId().toString()).vnpParams(vnpResult("00", 25000)).build();

    assertThatThrownBy(() -> this.service.verifyPayment(request, UUID.randomUUID()))
        .isInstanceOf(ForbiddenException.class);

    assertThat(this.payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    verify(this.eventPublisher, never()).publishEvent(any(Object.class));
  }

  @Test
  void verifyPayment_razorpayByAnotherUser_isForbidden_evenWithAValidRazorpayPaymentId() throws Exception {
    givenPaymentFound();
    givenRazorpayCaptured("pay_stolen");
    final PaymentVerifyRequest request = PaymentVerifyRequest.builder().razorpayPaymentId("pay_stolen").build();

    assertThatThrownBy(() -> this.service.verifyPayment(request, UUID.randomUUID()))
        .isInstanceOf(ForbiddenException.class);

    assertThat(this.payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    verify(this.eventPublisher, never()).publishEvent(any(Object.class));
  }

  @Test
  void verifyPayment_razorpayByTheOwner_marksTheCapturedPaymentSuccessful() throws Exception {
    givenPaymentFound();
    givenRazorpayCaptured("pay_mine");
    final PaymentVerifyRequest request = PaymentVerifyRequest.builder().razorpayPaymentId("pay_mine").build();

    final PaymentDTO result = this.service.verifyPayment(request, this.payment.getUserId());

    assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
  }

  @Test
  void getPaymentsByBookingIds_prefersTheSuccessfulPayment_overALaterRetry() {
    final Payment paid = Payment.builder().bookingId(this.bookingId).status(PaymentStatus.SUCCESS).amount(1.0).build();
    paid.setId(UUID.randomUUID());
    final Payment retry = Payment.builder().bookingId(this.bookingId).status(PaymentStatus.PENDING).amount(1.0).build();
    retry.setId(UUID.randomUUID());
    when(this.paymentRepository.findByBookingIdIn(List.of(this.bookingId))).thenReturn(List.of(paid, retry));

    final Map<UUID, PaymentDTO> result = this.service.getPaymentsByBookingIds(List.of(this.bookingId));

    assertThat(result.get(this.bookingId).getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
  }

  private void givenRazorpayCaptured(final String razorpayPaymentId) throws Exception {
    final JSONObject details = new JSONObject()
        .put("status", "captured")
        .put("notes", new JSONObject().put("payment_id", this.payment.getId().toString()));
    when(this.razorpayService.fetchPaymentDetails(razorpayPaymentId)).thenReturn(details);
  }

  private void givenPaymentFound() {
    when(this.paymentRepository.findById(this.payment.getId())).thenReturn(Optional.of(this.payment));
  }

  // Dựng tham số trả về của VNPay đã ký đúng như cổng thật (HMAC-SHA512 trên các field vnp_* sắp xếp theo tên).
  private Map<String, String> vnpResult(final String responseCode, final long vnpAmount) {
    final Map<String, String> fields = new HashMap<>();
    fields.put("vnp_TxnRef", this.payment.getId().toString());
    fields.put("vnp_Amount", String.valueOf(vnpAmount));
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
