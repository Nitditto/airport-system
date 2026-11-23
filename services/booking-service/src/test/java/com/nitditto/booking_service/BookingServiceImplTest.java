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
import com.nitditto.client.PaymentClient;
import com.nitditto.client.SeatClient;
import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.model.Booking;
import com.nitditto.payload.request.BookingRequest;
import com.nitditto.payload.request.PassengerRequest;
import com.nitditto.payload.request.PaymentInitiateRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.BookingResponse;
import com.nitditto.payload.response.PaymentInitiateResponse;
import java.util.Map;
import com.nitditto.repository.BookingRepository;
import com.nitditto.enums.BookingStatus;
import com.nitditto.enums.RefundStatus;
import com.nitditto.mapper.BookingEnrichmentContext;
import com.nitditto.service.BookingCancellationService;
import com.nitditto.service.BookingConfirmationService;
import com.nitditto.service.BookingCreationService;
import com.nitditto.service.BookingEnrichmentService;
import com.nitditto.service.BookingRefundService;
import com.nitditto.service.PassengerService;
import com.nitditto.service.SeatReservationService;
import com.nitditto.service.TicketService;
import com.nitditto.service.impl.BookingServiceImpl;
import com.nitditto.service.integration.BookingTargetResolver;
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
  private BookingTargetResolver bookingTargetResolver;
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
  private com.nitditto.service.PaymentSettlementService paymentSettlementService;
  @Mock
  private BookingCreationService bookingCreationService;
  @Mock
  private BookingRefundService bookingRefundService;
  @Mock
  private BookingCancellationService bookingCancellationService;

  private BookingServiceImpl service;

  private final UUID userId = UUID.randomUUID();
  private final UUID airlineId = UUID.randomUUID();
  private final UUID bookingId = UUID.randomUUID();
  private static final java.time.Instant BOOKING_CREATED_AT = java.time.Instant.parse("2026-11-20T03:00:00Z");
  private final UUID seatA = UUID.randomUUID();
  private final UUID seatB = UUID.randomUUID();
  private final BookingTargetResolver.BookingTarget target = new BookingTargetResolver.BookingTarget(this.airlineId,
      UUID.randomUUID(), new com.nitditto.payload.response.FareResponse());

  @BeforeEach
  void setUp() {
    this.service = new BookingServiceImpl(this.bookingRepository, this.passengerService, this.ticketService,
        this.bookingTargetResolver, this.seatClient, this.ancillaryClient, this.paymentClient, this.fareIntegrationService,
        this.airlineClient, this.seatReservationService, this.bookingEnrichmentService,
        this.bookingConfirmationService, new com.nitditto.service.PaymentWindow(15), this.paymentSettlementService, this.bookingCreationService, this.bookingRefundService,
        this.bookingCancellationService);
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
  void createBooking_makesThePaymentLinkExpireWhenTheBookingDoes_notAnIndependentTimer() {
    givenFlightAndPrices(1000.0, 150.0);
    givenBookingIsCreated();
    givenPaymentIsInitiated();

    this.service.createBooking(request(this.seatA, this.seatB), this.userId);

    final ArgumentCaptor<PaymentInitiateRequest> captor = ArgumentCaptor.forClass(PaymentInitiateRequest.class);
    verify(this.paymentClient).initiatePayment(captor.capture());
    // booking.payment-window-minutes = 15 (cùng một PaymentWindow quyết định cả hạn booking lẫn hạn link)
    assertThat(captor.getValue().getExpiresAt()).isEqualTo(BOOKING_CREATED_AT.plusSeconds(15 * 60));
  }

  @Test
  void createBooking_readsRemoteData_thenHoldsSeats_thenCommitsBooking_thenInitiatesPayment() {
    givenFlightAndPrices(500.0, 0.0);
    givenBookingIsCreated();
    givenPaymentIsInitiated();

    this.service.createBooking(request(this.seatA), this.userId);

    final InOrder order = inOrder(this.bookingTargetResolver, this.seatClient, this.seatReservationService,
        this.bookingCreationService, this.paymentClient);
    order.verify(this.bookingTargetResolver).resolve(any());
    order.verify(this.seatClient).calculateSeatPrice(any());
    order.verify(this.seatReservationService).hold(this.userId, List.of(this.seatA), this.target);
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
        .when(this.seatReservationService).hold(any(), any(), any());

    assertThatThrownBy(() -> this.service.createBooking(request(this.seatA), this.userId))
        .isInstanceOf(BusinessException.class);

    verify(this.bookingCreationService, never()).createPending(any(), any(), any(), any());
    verify(this.paymentClient, never()).initiatePayment(any());
  }

  @Test
  void createBooking_doesNotHoldSeats_whenFlightIsUnknown() {
    when(this.bookingTargetResolver.resolve(any()))
        .thenThrow(new ResourceNotFoundException("FlightInstance", "id", UUID.randomUUID()));

    assertThatThrownBy(() -> this.service.createBooking(request(this.seatA), this.userId))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(this.seatReservationService, never()).hold(any(), any(), any());
  }

  @Test
  void createBooking_doesNotPriceOrHoldSeats_whenFareDoesNotMatchTheFlight() {
    when(this.bookingTargetResolver.resolve(any()))
        .thenThrow(new BusinessException("Fare does not belong to the requested flight"));

    assertThatThrownBy(() -> this.service.createBooking(request(this.seatA), this.userId))
        .isInstanceOf(BusinessException.class);

    verify(this.seatClient, never()).calculateSeatPrice(any());
    verify(this.seatReservationService, never()).hold(any(), any(), any());
    verify(this.bookingCreationService, never()).createPending(any(), any(), any(), any());
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

  // ---- cancelBooking ----

  @Test
  void cancel_confirmedBooking_requestsTheRefundBeforeCancelling_andReportsIt() {
    final Booking booking = this.bookingIn(BookingStatus.CONFIRMED);
    this.givenBookingExists(booking);
    when(this.bookingRefundService.refundForCancellation(booking, this.userId)).thenReturn(
        new BookingRefundService.Outcome(1_050_000.0, RefundStatus.SUCCESS, "Refunded after a cancellation fee of 100000"));
    this.givenCancelledResponseCanBeBuilt(booking);

    final BookingResponse response = this.service.cancelBooking(booking.getId(), this.userId);

    final InOrder order = inOrder(this.bookingRefundService, this.bookingCancellationService,
        this.seatReservationService);
    order.verify(this.bookingRefundService).refundForCancellation(booking, this.userId);
    order.verify(this.bookingCancellationService).markCancelled(booking.getId());
    order.verify(this.seatReservationService).freeQuietly(booking.getSeatInstanceIds());
    assertThat(response.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    assertThat(response.getRefundAmount()).isEqualTo(1_050_000.0);
    assertThat(response.getRefundStatus()).isEqualTo(RefundStatus.SUCCESS);
    assertThat(response.getRefundNote()).contains("cancellation fee");
  }

  @Test
  void cancel_whenTheRefundCannotBeRecorded_leavesTheBookingUntouched_soTheUserCanRetry() {
    final Booking booking = this.bookingIn(BookingStatus.CONFIRMED);
    this.givenBookingExists(booking);
    when(this.bookingRefundService.refundForCancellation(booking, this.userId))
        .thenThrow(new IllegalStateException("payment-service unavailable"));

    assertThatThrownBy(() -> this.service.cancelBooking(booking.getId(), this.userId))
        .isInstanceOf(IllegalStateException.class);

    verify(this.bookingCancellationService, never()).markCancelled(any());
    verify(this.seatReservationService, never()).freeQuietly(any());
  }

  @Test
  void cancel_pendingBooking_neverTouchesRefunds_andOnlyReleasesTheHold() {
    final Booking booking = this.bookingIn(BookingStatus.PENDING);
    this.givenBookingExists(booking);
    this.givenCancelledResponseCanBeBuilt(booking);

    final BookingResponse response = this.service.cancelBooking(booking.getId(), this.userId);

    verify(this.bookingRefundService, never()).refundForCancellation(any(), any());
    verify(this.bookingCancellationService).markCancelled(booking.getId());
    verify(this.seatReservationService).releaseQuietly(this.userId, booking.getSeatInstanceIds());
    assertThat(response.getRefundAmount()).isNull();
    assertThat(response.getRefundStatus()).isNull();
  }

  @Test
  void cancel_byAnotherUser_isRejected_withoutAnySideEffect() {
    final Booking booking = this.bookingIn(BookingStatus.CONFIRMED);
    this.givenBookingExists(booking);

    assertThatThrownBy(() -> this.service.cancelBooking(booking.getId(), UUID.randomUUID()))
        .isInstanceOf(BusinessException.class).hasMessageContaining("not allowed");

    verify(this.bookingRefundService, never()).refundForCancellation(any(), any());
    verify(this.bookingCancellationService, never()).markCancelled(any());
  }

  @Test
  void cancel_alreadyCancelledBooking_doesNotRefundOrCancelAgain() {
    final Booking booking = this.bookingIn(BookingStatus.CANCELLED);
    this.givenBookingExists(booking);
    this.givenResponseCanBeBuilt(booking);

    this.service.cancelBooking(booking.getId(), this.userId);

    verify(this.bookingRefundService, never()).refundForCancellation(any(), any());
    verify(this.bookingCancellationService, never()).markCancelled(any());
    verify(this.seatReservationService, never()).freeQuietly(any());
  }

  @Test
  void createBooking_rejectsTwoPassengersOnTheSameSeat() {
    assertThatThrownBy(() -> this.service.createBooking(request(this.seatA, this.seatA), this.userId))
        .isInstanceOf(BusinessException.class).hasMessageContaining("different seat");

    verify(this.seatReservationService, never()).hold(any(), any(), any());
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.EnumSource(value = BookingStatus.class, names = {"PENDING", "CONFIRMED"})
  void deleteBooking_rejectsActiveBooking_andDeletesNothing(final BookingStatus status) {
    final Booking booking = this.bookingIn(status);
    this.givenBookingExists(booking);

    assertThatThrownBy(() -> this.service.deleteBooking(booking.getId(), this.userId))
        .isInstanceOf(BusinessException.class).hasMessageContaining("cancel the booking first");

    verify(this.ticketService, never()).deleteTicketsForBooking(any());
    verify(this.passengerService, never()).deletePassengersForBooking(any());
    verify(this.bookingRepository, never()).deleteById(any());
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.EnumSource(value = BookingStatus.class, names = {"CANCELLED", "EXPIRED"})
  void deleteBooking_removesFinishedBooking_childrenFirst(final BookingStatus status) {
    final Booking booking = this.bookingIn(status);
    this.givenBookingExists(booking);

    this.service.deleteBooking(booking.getId(), this.userId);

    final InOrder order = inOrder(this.ticketService, this.passengerService, this.bookingRepository);
    order.verify(this.ticketService).deleteTicketsForBooking(booking.getId());
    order.verify(this.passengerService).deletePassengersForBooking(booking.getId());
    order.verify(this.bookingRepository).deleteById(booking.getId());
  }

  @Test
  void deleteBooking_byAnotherUser_isRejectedBeforeStatusCheck() {
    final Booking booking = this.bookingIn(BookingStatus.CANCELLED);
    this.givenBookingExists(booking);

    assertThatThrownBy(() -> this.service.deleteBooking(booking.getId(), UUID.randomUUID()))
        .isInstanceOf(BusinessException.class).hasMessageContaining("not allowed to delete");

    verify(this.bookingRepository, never()).deleteById(any());
  }

  @Test
  void getAllBookingsByAirline_ownerWithoutAirline_isForbidden() {
    when(this.airlineClient.getAirlineByOwner(this.userId)).thenThrow(new feign.FeignException.NotFound("none",
        feign.Request.create(feign.Request.HttpMethod.GET, "/api/airlines/owner", Map.of(), null,
            java.nio.charset.StandardCharsets.UTF_8, null), null, null));

    assertThatThrownBy(() -> this.service.getAllBookingsByAirline(this.userId, null, null, null,
        org.springframework.data.domain.Pageable.ofSize(20)))
        .isInstanceOf(com.nitditto.exception.ForbiddenException.class);

    verify(this.bookingRepository, never()).findAll(any(org.springframework.data.jpa.domain.Specification.class),
        any(org.springframework.data.domain.Pageable.class));
  }

  @Test
  void getAllBookingsByAirline_nullAirlineData_isForbidden() {
    when(this.airlineClient.getAirlineByOwner(this.userId)).thenReturn(ApiResponse.success(null));

    assertThatThrownBy(() -> this.service.getAllBookingsByAirline(this.userId, null, null, null,
        org.springframework.data.domain.Pageable.ofSize(20)))
        .isInstanceOf(com.nitditto.exception.ForbiddenException.class);
  }

  private Booking bookingIn(final BookingStatus status) {
    final Booking booking = new Booking();
    booking.setId(UUID.randomUUID());
    booking.setBookingReference("BKCANCEL01");
    booking.setUserId(this.userId);
    booking.setBookingStatus(status);
    booking.setSeatInstanceIds(List.of(this.seatA));
    return booking;
  }

  private void givenBookingExists(final Booking booking) {
    when(this.bookingRepository.findById(booking.getId())).thenReturn(java.util.Optional.of(booking));
  }

  @Test
  void listing_doesNotLockOrCallPaymentService_forUnpaidBookingStillInsideItsWindow() {
    final Booking recent = this.bookingIn(BookingStatus.PENDING);
    recent.setCreatedAt(java.time.Instant.now());
    when(this.bookingRepository.findByUserId(any(), any()))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(recent)));
    this.givenResponseCanBeBuilt(recent);

    this.service.getBookingsByUser(this.userId, org.springframework.data.domain.Pageable.ofSize(20));

    verify(this.bookingConfirmationService, never()).expireIfStale(any());
    verify(this.paymentSettlementService, never()).settle(any());
  }

  @Test
  void listing_stillExpiresUnpaidBookingPastItsWindow() {
    final Booking stale = this.bookingIn(BookingStatus.PENDING);
    stale.setCreatedAt(BOOKING_CREATED_AT);
    when(this.bookingRepository.findByUserId(any(), any()))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(stale)));
    when(this.bookingConfirmationService.expireIfStale(stale.getId())).thenReturn(BookingStatus.EXPIRED);
    this.givenResponseCanBeBuilt(stale);

    this.service.getBookingsByUser(this.userId, org.springframework.data.domain.Pageable.ofSize(20));

    verify(this.bookingConfirmationService).expireIfStale(stale.getId());
  }

  private void givenCancelledResponseCanBeBuilt(final Booking booking) {
    final Booking cancelled = this.bookingIn(BookingStatus.CANCELLED);
    cancelled.setId(booking.getId());
    when(this.bookingCancellationService.markCancelled(booking.getId())).thenReturn(cancelled);
    this.givenResponseCanBeBuilt(cancelled);
  }

  private void givenResponseCanBeBuilt(final Booking booking) {
    when(this.passengerService.findByBookingIds(any())).thenReturn(Map.of());
    when(this.ticketService.findByBookingIds(any())).thenReturn(Map.of());
    when(this.bookingEnrichmentService.load(any(), any(), any())).thenReturn(Map.of(booking.getId(),
        new BookingEnrichmentContext(null, null, null, List.of(), List.of(), List.of(), List.of(), List.of())));
  }

  private void givenFlightAndPrices(final double fare, final double seatPrice) {
    when(this.bookingTargetResolver.resolve(any())).thenReturn(this.target);
    when(this.fareIntegrationService.farePerPassenger(this.target.fare())).thenReturn(fare);
    when(this.seatClient.calculateSeatPrice(any())).thenReturn(seatPrice);
    when(this.ancillaryClient.calculateAncillariesPrice(any())).thenReturn(ApiResponse.success(80.0));
    when(this.ancillaryClient.calculateMealPrice(any())).thenReturn(ApiResponse.success(20.0));
  }

  private void givenBookingIsCreated() {
    when(this.bookingCreationService.createPending(any(), any(), any(), any())).thenAnswer(invocation -> {
      final Booking booking = new Booking();
      booking.setId(this.bookingId);
      booking.setBookingReference("BKTEST001");
      booking.setCreatedAt(BOOKING_CREATED_AT);
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
