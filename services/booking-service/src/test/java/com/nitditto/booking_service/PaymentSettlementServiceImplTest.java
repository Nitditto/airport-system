package com.nitditto.booking_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nitditto.enums.BookingStatus;
import com.nitditto.exception.BusinessException;
import com.nitditto.exception.PaidBookingNotConfirmableException;
import com.nitditto.service.BookingConfirmationService;
import com.nitditto.service.BookingRefundService;
import com.nitditto.service.impl.PaymentSettlementServiceImpl;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentSettlementServiceImplTest {

  @Mock
  private BookingConfirmationService confirmationService;
  @Mock
  private BookingRefundService refundService;

  private PaymentSettlementServiceImpl service;
  private final UUID bookingId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    this.service = new PaymentSettlementServiceImpl(this.confirmationService, this.refundService);
  }

  @Test
  void aConfirmableBooking_isConfirmed_andNothingIsRefunded() {
    when(this.confirmationService.confirmPayment(this.bookingId)).thenReturn(BookingStatus.CONFIRMED);

    assertThat(this.service.settle(this.bookingId)).isEqualTo(BookingStatus.CONFIRMED);
    verify(this.refundService, never()).refundUnfulfillablePayment(org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any());
  }

  @Test
  void aPaymentThatCannotBeUsed_isRefundedInFull_andTheBookingStatusIsReported() {
    doThrow(new PaidBookingNotConfirmableException(this.bookingId, UUID.randomUUID(), BookingStatus.EXPIRED,
        "expired but paid")).when(this.confirmationService).confirmPayment(this.bookingId);

    assertThat(this.service.settle(this.bookingId)).isEqualTo(BookingStatus.EXPIRED);

    verify(this.refundService).refundUnfulfillablePayment(this.bookingId, "expired but paid");
  }

  @Test
  void ifTheRefundRequestCannotBeRecorded_theErrorSurfaces_soKafkaRetries() {
    doThrow(new PaidBookingNotConfirmableException(this.bookingId, UUID.randomUUID(), BookingStatus.CANCELLED, "x"))
        .when(this.confirmationService).confirmPayment(this.bookingId);
    doThrow(new IllegalStateException("payment-service down")).when(this.refundService)
        .refundUnfulfillablePayment(this.bookingId, "x");

    assertThatThrownBy(() -> this.service.settle(this.bookingId)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void otherBusinessErrors_arePropagatedUntouched_andNeverTriggerARefund() {
    doThrow(new BusinessException("Booking has no successful payment")).when(this.confirmationService)
        .confirmPayment(this.bookingId);

    assertThatThrownBy(() -> this.service.settle(this.bookingId)).isInstanceOf(BusinessException.class);
    verify(this.refundService, never()).refundUnfulfillablePayment(org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any());
  }
}
