package com.nitditto.booking_service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nitditto.client.AirlineClient;
import com.nitditto.exception.ForbiddenException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.model.Booking;
import com.nitditto.payload.response.AirlineResponse;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.repository.BookingRepository;
import com.nitditto.service.BookingEnrichmentService;
import com.nitditto.service.PassengerService;
import com.nitditto.service.TicketService;
import com.nitditto.service.impl.BookingServiceImpl;
import feign.FeignException;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Đường TỪ CHỐI của getBookingById ở tầng service: người không có quyền không được chạm tới bước dựng response
 * (không gọi passenger/ticket/payment...). Dùng @InjectMocks để không phụ thuộc thứ tự tham số constructor.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceReadAccessTest {

  @Mock
  private BookingRepository bookingRepository;
  @Mock
  private AirlineClient airlineClient;
  @Mock
  private PassengerService passengerService;
  @Mock
  private TicketService ticketService;
  @Mock
  private BookingEnrichmentService bookingEnrichmentService;
  @InjectMocks
  private BookingServiceImpl service;

  private final UUID bookingId = UUID.randomUUID();
  private final UUID customerId = UUID.randomUUID();
  private final UUID bookingAirlineId = UUID.randomUUID();
  private final UUID stranger = UUID.randomUUID();

  private void givenABookingOwnedBySomeoneElse() {
    final Booking booking = new Booking();
    booking.setId(this.bookingId);
    booking.setUserId(this.customerId);
    booking.setAirlineId(this.bookingAirlineId);
    when(this.bookingRepository.findById(this.bookingId)).thenReturn(Optional.of(booking));
  }

  @Test
  void strangerWhoOwnsNoAirline_isForbidden_andNothingIsAssembled() {
    this.givenABookingOwnedBySomeoneElse();
    when(this.airlineClient.getAirlineByOwner(this.stranger)).thenThrow(feignError(404));

    assertThatThrownBy(() -> this.service.getBookingById(this.bookingId, this.stranger, false))
        .isInstanceOf(ForbiddenException.class);

    verifyNoInteractions(this.passengerService, this.ticketService, this.bookingEnrichmentService);
  }

  @Test
  void ownerOfAnotherAirline_isForbidden_andNothingIsAssembled() {
    this.givenABookingOwnedBySomeoneElse();
    final AirlineResponse other = AirlineResponse.builder().id(UUID.randomUUID()).build();
    when(this.airlineClient.getAirlineByOwner(this.stranger)).thenReturn(ApiResponse.success(other));

    assertThatThrownBy(() -> this.service.getBookingById(this.bookingId, this.stranger, false))
        .isInstanceOf(ForbiddenException.class);

    verifyNoInteractions(this.passengerService, this.ticketService, this.bookingEnrichmentService);
  }

  @Test
  void airlineLookupOutage_failsClosed_insteadOfGuessingTheAnswer() {
    this.givenABookingOwnedBySomeoneElse();
    when(this.airlineClient.getAirlineByOwner(this.stranger)).thenThrow(feignError(503));

    assertThatThrownBy(() -> this.service.getBookingById(this.bookingId, this.stranger, false))
        .isInstanceOf(FeignException.class)
        .isNotInstanceOf(FeignException.NotFound.class);

    verifyNoInteractions(this.passengerService, this.ticketService, this.bookingEnrichmentService);
  }

  @Test
  void admin_isNotForbidden_andNoAirlineLookupIsMade() {
    this.givenABookingOwnedBySomeoneElse();

    // admin qua được bước kiểm tra quyền; việc dựng response (không được mock đầy đủ ở đây) mới là nơi có thể lỗi khác.
    assertThatThrownBy(() -> this.service.getBookingById(this.bookingId, this.stranger, true))
        .isNotInstanceOf(ForbiddenException.class);

    verifyNoInteractions(this.airlineClient);
  }

  @Test
  void unknownBooking_is404_notForbidden() {
    final UUID unknown = UUID.randomUUID();
    when(this.bookingRepository.findById(unknown)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> this.service.getBookingById(unknown, this.stranger, false))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  private static FeignException feignError(final int status) {
    final Request request = Request.create(Request.HttpMethod.GET, "/api/airlines/admin",
        Collections.emptyMap(), null, StandardCharsets.UTF_8, null);
    return FeignException.errorStatus("getAirlineByOwner", Response.builder().status(status).reason("x")
        .request(request).headers(Collections.emptyMap()).build());
  }
}
