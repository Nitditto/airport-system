package com.nitditto.service.impl;

import com.nitditto.client.FlightClient;
import com.nitditto.client.PaymentClient;
import com.nitditto.client.PricingClient;
import com.nitditto.client.SeatClient;
import com.nitditto.mapper.BookingEnrichmentContext;
import com.nitditto.model.Booking;
import com.nitditto.payload.dto.PaymentDTO;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.FareResponse;
import com.nitditto.payload.response.FlightInstanceResponse;
import com.nitditto.payload.response.PassengerResponse;
import com.nitditto.payload.response.SeatInstanceResponse;
import com.nitditto.payload.response.TicketResponse;
import com.nitditto.service.BookingEnrichmentService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingEnrichmentServiceImpl implements BookingEnrichmentService {

  private final FlightClient flightClient;
  private final PricingClient pricingClient;
  private final PaymentClient paymentClient;
  private final SeatClient seatClient;

  @Override
  public Map<UUID, BookingEnrichmentContext> load(final List<Booking> bookings,
      final Map<UUID, List<PassengerResponse>> passengersByBookingId,
      final Map<UUID, List<TicketResponse>> ticketsByBookingId) {
    final Map<UUID, FlightInstanceResponse> flightInstances = this.fetch("flight instances",
        () -> this.flightClient.getFlightInstancesByIds(distinct(bookings, Booking::getFlightInstanceId)));
    final Map<UUID, FareResponse> fares = this.fetch("fares",
        () -> this.pricingClient.getFaresByIds(distinct(bookings, Booking::getFareId)));
    final Map<UUID, PaymentDTO> payments = this.fetch("payments",
        () -> this.paymentClient.getPaymentsByBookingIds(distinct(bookings, Booking::getId)));
    final List<UUID> seatIds = bookings.stream().flatMap(b -> orEmpty(b.getSeatInstanceIds()).stream())
        .distinct().toList();
    final Map<UUID, SeatInstanceResponse> seats = seatIds.isEmpty() ? Map.of()
        : this.fetch("seat instances", () -> this.seatClient.getSeatInstancesByIds(seatIds));

    final Map<UUID, BookingEnrichmentContext> contexts = new HashMap<>();
    for (final Booking booking : bookings) {
      final List<SeatInstanceResponse> bookingSeats = orEmpty(booking.getSeatInstanceIds()).stream()
          .map(seats::get).filter(Objects::nonNull).toList();
      contexts.put(booking.getId(), new BookingEnrichmentContext(
          payments.get(booking.getId()), fares.get(booking.getFareId()),
          flightInstances.get(booking.getFlightInstanceId()), List.of(), List.of(), bookingSeats,
          passengersByBookingId.getOrDefault(booking.getId(), List.of()),
          ticketsByBookingId.getOrDefault(booking.getId(), List.of())));
    }
    return contexts;
  }

  private <T> Map<UUID, T> fetch(final String what, final Supplier<ApiResponse<Map<UUID, T>>> call) {
    try {
      final Map<UUID, T> data = call.get().getData();
      return data != null ? data : Map.of();
    } catch (final RuntimeException ex) {
      log.warn("Could not load {} for booking list: {}", what, ex.getMessage());
      return Map.of();
    }
  }

  private static List<UUID> distinct(final List<Booking> bookings,
      final java.util.function.Function<Booking, UUID> id) {
    return bookings.stream().map(id).filter(Objects::nonNull).distinct().toList();
  }

  private static <T> List<T> orEmpty(final List<T> list) {
    return list != null ? list : List.of();
  }
}
