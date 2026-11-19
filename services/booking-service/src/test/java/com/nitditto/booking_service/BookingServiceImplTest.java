package com.nitditto.booking_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nitditto.client.AirlineClient;
import com.nitditto.client.AncillaryClient;
import com.nitditto.client.FlightClient;
import com.nitditto.client.PaymentClient;
import com.nitditto.client.SeatClient;
import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.model.Booking;
import com.nitditto.payload.request.BookingRequest;
import com.nitditto.payload.request.PassengerRequest;
import com.nitditto.payload.request.PaymentInitiateRequest;
import com.nitditto.payload.response.AirlineResponse;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.FlightResponse;
import com.nitditto.payload.response.PaymentInitiateResponse;
import com.nitditto.repository.BookingRepository;
import com.nitditto.service.BookingConfirmationService;
import com.nitditto.service.BookingCreationService;
import com.nitditto.service.BookingEnrichmentService;
import com.nitditto.service.PassengerService;
import com.nitditto.service.SeatReservationService;
import com.nitditto.service.TicketService;
import com.nitditto.service.impl.BookingServiceImpl;
import com.nitditto.service.integration.FareIntegrationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

  @Mock
  private BookingRepository bookingRepository;
  @Mock
  private PassengerService passengerService;
  @Mock
  private TicketService ticketService;
  @Mock
  private FlightClient flightClient;
  @Mock
  private SeatClient seatClient;
  @Mock
  private AncillaryClient ancillaryClient;
  @Mock
  private PaymentClient paymentClient;
  @Mock
  private FareIntegrationService fareIntegrationService;
  @Mock
  private AirlineClient airlineClient;
  @Mock
  private SeatReservationService seatReservationService;
  @Mock
  private BookingEnrichmentService bookingEnrichmentService;
  @Mock
  private BookingConfirmationService bookingConfirmationService;
  @Mock
  private BookingCreationService bookingCreationService;

  private BookingServiceImpl service;

  private final UUID userId = UUID.randomUUID();
  private final UUID airlineId = UUID.randomUUID();
  private final UUID bookingId = UUID.randomUUID();
  private final UUID seatA = UUID.randomUUID();
  private final UUID seatB = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    this.service = new BookingServiceImpl(this.bookingRepository, this.passengerService, this.ticketService,
        this.flightClient, this.seatClient, this.ancillaryClient, this.paymentClient, this.fareIntegrationService,
        this.airlineClient, this.seatReservationService, this.bookingEnrichmentService,
        this.bookingConfirmationService, this.bookingCreationService);
  }

  @Test
  void createBooking_chargesFarePerPassenger_notOncePerBooking() {
    givenFlightAndPrices(1000.0, 150.0);
    givenBookingIsCreated();
    givenPaymentIsInitiated();

    this.service.createBooking(request(this.seatA, this.seatB), this.userId);

    final ArgumentCaptor<PaymentInitiateRequest> captor = ArgumentCaptor.forClass(PaymentInitiateRequest.class);
    verify(this.paymentClient).initiatePayment(captor.capture());
    // 2 hành khách x 1000 + phụ thu ghế 150 + hành lý/bảo hiểm 80 + suất ăn 20
    assertThat(captor.getValue().getAmount()).isEqualTo(2 * 1000.0 + 150.0 + 80.0 + 20.0);
    assertThat(captor.getValue().getBookingId()).isEqualTo(this.bookingId);
  }

  @Test
  void createBooking_readsRemoteData_thenHoldsSeats_thenCommitsBooking_thenInitiatesPayment() {
    givenFlightAndPrices(500.0, 0.0);
    givenBookingIsCreated();
    givenPaymentIsInitiated();

    this.service.createBooking(request(this.seatA), this.userId);

    final InOrder order = inOrder(this.flightClient, this.seatClient, this.seatReservationService,
        this.bookingCreationService, this.paymentClient);
    order.verify(this.flightClient).getFlightById(any());
    order.verify(this.seatClient).calculateSeatPrice(any());
    order.verify(this.seatReservationService).hold(this.userId, List.of(this.seatA));
    order.verify(this.bookingCreationService).createPending(any(), any(), any(), any());
    order.verify(this.paymentClient).initiatePayment(any());
  }

  @Test
  void createBooking_passesAirlineOfTheFlight_toTheNewBooking() {
    givenFlightAndPrices(500.0, 0.0);
    givenBookingIsCreated();
    givenPaymentIsInitiated();

    this.service.createBooking(request(this.seatA), this.userId);

    verify(this.bookingCreationService).createPending(any(), eq(this.userId), eq(this.airlineId),
        eq(List.of(this.seatA)));
  }

  @Test
  void createBooking_stopsWithoutCreatingBookingOrPayment_whenASeatIsAlreadyTaken() {
    givenFlightAndPrices(500.0, 0.0);
    doThrow(new BusinessException("One or more selected seats are no longer available"))
        .when(this.seatReservationService).hold(any(), any());

    assertThatThrownBy(() -> this.service.createBooking(request(this.seatA), this.userId))
        .isInstanceOf(BusinessException.class);

    verify(this.bookingCreationService, never()).createPending(any(), any(), any(), any());
    verify(this.paymentClient, never()).initiatePayment(any());
  }

  @Test
  void createBooking_doesNotHoldSeats_whenFlightIsUnknown() {
    when(this.flightClient.getFlightById(any())).thenReturn(ApiResponse.success(null));

    assertThatThrownBy(() -> this.service.createBooking(request(this.seatA), this.userId))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(this.seatReservationService, never()).hold(any(), any());
  }

  @Test
  void createBooking_releasesSeats_andSkipsPayment_whenBookingCannotBeCommitted() {
    givenFlightAndPrices(500.0, 0.0);
    when(this.bookingCreationService.createPending(any(), any(), any(), any()))
        .thenThrow(new CannotAcquireLockException("deadlock"));

    assertThatThrownBy(() -> this.service.createBooking(request(this.seatA), this.userId))
        .isInstanceOf(CannotAcquireLockException.class);

    verify(this.seatReservationService).releaseQuietly(this.userId, List.of(this.seatA));
    verify(this.paymentClient, never()).initiatePayment(any());
    verify(this.bookingCreationService, never()).abandonPending(any());
  }

  @Test
  void createBooking_abandonsBookingAndReleasesSeats_whenPaymentCannotBeInitiated() {
    givenFlightAndPrices(500.0, 0.0);
    givenBookingIsCreated();
    when(this.paymentClient.initiatePayment(any())).thenThrow(new IllegalStateException("payment down"));

    assertThatThrownBy(() -> this.service.createBooking(request(this.seatA), this.userId))
        .isInstanceOf(IllegalStateException.class);

    verify(this.bookingCreationService).abandonPending(this.bookingId);
    verify(this.seatReservationService).releaseQuietly(this.userId, List.of(this.seatA));
  }

  @Test
  void createBooking_stillReleasesSeatsAndRethrowsOriginalError_whenAbandoningFails() {
    givenFlightAndPrices(500.0, 0.0);
    givenBookingIsCreated();
    when(this.paymentClient.initiatePayment(any())).thenThrow(new IllegalStateException("payment down"));
    doThrow(new IllegalStateException("db down")).when(this.bookingCreationService).abandonPending(any());

    assertThatThrownBy(() -> this.service.createBooking(request(this.seatA), this.userId))
        .isInstanceOf(IllegalStateException.class).hasMessage("payment down");

    verify(this.seatReservationService).releaseQuietly(this.userId, List.of(this.seatA));
  }

  @Test
  void createBooking_treatsMissingAncillaryAndMealLists_asEmpty() {
    givenFlightAndPrices(100.0, 0.0);
    givenBookingIsCreated();
    givenPaymentIsInitiated();
    final BookingRequest request = request(this.seatA);
    request.setAncillaryIds(null);
    request.setMealIds(null);

    this.service.createBooking(request, this.userId);

    verify(this.ancillaryClient).calculateAncillariesPrice(List.of());
    verify(this.ancillaryClient).calculateMealPrice(List.of());
  }

  @Test
  void createBooking_rejectsTwoPassengersOnTheSameSeat() {
    assertThatThrownBy(() -> this.service.createBooking(request(this.seatA, this.seatA), this.userId))
        .isInstanceOf(BusinessException.class).hasMessageContaining("different seat");

    verify(this.seatReservationService, never()).hold(any(), any());
  }

  private void givenFlightAndPrices(final double fare, final double seatPrice) {
    final FlightResponse flight = new FlightResponse();
    final AirlineResponse airline = new AirlineResponse();
    airline.setId(this.airlineId);
    flight.setAirline(airline);
    when(this.flightClient.getFlightById(any())).thenReturn(ApiResponse.success(flight));
    when(this.fareIntegrationService.calculateFareTotal(any())).thenReturn(fare);
    when(this.seatClient.calculateSeatPrice(any())).thenReturn(seatPrice);
    when(this.ancillaryClient.calculateAncillariesPrice(any())).thenReturn(ApiResponse.success(80.0));
    when(this.ancillaryClient.calculateMealPrice(any())).thenReturn(ApiResponse.success(20.0));
  }

  private void givenBookingIsCreated() {
    when(this.bookingCreationService.createPending(any(), any(), any(), any())).thenAnswer(invocation -> {
      final Booking booking = new Booking();
      booking.setId(this.bookingId);
      booking.setBookingReference("BKTEST001");
      return booking;
    });
  }

  private void givenPaymentIsInitiated() {
    when(this.paymentClient.initiatePayment(any()))
        .thenReturn(ResponseEntity.ok(ApiResponse.success(new PaymentInitiateResponse())));
  }

  private BookingRequest request(final UUID... seatIds) {
    final List<PassengerRequest> passengers = java.util.Arrays.stream(seatIds)
        .map(seatId -> PassengerRequest.builder().seatInstanceId(seatId).build()).toList();
    return BookingRequest.builder().flightId(UUID.randomUUID()).flightInstanceId(UUID.randomUUID())
        .fareId(UUID.randomUUID()).passengers(passengers).ancillaryIds(List.of()).mealIds(List.of()).build();
  }
}
