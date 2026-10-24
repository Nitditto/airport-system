package com.nitditto.booking_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nitditto.client.PaymentClient;
import com.nitditto.enums.BookingStatus;
import com.nitditto.enums.PaymentStatus;
import com.nitditto.exception.BusinessException;
import com.nitditto.model.Booking;
import com.nitditto.payload.dto.PaymentDTO;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.repository.BookingRepository;
import com.nitditto.service.SeatReservationService;
import com.nitditto.service.TicketService;
import com.nitditto.service.impl.BookingConfirmationServiceImpl;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingConfirmationServiceImplTest {

  private static final Instant NOW = Instant.parse("2026-10-24T08:00:00Z");

  @Mock
  private BookingRepository bookingRepository;
  @Mock
  private TicketService ticketService;
  @Mock
  private PaymentClient paymentClient;
  @Mock
  private SeatReservationService seatReservationService;

  private BookingConfirmationServiceImpl service;

  private final UUID userId = UUID.randomUUID();
  private final UUID bookingId = UUID.randomUUID();
  private final List<UUID> seats = List.of(UUID.randomUUID(), UUID.randomUUID());

  @BeforeEach
  void setUp() {
    this.service = new BookingConfirmationServiceImpl(this.bookingRepository, this.ticketService,
        this.paymentClient, this.seatReservationService, Clock.fixed(NOW, ZoneOffset.UTC), 15);
  }

  @Test
  void confirmPayment_confirmsBooking_bookSeats_andIssuesTickets_whenPaymentSucceeded() {
    final Booking booking = pendingBooking(NOW.minusSeconds(60));
    givenPayment(PaymentStatus.SUCCESS);

    final BookingStatus status = this.service.confirmPayment(this.bookingId);

    assertThat(status).isEqualTo(BookingStatus.CONFIRMED);
    assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
    assertThat(booking.isTicketIssued()).isTrue();
    verify(this.seatReservationService).confirmBooked(this.userId, this.seats);
    verify(this.ticketService).issueTicketsForBooking(this.bookingId);
  }

  @Test
  void confirmPayment_isIdempotent_forAnAlreadyConfirmedBooking() {
    final Booking booking = pendingBooking(NOW.minusSeconds(60));
    booking.setBookingStatus(BookingStatus.CONFIRMED);

    assertThat(this.service.confirmPayment(this.bookingId)).isEqualTo(BookingStatus.CONFIRMED);

    verify(this.paymentClient, never()).getPaymentsByBookingIds(any());
    verify(this.seatReservationService, never()).confirmBooked(any(), any());
  }

  @Test
  void confirmPayment_rejectsBookingWithoutSuccessfulPayment_soCallersCannotSkipPaying() {
    final Booking booking = pendingBooking(NOW.minusSeconds(60));
    givenPayment(PaymentStatus.PENDING);

    assertThatThrownBy(() -> this.service.confirmPayment(this.bookingId))
        .isInstanceOf(BusinessException.class).hasMessageContaining("no successful payment");

    assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.PENDING);
    verify(this.seatReservationService, never()).confirmBooked(any(), any());
  }

  @Test
  void confirmPayment_rejectsCancelledBooking() {
    final Booking booking = pendingBooking(NOW.minusSeconds(60));
    booking.setBookingStatus(BookingStatus.CANCELLED);

    assertThatThrownBy(() -> this.service.confirmPayment(this.bookingId)).isInstanceOf(BusinessException.class);
  }

  @Test
  void confirmPayment_keepsBookingPending_whenSeatsCannotBeConfirmed() {
    final Booking booking = pendingBooking(NOW.minusSeconds(60));
    givenPayment(PaymentStatus.SUCCESS);
    doThrow(new BusinessException("Seats can no longer be confirmed for this booking"))
        .when(this.seatReservationService).confirmBooked(this.userId, this.seats);

    assertThatThrownBy(() -> this.service.confirmPayment(this.bookingId)).isInstanceOf(BusinessException.class);

    assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.PENDING);
    verify(this.ticketService, never()).issueTicketsForBooking(any());
  }

  @Test
  void expireIfStale_expiresUnpaidBookingPastTheWindow_andReleasesItsSeats() {
    final Booking booking = pendingBooking(NOW.minusSeconds(16 * 60));
    givenPayment(PaymentStatus.PENDING);

    assertThat(this.service.expireIfStale(this.bookingId)).isEqualTo(BookingStatus.EXPIRED);

    assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.EXPIRED);
    verify(this.ticketService).expireTicketsForBooking(this.bookingId);
    verify(this.seatReservationService).releaseQuietly(this.userId, this.seats);
  }

  @Test
  void expireIfStale_leavesRecentBookingPending() {
    final Booking booking = pendingBooking(NOW.minusSeconds(5 * 60));
    givenPayment(PaymentStatus.PENDING);

    assertThat(this.service.expireIfStale(this.bookingId)).isEqualTo(BookingStatus.PENDING);

    assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.PENDING);
  }

  @Test
  void expireIfStale_neverExpiresABookingThatWasPaid() {
    final Booking booking = pendingBooking(NOW.minusSeconds(60 * 60));
    givenPayment(PaymentStatus.SUCCESS);

    assertThat(this.service.expireIfStale(this.bookingId)).isEqualTo(BookingStatus.PENDING);

    assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.PENDING);
    verify(this.seatReservationService, never()).releaseQuietly(any(), any());
  }

  private Booking pendingBooking(final Instant createdAt) {
    final Booking booking = Booking.builder().userId(this.userId).bookingReference("BK12345678")
        .seatInstanceIds(this.seats).bookingStatus(BookingStatus.PENDING).build();
    booking.setId(this.bookingId);
    booking.setCreatedAt(createdAt);
    when(this.bookingRepository.findByIdForUpdate(this.bookingId)).thenReturn(Optional.of(booking));
    return booking;
  }

  private void givenPayment(final PaymentStatus status) {
    final PaymentDTO payment = PaymentDTO.builder().id(UUID.randomUUID()).bookingId(this.bookingId)
        .paymentStatus(status).amount(100.0).build();
    when(this.paymentClient.getPaymentsByBookingIds(List.of(this.bookingId)))
        .thenReturn(ApiResponse.success(Map.of(this.bookingId, payment)));
  }
}
