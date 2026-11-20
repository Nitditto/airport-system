package com.nitditto.service.impl;

import com.nitditto.enums.BookingStatus;
import com.nitditto.exception.PaidBookingNotConfirmableException;
import com.nitditto.service.BookingConfirmationService;
import com.nitditto.service.BookingRefundService;
import com.nitditto.service.PaymentSettlementService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentSettlementServiceImpl implements PaymentSettlementService {

  private final BookingConfirmationService bookingConfirmationService;
  private final BookingRefundService bookingRefundService;

  @Override
  public BookingStatus settle(final UUID bookingId) {
    try {
      return this.bookingConfirmationService.confirmPayment(bookingId);
    } catch (final PaidBookingNotConfirmableException ex) {
      log.warn("Booking {} is {} but payment {} succeeded: refunding the customer", bookingId, ex.getBookingStatus(),
          ex.getPaymentId());
      // Lỗi gọi payment-service nổi lên: Kafka retry; đường HTTP của payment-service chỉ ghi log (Kafka vẫn giữ event).
      this.bookingRefundService.refundUnfulfillablePayment(bookingId, ex.getMessage());
      return ex.getBookingStatus();
    }
  }
}
