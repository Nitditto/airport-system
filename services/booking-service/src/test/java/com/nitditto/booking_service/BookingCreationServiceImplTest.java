package com.nitditto.booking_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nitditto.enums.BookingStatus;
import com.nitditto.model.Booking;
import com.nitditto.payload.request.BookingRequest;
import com.nitditto.payload.request.PassengerRequest;
import com.nitditto.repository.BookingRepository;
import com.nitditto.service.PassengerService;
import com.nitditto.service.TicketService;
import com.nitditto.service.impl.BookingCreationServiceImpl;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingCreationServiceImplTest {

  @Mock
  private BookingRepository bookingRepository;
  @Mock
  private PassengerService passengerService;
  @Mock
  private TicketService ticketService;
  private BookingCreationServiceImpl service;

  private final UUID bookingId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    this.service = new BookingCreationServiceImpl(this.bookingRepository, this.passengerService, this.ticketService);
  }

  @Test
  void createPending_savesPendingBookingWithAirline_thenPassengersAndTickets() {
    final UUID userId = UUID.randomUUID();
    final UUID airlineId = UUID.randomUUID();
    final UUID seatId = UUID.randomUUID();
    when(this.bookingRepository.existsByBookingReference(any())).thenReturn(false);
    when(this.bookingRepository.saveAndFlush(any())).thenAnswer(invocation -> {
      final Booking booking = invocation.getArgument(0);
      booking.setId(this.bookingId);
      return booking;
    });
    when(this.passengerService.createPassengers(any(), any(), any())).thenReturn(List.of());

    final Booking saved = this.service.createPending(request(seatId), userId, airlineId, List.of(seatId));

    final ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
    verify(this.bookingRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getBookingStatus()).isEqualTo(BookingStatus.PENDING);
    assertThat(captor.getValue().getAirlineId()).isEqualTo(airlineId);
    assertThat(captor.getValue().getBookingReference()).startsWith("BK");
    assertThat(saved.getId()).isEqualTo(this.bookingId);
    verify(this.passengerService).createPassengers(any(), any(), any());
    verify(this.ticketService).generateTicketsForBooking(any(), any());
  }

  @Test
  void abandonPending_cancelsBookingAndTickets_whenStillPending() {
    final Booking booking = booking(BookingStatus.PENDING);
    when(this.bookingRepository.findByIdForUpdate(this.bookingId)).thenReturn(Optional.of(booking));

    this.service.abandonPending(this.bookingId);

    assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.CANCELLED);
    verify(this.bookingRepository).save(booking);
    verify(this.ticketService).cancelTicketsForBooking(this.bookingId);
  }

  @Test
  void abandonPending_leavesBookingUntouched_whenItIsNoLongerPending() {
    final Booking booking = booking(BookingStatus.CONFIRMED);
    when(this.bookingRepository.findByIdForUpdate(this.bookingId)).thenReturn(Optional.of(booking));

    this.service.abandonPending(this.bookingId);

    assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
    verify(this.bookingRepository, never()).save(any());
    verify(this.ticketService, never()).cancelTicketsForBooking(any());
  }

  @Test
  void abandonPending_doesNothing_whenBookingDoesNotExist() {
    when(this.bookingRepository.findByIdForUpdate(this.bookingId)).thenReturn(Optional.empty());

    this.service.abandonPending(this.bookingId);

    verify(this.bookingRepository, never()).save(any());
  }

  private Booking booking(final BookingStatus status) {
    final Booking booking = new Booking();
    booking.setId(this.bookingId);
    booking.setBookingStatus(status);
    return booking;
  }

  private static BookingRequest request(final UUID seatId) {
    return BookingRequest.builder().flightId(UUID.randomUUID()).flightInstanceId(UUID.randomUUID())
        .fareId(UUID.randomUUID()).passengers(List.of(PassengerRequest.builder().seatInstanceId(seatId).build()))
        .ancillaryIds(List.of()).mealIds(List.of()).build();
  }
}
