package com.nitditto.payment_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nitditto.client.UserClient;
import com.nitditto.enums.PaymentStatus;
import com.nitditto.event.PaymentFailedEvent;
import com.nitditto.event.PaymentSucceededEvent;
import com.nitditto.exception.BusinessException;
import com.nitditto.enums.PaymentGateway;
import com.nitditto.payload.response.PaymentGatewayResponse;
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
  private static final java.time.Clock CLOCK = java.time.Clock.fixed(java.time.Instant.parse("2026-11-20T03:00:00Z"),
      java.time.ZoneOffset.UTC);

  @Mock
  private PaymentRepository paymentRepository;
  @Mock
  private RazorpayService razorpayService;
  @Mock
  private ApplicationEventPublisher eventPublisher;
  @Mock
  private UserClient userClient;
  @Mock
  private com.nitditto.service.gateway.ExchangeRateService exchangeRateService;

  private PaymentServiceImpl service;

  private final UUID bookingId = UUID.randomUUID();
  private Payment payment;

  @BeforeEach
  void setUp() {
    final VNPayService vnPayService = new VNPayService();
    ReflectionTestUtils.setField(vnPayService, "vnpTmnCode", "TESTTMN");
    ReflectionTestUtils.setField(vnPayService, "vnpHashSecret", SECRET);
    this.service = new PaymentServiceImpl(this.paymentRepository, this.razorpayService, vnPayService,
        this.eventPublisher, this.userClient, CLOCK,
        new com.nitditto.service.impl.PaymentRecordServiceImpl(this.paymentRepository),
        new com.nitditto.service.gateway.ClientIpPolicy(false), this.exchangeRateService,
        org.springframework.transaction.support.TransactionOperations.withoutTransaction());

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
    assertThat(captor.getValue()).isInstanceOfSatisfying(PaymentSucceededEvent.class, event -> {
      assertThat(event.paymentId()).isEqualTo(this.payment.getId());
      assertThat(event.bookingId()).isEqualTo(this.bookingId);
      assertThat(event.userId()).isEqualTo(this.payment.getUserId());
      assertThat(event.amount()).isEqualTo(250.0);
      assertThat(event.paidAt()).isNotNull();
    });
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
  void checkPaymentVNPay_marksFailed_andPublishesOnlyFailedEvent_whenSignedResultIsACancel() {
    givenPaymentFound();

    final PaymentDTO result = this.service.checkPaymentVNPay(vnpResult("24", 25000));

    assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
    verify(this.eventPublisher, never()).publishEvent(any(PaymentSucceededEvent.class));
    final ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(this.eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue()).isInstanceOfSatisfying(PaymentFailedEvent.class, event -> {
      assertThat(event.bookingId()).isEqualTo(this.bookingId);
      assertThat(event.paymentId()).isEqualTo(this.payment.getId());
    });
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
  void verifyPayment_razorpay_callsRazorpayOutsideTheTransaction_andWritesInsideIt() throws Exception {
    final java.util.concurrent.atomic.AtomicBoolean inTransaction = new java.util.concurrent.atomic.AtomicBoolean();
    final org.springframework.transaction.support.TransactionOperations tracking =
        new org.springframework.transaction.support.TransactionOperations() {
          @Override
          public <T> T execute(final org.springframework.transaction.support.TransactionCallback<T> action) {
            inTransaction.set(true);
            try {
              return action.doInTransaction(null);
            } finally {
              inTransaction.set(false);
            }
          }
        };
    ReflectionTestUtils.setField(this.service, "transactionOperations", tracking);
    final JSONObject details = new JSONObject().put("status", "captured")
        .put("notes", new JSONObject().put("payment_id", this.payment.getId().toString()));
    when(this.razorpayService.fetchPaymentDetails("pay_tx")).thenAnswer(invocation -> {
      assertThat(inTransaction).as("Razorpay must not be called while holding a DB transaction").isFalse();
      return details;
    });
    when(this.paymentRepository.findById(this.payment.getId())).thenAnswer(invocation -> {
      assertThat(inTransaction).as("payment must be loaded and written inside the transaction").isTrue();
      return java.util.Optional.of(this.payment);
    });

    this.service.verifyPayment(PaymentVerifyRequest.builder().razorpayPaymentId("pay_tx").build(),
        this.payment.getUserId());

    assertThat(this.payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
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

  // ---- Razorpay: quy đổi VND -> INR theo tỷ giá thị trường ----

  private static final com.nitditto.service.gateway.ExchangeRateService.Quote QUOTE =
      new com.nitditto.service.gateway.ExchangeRateService.Quote("INR", 41_926L, 274.43, 1.0,
          "Vietcombank transfer rate (feed 11/20/2026)");

  private com.nitditto.payload.request.PaymentInitiateRequest razorpayRequest() {
    return com.nitditto.payload.request.PaymentInitiateRequest.builder().userId(this.payment.getUserId())
        .bookingId(this.bookingId).paymentGateway(PaymentGateway.RAZORPAY).amount(1_150_500.0).description("d").build();
  }

  private void givenUserAndRazorpayLink() throws Exception {
    final com.nitditto.payload.dto.UserDTO user = new com.nitditto.payload.dto.UserDTO();
    user.setId(this.payment.getUserId());
    when(this.userClient.getUserById(any())).thenReturn(org.springframework.http.ResponseEntity
        .ok(com.nitditto.payload.response.ApiResponse.success(user)));
    when(this.razorpayService.createPaymentLink(any(), any(), any())).thenReturn(
        com.nitditto.payload.response.PaymentLinkResponse.builder().paymentLinkId("plink_1")
            .paymentLinkUrl("https://rzp.test/pay").build());
    givenSaveAssignsIds();
  }

  @Test
  void razorpayInitiate_chargesTheConvertedInrAmount_keepsVndAsTheBookingAmount_andTellsTheCaller() throws Exception {
    givenUserAndRazorpayLink();
    when(this.exchangeRateService.quoteFromVnd("INR", 1_150_500.0)).thenReturn(QUOTE);

    final var response = this.service.initiatePayment(razorpayRequest());

    final ArgumentCaptor<Payment> sent = ArgumentCaptor.forClass(Payment.class);
    verify(this.razorpayService).createPaymentLink(any(), sent.capture(), any());
    assertThat(sent.getValue().getAmount()).isEqualTo(1_150_500.0); // VND của booking không đổi
    assertThat(sent.getValue().getChargeCurrency()).isEqualTo("INR");
    assertThat(sent.getValue().getChargeAmountMinor()).isEqualTo(41_926L);
    assertThat(sent.getValue().getFxRate()).isEqualTo(274.43);
    assertThat(sent.getValue().getFxSource()).contains("Vietcombank");
    assertThat(response.getAmount()).isEqualTo(1_150_500.0);
    assertThat(response.getChargeCurrency()).isEqualTo("INR");
    assertThat(response.getChargeAmount()).isEqualTo(419.26);
    assertThat(response.getExchangeRate()).isEqualTo(274.43);
    assertThat(response.getExchangeRateMarginPercent()).isEqualTo(1.0);
    assertThat(sent.getValue().getFxMarginPercent()).isEqualTo(1.0);
    assertThat(response.getExchangeRateSource()).contains("Vietcombank");
  }

  @Test
  void razorpayInitiate_withoutAReliableRate_isRefusedBeforeAnythingIsWritten() throws Exception {
    final com.nitditto.payload.dto.UserDTO user = new com.nitditto.payload.dto.UserDTO();
    when(this.userClient.getUserById(any())).thenReturn(org.springframework.http.ResponseEntity
        .ok(com.nitditto.payload.response.ApiResponse.success(user)));
    when(this.exchangeRateService.quoteFromVnd(any(), org.mockito.ArgumentMatchers.anyDouble()))
        .thenThrow(new BusinessException("Razorpay payment is temporarily unavailable: no reliable VND/INR exchange rate"));

    assertThatThrownBy(() -> this.service.initiatePayment(razorpayRequest()))
        .isInstanceOf(BusinessException.class).hasMessageContaining("exchange rate");

    verify(this.paymentRepository, never()).save(any(Payment.class));
    verify(this.razorpayService, never()).createPaymentLink(any(), any(), any());
  }

  @Test
  void vnpayInitiate_isNotConverted_andCarriesNoChargeInformation() {
    givenSaveAssignsIds();

    final var response = this.service.initiatePayment(initiateRequest());

    verify(this.exchangeRateService, never()).quoteFromVnd(any(), org.mockito.ArgumentMatchers.anyDouble());
    assertThat(response.getChargeCurrency()).isNull();
    assertThat(response.getChargeAmount()).isNull();
  }

  @Test
  void razorpayVerify_rejectsACaptureWhoseAmountOrCurrencyDiffersFromWhatWasQuoted() throws Exception {
    this.payment.setChargeCurrency("INR");
    this.payment.setChargeAmountMinor(41_926L);
    givenPaymentFound();
    final JSONObject details = new JSONObject().put("status", "captured").put("amount", 100).put("currency", "INR")
        .put("notes", new JSONObject().put("payment_id", this.payment.getId().toString()));
    when(this.razorpayService.fetchPaymentDetails("pay_cheap")).thenReturn(details);

    assertThatThrownBy(() -> this.service.verifyPayment(
        PaymentVerifyRequest.builder().razorpayPaymentId("pay_cheap").build(), this.payment.getUserId()))
        .isInstanceOf(BusinessException.class).hasMessageContaining("amount does not match");

    assertThat(this.payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    verify(this.eventPublisher, never()).publishEvent(any(Object.class));
  }

  @Test
  void razorpayVerify_acceptsACaptureThatMatchesTheQuotedAmount() throws Exception {
    this.payment.setChargeCurrency("INR");
    this.payment.setChargeAmountMinor(41_926L);
    givenPaymentFound();
    final JSONObject details = new JSONObject().put("status", "captured").put("amount", 41_926).put("currency", "INR")
        .put("notes", new JSONObject().put("payment_id", this.payment.getId().toString()));
    when(this.razorpayService.fetchPaymentDetails("pay_ok")).thenReturn(details);

    final PaymentDTO result = this.service.verifyPayment(
        PaymentVerifyRequest.builder().razorpayPaymentId("pay_ok").build(), this.payment.getUserId());

    assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
  }

  // ---- initiate idempotent + một payment thành công cho mỗi booking ----

  private com.nitditto.payload.request.PaymentInitiateRequest initiateRequest() {
    return com.nitditto.payload.request.PaymentInitiateRequest.builder().userId(this.payment.getUserId())
        .bookingId(this.bookingId).paymentGateway(PaymentGateway.VNPAY).amount(250.0).description("d").build();
  }

  private Payment pendingLink(final java.time.Instant createdAt) {
    final Payment pending = Payment.builder().userId(this.payment.getUserId()).bookingId(this.bookingId).amount(250.0)
        .provider(PaymentGateway.VNPAY).status(PaymentStatus.PENDING).checkoutUrl("https://vnpay.test/old-link").build();
    pending.setId(UUID.randomUUID());
    pending.setCreatedAt(createdAt);
    return pending;
  }

  private void givenPendingFor(final Payment pending) {
    when(this.paymentRepository.findFirstByBookingIdAndProviderAndStatusOrderByCreatedAtDesc(this.bookingId,
        PaymentGateway.VNPAY, PaymentStatus.PENDING)).thenReturn(Optional.of(pending));
  }

  @Test
  void initiate_calledAgain_returnsTheSameLink_withoutCreatingAnotherPayment() {
    final Payment pending = pendingLink(CLOCK.instant().minusSeconds(60));
    givenPendingFor(pending);

    final var response = this.service.initiatePayment(initiateRequest());

    assertThat(response.getPaymentId()).isEqualTo(pending.getId());
    assertThat(response.getCheckoutUrl()).isEqualTo("https://vnpay.test/old-link");
    verify(this.paymentRepository, never()).save(any(Payment.class));
  }

  @Test
  void initiate_createsANewPayment_whenTheOldLinkIsTooCloseToExpiry() {
    givenPendingFor(pendingLink(CLOCK.instant().minusSeconds(11 * 60)));
    givenSaveAssignsIds();

    final var response = this.service.initiatePayment(initiateRequest());

    assertThat(response.getCheckoutUrl()).contains("vnp_SecureHash=").doesNotContain("old-link");
    final ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
    verify(this.paymentRepository, times(2)).save(saved.capture());
    assertThat(saved.getAllValues().get(1).getCheckoutUrl()).isEqualTo(response.getCheckoutUrl());
  }

  @Test
  void initiate_doesNotReuseALinkForADifferentAmountOrUser() {
    final Payment pending = pendingLink(CLOCK.instant().minusSeconds(60));
    pending.setAmount(999.0);
    givenPendingFor(pending);
    givenSaveAssignsIds();

    final var response = this.service.initiatePayment(initiateRequest());

    assertThat(response.getPaymentId()).isNotEqualTo(pending.getId());
  }

  @Test
  void initiate_isRejected_whenTheBookingIsAlreadyPaid() {
    final Payment paid = pendingLink(CLOCK.instant());
    paid.setStatus(PaymentStatus.SUCCESS);
    when(this.paymentRepository.findByBookingIdAndStatusIn(this.bookingId,
        List.of(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED))).thenReturn(List.of(paid));

    assertThatThrownBy(() -> this.service.initiatePayment(initiateRequest()))
        .isInstanceOf(BusinessException.class).hasMessageContaining("already has a successful payment");
    verify(this.paymentRepository, never()).save(any(Payment.class));
  }

  @Test
  void aSuccessfulResult_setsPaidBookingId_soTheDatabaseCanEnforceOnePaymentPerBooking() {
    givenPaymentFound();

    this.service.checkPaymentVNPay(vnpResult("00", 25000));

    assertThat(this.payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    assertThat(this.payment.getPaidBookingId()).isEqualTo(this.bookingId);
  }

  @Test
  void aSecondSuccessfulPaymentForTheSameBooking_isMarkedDuplicate_notSuccess_andNothingIsPublished() {
    givenPaymentFound();
    final Payment first = pendingLink(CLOCK.instant());
    first.setStatus(PaymentStatus.SUCCESS);
    when(this.paymentRepository.findByBookingIdAndStatusIn(this.bookingId,
        List.of(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED))).thenReturn(List.of(first));

    final PaymentDTO result = this.service.checkPaymentVNPay(vnpResult("00", 25000));

    assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.DUPLICATE);
    assertThat(this.payment.getPaidBookingId()).isNull();
    assertThat(this.payment.getFailureReason()).contains("DUPLICATE PAYMENT").contains(first.getId().toString());
    verify(this.eventPublisher, never()).publishEvent(any(Object.class));
  }

  // ---- IPN của VNPay (server-to-server) ----

  @Test
  void ipn_success_updatesThePayment_andPublishesOnce_evenIfTheCustomerNeverComesBack() {
    givenPaymentFound();

    final var response = this.service.handleVnpayIpn(vnpResult("00", 25000));

    assertThat(response.RspCode()).isEqualTo("00");
    assertThat(this.payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    verify(this.eventPublisher, times(1)).publishEvent(any(PaymentSucceededEvent.class));
  }

  @Test
  void ipn_calledAgain_answers02_andDoesNotPublishAgain() {
    givenPaymentFound();

    this.service.handleVnpayIpn(vnpResult("00", 25000));
    final var second = this.service.handleVnpayIpn(vnpResult("00", 25000));

    assertThat(second.RspCode()).isEqualTo("02");
    verify(this.eventPublisher, times(1)).publishEvent(any(Object.class));
  }

  @Test
  void ipn_afterTheBrowserAlreadyConfirmed_answers02() {
    givenPaymentFound();
    this.service.checkPaymentVNPay(vnpResult("00", 25000));

    assertThat(this.service.handleVnpayIpn(vnpResult("00", 25000)).RspCode()).isEqualTo("02");
    verify(this.eventPublisher, times(1)).publishEvent(any(Object.class));
  }

  @Test
  void ipn_invalidSignature_answers97_andTouchesNothing() {
    final Map<String, String> params = vnpResult("00", 25000);
    params.put("vnp_Amount", "100"); // đổi sau khi ký

    assertThat(this.service.handleVnpayIpn(params).RspCode()).isEqualTo("97");
    verify(this.paymentRepository, never()).findById(any(UUID.class));
    verify(this.eventPublisher, never()).publishEvent(any(Object.class));
  }

  @Test
  void ipn_missingSignature_answers97() {
    final Map<String, String> params = vnpResult("00", 25000);
    params.remove("vnp_SecureHash");

    assertThat(this.service.handleVnpayIpn(params).RspCode()).isEqualTo("97");
  }

  @Test
  void ipn_unknownPayment_answers01() {
    when(this.paymentRepository.findById(this.payment.getId())).thenReturn(Optional.empty());

    assertThat(this.service.handleVnpayIpn(vnpResult("00", 25000)).RspCode()).isEqualTo("01");
    verify(this.eventPublisher, never()).publishEvent(any(Object.class));
  }

  @Test
  void ipn_validlySignedButWrongAmount_answers04_andDoesNotConfirm() {
    givenPaymentFound();

    assertThat(this.service.handleVnpayIpn(vnpResult("00", 100)).RspCode()).isEqualTo("04");
    assertThat(this.payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    verify(this.eventPublisher, never()).publishEvent(any(Object.class));
  }

  @Test
  void ipn_cancelledByCustomer_marksFailed_andAnswers00() {
    givenPaymentFound();

    assertThat(this.service.handleVnpayIpn(vnpResult("24", 25000)).RspCode()).isEqualTo("00");
    assertThat(this.payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    verify(this.eventPublisher, times(1)).publishEvent(any(PaymentFailedEvent.class));
  }

  @Test
  void ipn_aLateSuccess_afterTheBrowserReportedFailure_stillConfirmsThePayment() {
    givenPaymentFound();
    this.payment.setStatus(PaymentStatus.FAILED);

    assertThat(this.service.handleVnpayIpn(vnpResult("00", 25000)).RspCode()).isEqualTo("00");
    assertThat(this.payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
  }

  @Test
  void ipn_aRepeatedFailureOnAnAlreadyFailedPayment_answers02() {
    givenPaymentFound();
    this.payment.setStatus(PaymentStatus.FAILED);

    assertThat(this.service.handleVnpayIpn(vnpResult("24", 25000)).RspCode()).isEqualTo("02");
  }

  @Test
  void ipn_neverChangesAPaymentThatWasRefunded() {
    givenPaymentFound();
    this.payment.setStatus(PaymentStatus.REFUNDED);

    assertThat(this.service.handleVnpayIpn(vnpResult("00", 25000)).RspCode()).isEqualTo("02");
    assertThat(this.payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
  }

  @Test
  void ipn_aSecondSuccessForAPaidBooking_isRecordedAsDuplicate_andStillAcknowledged() {
    givenPaymentFound();
    final Payment first = pendingLink(CLOCK.instant());
    first.setStatus(PaymentStatus.SUCCESS);
    when(this.paymentRepository.findByBookingIdAndStatusIn(this.bookingId,
        List.of(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED))).thenReturn(List.of(first));

    assertThat(this.service.handleVnpayIpn(vnpResult("00", 25000)).RspCode()).isEqualTo("00");
    assertThat(this.payment.getStatus()).isEqualTo(PaymentStatus.DUPLICATE);
    verify(this.eventPublisher, never()).publishEvent(any(Object.class));
  }

  @Test
  void ipnEndpoint_answers99_whenTheServiceFailsUnexpectedly_soVnpayRetries() {
    final com.nitditto.service.PaymentService failing = org.mockito.Mockito.mock(com.nitditto.service.PaymentService.class);
    when(failing.handleVnpayIpn(any())).thenThrow(new IllegalStateException("db down"));

    final var response = new com.nitditto.controller.PaymentController(failing,
        org.mockito.Mockito.mock(com.nitditto.service.RefundService.class)).vnpayIpn(Map.of());

    assertThat(response.getBody().RspCode()).isEqualTo("99");
  }

  // save() gán id như JPA; findById() trả lại payment vừa lưu (bước attachLink nạp lại payment theo id).
  private void givenSaveAssignsIds() {
    final java.util.Map<UUID, Payment> store = new java.util.HashMap<>();
    when(this.paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
      final Payment saved = inv.getArgument(0);
      if (saved.getId() == null) {
        saved.setId(UUID.randomUUID());
      }
      store.put(saved.getId(), saved);
      return saved;
    });
    when(this.paymentRepository.findById(any(UUID.class)))
        .thenAnswer(inv -> Optional.ofNullable(store.get(inv.<UUID>getArgument(0))));
  }

  // ---- hạn link VNPay theo booking + IP thật của khách ----

  @Test
  void initiate_sendsTheCustomersIpAndTheBookingExpiryToVnpay_andReportsTheLinkExpiry() {
    givenSaveAssignsIds();
    final java.time.Instant bookingExpiry = java.time.Instant.now().plusSeconds(11 * 60);
    final var request = initiateRequest();
    request.setExpiresAt(bookingExpiry);

    final var response = this.service.initiatePayment(request, "203.0.113.5, 10.0.0.2");

    final java.net.URI url = java.net.URI.create(response.getCheckoutUrl());
    assertThat(url.getRawQuery()).contains("vnp_IpAddr=203.0.113.5&");
    assertThat(response.getExpiresAt()).isEqualTo(bookingExpiry.atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
        .withNano(0).toInstant());
    final ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
    verify(this.paymentRepository, times(2)).save(saved.capture());
    assertThat(saved.getAllValues().get(1).getLinkExpiresAt()).isEqualTo(response.getExpiresAt());
  }

  @Test
  void initiate_withoutAForwardedFor_usesTheLoopbackFallback_andAForgedValueNeverReachesVnpay() {
    givenSaveAssignsIds();

    final var plain = this.service.initiatePayment(initiateRequest(), null);
    final var forged = this.service.initiatePayment(initiateRequest(), "x&vnp_Amount=1");

    assertThat(java.net.URI.create(plain.getCheckoutUrl()).getRawQuery()).contains("vnp_IpAddr=127.0.0.1&");
    assertThat(java.net.URI.create(forged.getCheckoutUrl()).getRawQuery()).contains("vnp_IpAddr=127.0.0.1&");
  }

  @Test
  void initiate_reusesTheLinkUntilJustBeforeItsRealExpiry() {
    final Payment pending = pendingLink(CLOCK.instant().minusSeconds(60));
    pending.setLinkExpiresAt(CLOCK.instant().plusSeconds(5 * 60)); // booking cho 5 phút nữa
    givenPendingFor(pending);

    final var response = this.service.initiatePayment(initiateRequest());

    assertThat(response.getPaymentId()).isEqualTo(pending.getId());
    assertThat(response.getExpiresAt()).isEqualTo(pending.getLinkExpiresAt());
    verify(this.paymentRepository, never()).save(any(Payment.class));
  }

  @Test
  void initiate_doesNotReuseALinkThatIsAboutToExpire_evenIfItWasCreatedRecently() {
    final Payment pending = pendingLink(CLOCK.instant().minusSeconds(60));
    pending.setLinkExpiresAt(CLOCK.instant().plusSeconds(30)); // sắp hết hạn
    givenPendingFor(pending);
    givenSaveAssignsIds();

    final var response = this.service.initiatePayment(initiateRequest());

    assertThat(response.getPaymentId()).isNotEqualTo(pending.getId());
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
