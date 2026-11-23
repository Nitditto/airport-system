package com.nitditto.booking_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nitditto.client.FlightClient;
import com.nitditto.client.PaymentClient;
import com.nitditto.client.PricingClient;
import com.nitditto.client.SeatClient;
import com.nitditto.mapper.BookingEnrichmentContext;
import com.nitditto.model.Booking;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.SeatInstanceResponse;
import com.nitditto.service.impl.BookingEnrichmentServiceImpl;
import feign.FeignException;
import feign.Request;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingEnrichmentServiceImplTest {

  @Mock
  private FlightClient flightClient;
  @Mock
  private PricingClient pricingClient;
  @Mock
  private PaymentClient paymentClient;
  @Mock
  private SeatClient seatClient;

  private BookingEnrichmentServiceImpl service;

  @BeforeEach
  void setUp() {
    this.service = new BookingEnrichmentServiceImpl(this.flightClient, this.pricingClient, this.paymentClient,
        this.seatClient);
    when(this.flightClient.getFlightInstancesByIds(anyList())).thenReturn(ApiResponse.success(Map.of()));
    when(this.pricingClient.getFaresByIds(anyList())).thenReturn(ApiResponse.success(Map.of()));
    when(this.paymentClient.getPaymentsByBookingIds(anyList())).thenReturn(ApiResponse.success(Map.of()));
  }

  @Test
  void moreThan200Seats_areFetchedInChunks_andEverySeatIsAttached() {
    // 100 booking x 3 ghế = 300 ghế -> 2 lô (200 + 100), không lô nào vượt giới hạn của seat-service.
    final List<Booking> bookings = IntStream.range(0, 100).mapToObj(i -> booking(3)).toList();
    when(this.seatClient.getSeatInstancesByIds(anyList())).thenAnswer(inv -> {
      final List<UUID> ids = inv.getArgument(0);
      final Map<UUID, SeatInstanceResponse> data = new HashMap<>();
      ids.forEach(id -> data.put(id, SeatInstanceResponse.builder().id(id).build()));
      return ApiResponse.success(data);
    });

    final Map<UUID, BookingEnrichmentContext> contexts = this.service.load(bookings, Map.of(), Map.of());

    final ArgumentCaptor<List<UUID>> chunks = ArgumentCaptor.captor();
    verify(this.seatClient, times(2)).getSeatInstancesByIds(chunks.capture());
    assertThat(chunks.getAllValues()).extracting(List::size).containsExactly(200, 100);
    assertThat(contexts.values()).allSatisfy(context -> assertThat(context.seatInstances()).hasSize(3));
  }

  @Test
  void unreachableService_degradesToEmptyData() {
    final Booking booking = booking(1);
    when(this.seatClient.getSeatInstancesByIds(anyList()))
        .thenThrow(new FeignException.ServiceUnavailable("down", request(), null, null));

    final Map<UUID, BookingEnrichmentContext> contexts = this.service.load(List.of(booking), Map.of(), Map.of());

    assertThat(contexts.get(booking.getId()).seatInstances()).isEmpty();
  }

  @Test
  void badRequest_isNotSwallowed() {
    when(this.seatClient.getSeatInstancesByIds(anyList()))
        .thenThrow(new FeignException.BadRequest("too many ids", request(), null, null));

    assertThatThrownBy(() -> this.service.load(List.of(booking(1)), Map.of(), Map.of()))
        .isInstanceOf(FeignException.BadRequest.class);
  }

  private static Booking booking(final int seats) {
    final Booking booking = new Booking();
    booking.setId(UUID.randomUUID());
    booking.setFlightInstanceId(UUID.randomUUID());
    booking.setFareId(UUID.randomUUID());
    final List<UUID> seatIds = new ArrayList<>();
    for (int i = 0; i < seats; i++) {
      seatIds.add(UUID.randomUUID());
    }
    booking.setSeatInstanceIds(seatIds);
    return booking;
  }

  private static Request request() {
    return Request.create(Request.HttpMethod.POST, "/api/seat-instances/batch-by-ids", Map.of(), null,
        StandardCharsets.UTF_8, null);
  }
}
