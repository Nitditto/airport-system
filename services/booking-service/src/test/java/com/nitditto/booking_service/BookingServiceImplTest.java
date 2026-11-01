package com.nitditto.booking_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nitditto.client.AirlineClient;
import com.nitditto.client.AncillaryClient;
import com.nitditto.client.FlightClient;
import com.nitditto.client.PaymentClient;
import com.nitditto.client.SeatClient;
import com.nitditto.exception.BusinessException;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

  private BookingServiceImpl service;

  private final UUID userId = UUID.randomUUID();
  private final UUID seatA = UUID.randomUUID();
  private final UUID seatB = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    this.service = new BookingServiceImpl(this.bookingRepository, this.passengerService, this.ticketService,
        this.flightClient, this.seatClient, this.ancillaryClient, this.paymentClient, this.fareIntegrationService,
        this.airlineClient, this.seatReservationService, this.bookingEnrichmentService,
        this.bookingConfirmationService);
  }

  @Test
  void createBooking_chargesFarePerPassenger_notOncePerBooking() {
    givenHappyPath(1000.0, 150.0);

    this.service.createBooking(request(this.seatA, this.seatB), this.userId);

    final ArgumentCaptor<PaymentInitiateRequest> captor = ArgumentCaptor.forClass(PaymentInitiateRequest.class);
    verify(this.paymentClient).initiatePayment(captor.capture());
    // 2 hành khách x 1000 + phụ thu ghế 150 + hành lý/bảo hiểm 80 + suất ăn 20
    assertThat(captor.getValue().getAmount()).isEqualTo(2 * 1000.0 + 150.0 + 80.0 + 20.0);
  }

  @Test
  void createBooking_holdsSeatsBeforeCreatingAnything() {
    givenHappyPath(500.0, 0.0);

    this.service.createBooking(request(this.seatA), this.userId);

    verify(this.seatReservationService).hold(this.userId, List.of(this.seatA));
  }

  @Test
  void createBooking_stopsWithoutCreatingBookingOrPayment_whenASeatIsAlreadyTaken() {
    doThrow(new BusinessException("One or more selected seats are no longer available"))
        .when(this.seatReservationService).hold(any(), any());

    assertThatThrownBy(() -> this.service.createBooking(request(this.seatA), this.userId))
        .isInstanceOf(BusinessException.class);

    verify(this.bookingRepository, never()).saveAndFlush(any());
    verify(this.paymentClient, never()).initiatePayment(any());
  }

  @Test
  void createBooking_releasesHeldSeats_whenPaymentCannotBeInitiated() {
    givenHappyPath(500.0, 0.0);
    when(this.paymentClient.initiatePayment(any())).thenThrow(new IllegalStateException("payment down"));

    assertThatThrownBy(() -> this.service.createBooking(request(this.seatA), this.userId))
        .isInstanceOf(IllegalStateException.class);

    verify(this.seatReservationService).releaseQuietly(this.userId, List.of(this.seatA));
  }

  @Test
  void createBooking_rejectsTwoPassengersOnTheSameSeat() {
    assertThatThrownBy(() -> this.service.createBooking(request(this.seatA, this.seatA), this.userId))
        .isInstanceOf(BusinessException.class).hasMessageContaining("different seat");

    verify(this.seatReservationService, never()).hold(any(), any());
  }

  private void givenHappyPath(final double fare, final double seatPrice) {
    final FlightResponse flight = new FlightResponse();
    final AirlineResponse airline = new AirlineResponse();
    airline.setId(UUID.randomUUID());
    flight.setAirline(airline);
    when(this.flightClient.getFlightById(any())).thenReturn(ResponseEntity.ok(ApiResponse.success(flight)));
    when(this.bookingRepository.existsByBookingReference(any())).thenReturn(false);
    when(this.bookingRepository.saveAndFlush(any())).thenAnswer(invocation -> {
      final Booking booking = invocation.getArgument(0);
      booking.setId(UUID.randomUUID());
      return booking;
    });
    when(this.passengerService.createPassengers(any(), any(), any())).thenReturn(List.of());
    when(this.ticketService.generateTicketsForBooking(any(), any())).thenReturn(List.of());
    when(this.fareIntegrationService.calculateFareTotal(any())).thenReturn(fare);
    when(this.seatClient.calculateSeatPrice(any())).thenReturn(seatPrice);
    when(this.ancillaryClient.calculateAncillariesPrice(any())).thenReturn(ApiResponse.success(80.0));
    when(this.ancillaryClient.calculateMealPrice(any())).thenReturn(ApiResponse.success(20.0));
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
