package com.nitditto.service.impl;

import com.nitditto.client.FlightClient;
import com.nitditto.client.PaymentClient;
import com.nitditto.client.PricingClient;
import com.nitditto.enums.PaymentStatus;
import com.nitditto.enums.RefundStatus;
import com.nitditto.model.Booking;
import com.nitditto.payload.dto.PaymentDTO;
import com.nitditto.payload.request.RefundRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.FareRulesResponse;
import com.nitditto.payload.response.FlightInstanceResponse;
import com.nitditto.payload.response.RefundResponse;
import com.nitditto.service.BookingRefundService;
import com.nitditto.service.RefundPolicy;
import feign.FeignException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingRefundServiceImpl implements BookingRefundService {

  private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  private final PaymentClient paymentClient;
  private final PricingClient pricingClient;
  private final FlightClient flightClient;
  private final Clock clock;

  @Override
  public Outcome refundForCancellation(final Booking booking, final UUID requesterUserId) {
    final PaymentDTO payment = this.findPayment(booking.getId());
    if (payment == null || payment.getPaymentStatus() == null || payment.getAmount() == null) {
      log.warn("Confirmed booking {} has no payment record to refund", booking.getBookingReference());
      return new Outcome(null, null, "No successful payment was found for this booking, so nothing was refunded");
    }
    if (payment.getPaymentStatus() == PaymentStatus.REFUNDED) {
      // Lần huỷ trước đã hoàn tiền xong nhưng chưa kịp ghi trạng thái huỷ: không hoàn lần hai.
      return new Outcome(null, RefundStatus.SUCCESS, "A refund was already issued for this booking");
    }
    if (payment.getPaymentStatus() != PaymentStatus.SUCCESS) {
      return new Outcome(null, null, "No successful payment was found for this booking, so nothing was refunded");
    }

    final RefundPolicy.Decision decision = RefundPolicy.evaluate(this.fareRulesOf(booking), payment.getAmount(),
        this.departureOf(booking), this.clock.instant());
    if (!decision.refundable()) {
      log.info("No refund for booking {}: {}", booking.getBookingReference(), decision.note());
      return new Outcome(0.0, null, decision.note());
    }

    // Lỗi ở đây (payment-service down...) nổi lên: booking CHƯA bị huỷ, người dùng thử lại được.
    final RefundResponse refund = this.paymentClient.requestRefund(RefundRequest.builder()
        .bookingId(booking.getId()).amount(decision.amount())
        .reason("Booking " + booking.getBookingReference() + " cancelled by the customer")
        .requestedBy(requesterUserId == null ? null : requesterUserId.toString()).build()).getData();
    return new Outcome(decision.amount(), refund.getStatus(), noteFor(decision, refund));
  }

  private static String noteFor(final RefundPolicy.Decision decision, final RefundResponse refund) {
    if (refund.getStatus() == RefundStatus.SUCCESS) {
      return decision.note();
    }
    // PENDING/FAILED: hãng xử lý tiếp; khách không cần làm gì thêm.
    return decision.note() + ". The refund could not be completed automatically and will be processed manually";
  }

  private PaymentDTO findPayment(final UUID bookingId) {
    final ApiResponse<Map<UUID, PaymentDTO>> response = this.paymentClient.getPaymentsByBookingIds(List.of(bookingId));
    return response == null || response.getData() == null ? null : response.getData().get(bookingId);
  }

  /** Fare không có rules (404 từ pricing-service) = không hoàn; lỗi khác nổi lên (không đoán chính sách khi chưa biết). */
  private FareRulesResponse fareRulesOf(final Booking booking) {
    if (booking.getFareId() == null) {
      return null;
    }
    try {
      final ApiResponse<FareRulesResponse> response = this.pricingClient.getFareRulesByFareId(booking.getFareId());
      return response == null ? null : response.getData();
    } catch (final FeignException.NotFound ex) {
      return null;
    }
  }

  private Instant departureOf(final Booking booking) {
    final ResponseEntity<ApiResponse<FlightInstanceResponse>> response =
        this.flightClient.getFlightInstance(booking.getFlightInstanceId());
    final FlightInstanceResponse flight = response.getBody() == null ? null : response.getBody().getData();
    final LocalDateTime departure = flight == null ? null : flight.getDepartureDateTime();
    if (departure == null) {
      return null;
    }
    final ZoneId zone = flight.getDepartureAirport() != null && flight.getDepartureAirport().getTimeZone() != null
        ? flight.getDepartureAirport().getTimeZone() : DEFAULT_ZONE;
    return departure.atZone(zone).toInstant();
  }
}
