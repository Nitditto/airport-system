package com.nitditto.booking_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nitditto.client.FlightClient;
import com.nitditto.client.PaymentClient;
import com.nitditto.client.PricingClient;
import com.nitditto.enums.PaymentStatus;
import com.nitditto.enums.RefundStatus;
import com.nitditto.model.Booking;
import com.nitditto.payload.dto.PaymentDTO;
import com.nitditto.payload.request.RefundRequest;
import com.nitditto.payload.response.AirportResponse;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.FareRulesResponse;
import com.nitditto.payload.response.FlightInstanceResponse;
import com.nitditto.payload.response.RefundResponse;
import com.nitditto.service.BookingRefundService;
import com.nitditto.service.impl.BookingRefundServiceImpl;
import feign.FeignException;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class BookingRefundServiceImplTest {

  private static final Instant NOW = Instant.parse("2026-11-20T03:00:00Z");
  private static final double PAID = 1_150_000.0;

  @Mock
  private PaymentClient paymentClient;
  @Mock
  private PricingClient pricingClient;
  @Mock
  private FlightClient flightClient;

  private BookingRefundServiceImpl service;

  private final Booking booking = new Booking();
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    this.service = new BookingRefundServiceImpl(this.paymentClient, this.pricingClient, this.flightClient,
        Clock.fixed(NOW, ZoneOffset.UTC));
    this.booking.setId(UUID.randomUUID());
    this.booking.setBookingReference("BKTEST001");
    this.booking.setFareId(UUID.randomUUID());
    this.booking.setFlightInstanceId(UUID.randomUUID());
  }

  @Test
  void refundableFare_requestsTheAmountAfterFee_andReportsTheGatewayResult() {
    this.givenPayment(PaymentStatus.SUCCESS);
    this.givenRules(true, 100_000.0, 3);
    this.givenDeparture(NOW.plusSeconds(10L * 24 * 3600), "Asia/Ho_Chi_Minh");
    when(this.paymentClient.requestRefund(any())).thenReturn(ApiResponse.success(this.refund(RefundStatus.SUCCESS)));

    final BookingRefundService.Outcome outcome = this.service.refundForCancellation(this.booking, this.userId);

    final ArgumentCaptor<RefundRequest> sent = ArgumentCaptor.forClass(RefundRequest.class);
    verify(this.paymentClient).requestRefund(sent.capture());
    assertThat(sent.getValue().getBookingId()).isEqualTo(this.booking.getId());
    assertThat(sent.getValue().getAmount()).isEqualTo(1_050_000.0);
    assertThat(sent.getValue().getRequestedBy()).isEqualTo(this.userId.toString());
    assertThat(outcome.amount()).isEqualTo(1_050_000.0);
    assertThat(outcome.status()).isEqualTo(RefundStatus.SUCCESS);
    assertThat(outcome.note()).contains("cancellation fee of 100000");
  }

  @Test
  void nonRefundableFare_isCancelledWithoutAnyGatewayCall() {
    this.givenPayment(PaymentStatus.SUCCESS);
    this.givenRules(false, 0.0, null);
    this.givenDeparture(NOW.plusSeconds(10L * 24 * 3600), "Asia/Ho_Chi_Minh");

    final BookingRefundService.Outcome outcome = this.service.refundForCancellation(this.booking, this.userId);

    verify(this.paymentClient, never()).requestRefund(any());
    assertThat(outcome.amount()).isZero();
    assertThat(outcome.status()).isNull();
    assertThat(outcome.note()).contains("not refundable");
  }

  @Test
  void fareWithoutRules_pricingAnswers404_meansNoRefund() {
    this.givenPayment(PaymentStatus.SUCCESS);
    when(this.pricingClient.getFareRulesByFareId(any())).thenThrow(feignError(404));
    this.givenDeparture(NOW.plusSeconds(10L * 24 * 3600), "Asia/Ho_Chi_Minh");

    final BookingRefundService.Outcome outcome = this.service.refundForCancellation(this.booking, this.userId);

    verify(this.paymentClient, never()).requestRefund(any());
    assertThat(outcome.note()).contains("no refund rules");
  }

  @Test
  void departureTimeIsInterpretedInTheDepartureAirportsTimeZone() {
    this.givenPayment(PaymentStatus.SUCCESS);
    this.givenRules(true, 0.0, 3);
    // Giờ bay 10:00 giờ Việt Nam ngày 23/11 = 03:00Z, đúng 3 ngày sau NOW (20/11 03:00Z): còn trong hạn (biên).
    this.givenDeparture(LocalDateTime.of(2026, 11, 23, 10, 0), "Asia/Ho_Chi_Minh");
    when(this.paymentClient.requestRefund(any())).thenReturn(ApiResponse.success(this.refund(RefundStatus.SUCCESS)));

    assertThat(this.service.refundForCancellation(this.booking, this.userId).status()).isEqualTo(RefundStatus.SUCCESS);
  }

  @Test
  void sameWallClockTimeInAnEarlierTimeZone_isAlreadyPastTheDeadline() {
    this.givenPayment(PaymentStatus.SUCCESS);
    this.givenRules(true, 0.0, 3);
    // Cùng giờ treo tường 10:00 ngày 23/11 nhưng ở múi giờ UTC+14 là 20:00Z ngày 22/11: chưa đủ 3 ngày so với NOW.
    this.givenDeparture(LocalDateTime.of(2026, 11, 23, 10, 0), "Pacific/Kiritimati");

    final BookingRefundService.Outcome outcome = this.service.refundForCancellation(this.booking, this.userId);

    verify(this.paymentClient, never()).requestRefund(any());
    assertThat(outcome.note()).contains("deadline has passed");
  }

  @Test
  void alreadyRefundedPayment_isNotRefundedTwice() {
    this.givenPayment(PaymentStatus.REFUNDED);

    final BookingRefundService.Outcome outcome = this.service.refundForCancellation(this.booking, this.userId);

    verify(this.paymentClient, never()).requestRefund(any());
    assertThat(outcome.status()).isEqualTo(RefundStatus.SUCCESS);
    assertThat(outcome.note()).contains("already issued");
  }

  @Test
  void missingPayment_cancelsWithANote_andNoGatewayCall() {
    when(this.paymentClient.getPaymentsByBookingIds(any())).thenReturn(ApiResponse.success(Map.of()));

    final BookingRefundService.Outcome outcome = this.service.refundForCancellation(this.booking, this.userId);

    verify(this.paymentClient, never()).requestRefund(any());
    assertThat(outcome.status()).isNull();
    assertThat(outcome.note()).contains("No successful payment");
  }

  @Test
  void gatewayFailure_isReportedAsManualFollowUp_notAsAnError() {
    this.givenPayment(PaymentStatus.SUCCESS);
    this.givenRules(true, 0.0, null);
    this.givenDeparture(NOW.plusSeconds(10L * 24 * 3600), "Asia/Ho_Chi_Minh");
    when(this.paymentClient.requestRefund(any())).thenReturn(ApiResponse.success(this.refund(RefundStatus.FAILED)));

    final BookingRefundService.Outcome outcome = this.service.refundForCancellation(this.booking, this.userId);

    assertThat(outcome.status()).isEqualTo(RefundStatus.FAILED);
    assertThat(outcome.amount()).isEqualTo(PAID);
    assertThat(outcome.note()).contains("processed manually");
  }

  @Test
  void paymentServiceDown_failsTheCancellation_soNoRefundRequestIsLost() {
    this.givenPayment(PaymentStatus.SUCCESS);
    this.givenRules(true, 0.0, null);
    this.givenDeparture(NOW.plusSeconds(10L * 24 * 3600), "Asia/Ho_Chi_Minh");
    when(this.paymentClient.requestRefund(any())).thenThrow(feignError(503));

    assertThatThrownBy(() -> this.service.refundForCancellation(this.booking, this.userId))
        .isInstanceOf(FeignException.class);
  }

  @Test
  void pricingOutage_isNotMistakenForNoRules() {
    this.givenPayment(PaymentStatus.SUCCESS);
    when(this.pricingClient.getFareRulesByFareId(any())).thenThrow(feignError(503));

    assertThatThrownBy(() -> this.service.refundForCancellation(this.booking, this.userId))
        .isInstanceOf(FeignException.class);
    verify(this.paymentClient, never()).requestRefund(any());
  }

  // ---- helpers ----

  // ---- hoàn toàn bộ khi booking không thể thực hiện (thanh toán đến muộn / mất ghế) ----

  @Test
  void unfulfillablePayment_isRefundedInFull_withoutApplyingTheFareRules() {
    this.givenPayment(PaymentStatus.SUCCESS);
    when(this.paymentClient.requestRefund(any())).thenReturn(ApiResponse.success(this.refund(RefundStatus.SUCCESS)));

    this.service.refundUnfulfillablePayment(this.booking.getId(), "booking expired");

    final ArgumentCaptor<RefundRequest> sent = ArgumentCaptor.forClass(RefundRequest.class);
    verify(this.paymentClient).requestRefund(sent.capture());
    assertThat(sent.getValue().getBookingId()).isEqualTo(this.booking.getId());
    assertThat(sent.getValue().getAmount()).isEqualTo(PAID); // toàn bộ, không trừ phí huỷ
    assertThat(sent.getValue().getReason()).contains("booking expired");
    assertThat(sent.getValue().getRequestedBy()).isEqualTo("system");
    verify(this.pricingClient, never()).getFareRulesByFareId(any());
    verify(this.flightClient, never()).getFlightInstance(any());
  }

  @Test
  void unfulfillablePayment_alreadyRefundedOrNotPaid_isLeftAlone() {
    for (final PaymentStatus status : new PaymentStatus[] {PaymentStatus.REFUNDED, PaymentStatus.PENDING,
        PaymentStatus.DUPLICATE}) {
      this.givenPayment(status);
      this.service.refundUnfulfillablePayment(this.booking.getId(), "x");
    }
    verify(this.paymentClient, never()).requestRefund(any());
  }

  @Test
  void unfulfillablePayment_withNoPaymentRecord_doesNothing() {
    when(this.paymentClient.getPaymentsByBookingIds(any())).thenReturn(ApiResponse.success(Collections.emptyMap()));

    this.service.refundUnfulfillablePayment(this.booking.getId(), "x");

    verify(this.paymentClient, never()).requestRefund(any());
  }

  @Test
  void unfulfillablePayment_aGatewayFailureDoesNotThrow_butIsLeftForManualFollowUp() {
    this.givenPayment(PaymentStatus.SUCCESS);
    when(this.paymentClient.requestRefund(any())).thenReturn(ApiResponse.success(this.refund(RefundStatus.FAILED)));

    this.service.refundUnfulfillablePayment(this.booking.getId(), "x"); // không ném: yêu cầu đã được ghi nhận
  }

  @Test
  void unfulfillablePayment_whenPaymentServiceCannotBeReached_theErrorSurfaces_soTheCallerCanRetry() {
    this.givenPayment(PaymentStatus.SUCCESS);
    when(this.paymentClient.requestRefund(any())).thenThrow(feignError(503));

    org.assertj.core.api.Assertions.assertThatThrownBy(
        () -> this.service.refundUnfulfillablePayment(this.booking.getId(), "x")).isInstanceOf(FeignException.class);
  }

  private void givenPayment(final PaymentStatus status) {
    final PaymentDTO payment = new PaymentDTO();
    payment.setPaymentStatus(status);
    payment.setAmount(PAID);
    when(this.paymentClient.getPaymentsByBookingIds(any()))
        .thenReturn(ApiResponse.success(Map.of(this.booking.getId(), payment)));
  }

  private void givenRules(final Boolean refundable, final Double fee, final Integer deadlineDays) {
    final FareRulesResponse rules = new FareRulesResponse();
    rules.setIsRefundable(refundable);
    rules.setCancellationFee(fee);
    rules.setRefundDeadlineDays(deadlineDays);
    when(this.pricingClient.getFareRulesByFareId(any())).thenReturn(ApiResponse.success(rules));
  }

  private void givenDeparture(final Instant departure, final String zone) {
    this.givenDeparture(LocalDateTime.ofInstant(departure, ZoneId.of(zone)), zone);
  }

  private void givenDeparture(final LocalDateTime localDeparture, final String zone) {
    final FlightInstanceResponse flight = new FlightInstanceResponse();
    flight.setDepartureDateTime(localDeparture);
    final AirportResponse airport = new AirportResponse();
    airport.setTimeZone(ZoneId.of(zone));
    flight.setDepartureAirport(airport);
    when(this.flightClient.getFlightInstance(any())).thenReturn(ResponseEntity.ok(ApiResponse.success(flight)));
  }

  private RefundResponse refund(final RefundStatus status) {
    return RefundResponse.builder().refundId(UUID.randomUUID()).bookingId(this.booking.getId()).status(status).build();
  }

  private static FeignException feignError(final int status) {
    final Request request = Request.create(Request.HttpMethod.GET, "/x", Collections.emptyMap(), null,
        StandardCharsets.UTF_8, null);
    return FeignException.errorStatus("call", Response.builder().status(status).reason("x").request(request)
        .headers(Collections.emptyMap()).build());
  }
}
