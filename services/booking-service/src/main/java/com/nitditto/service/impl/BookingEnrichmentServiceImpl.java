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
import feign.FeignException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingEnrichmentServiceImpl implements BookingEnrichmentService {

  /** Khớp @Size(max = 200) ở endpoint batch-by-ids phía service đích. */
  static final int BATCH_SIZE = 200;

  private final FlightClient flightClient;
  private final PricingClient pricingClient;
  private final PaymentClient paymentClient;
  private final SeatClient seatClient;

  @Override
  public Map<UUID, BookingEnrichmentContext> load(final List<Booking> bookings,
      final Map<UUID, List<PassengerResponse>> passengersByBookingId,
      final Map<UUID, List<TicketResponse>> ticketsByBookingId) {
    final Map<UUID, FlightInstanceResponse> flightInstances = this.fetch("flight instances",
        distinct(bookings, Booking::getFlightInstanceId), this.flightClient::getFlightInstancesByIds);
    final Map<UUID, FareResponse> fares = this.fetch("fares",
        distinct(bookings, Booking::getFareId), this.pricingClient::getFaresByIds);
    final Map<UUID, PaymentDTO> payments = this.fetch("payments",
        distinct(bookings, Booking::getId), this.paymentClient::getPaymentsByBookingIds);
    // Ghế là loại duy nhất vượt được 200 id dù trang đã chặn 100 booking (mỗi booking nhiều ghế).
    final List<UUID> seatIds = bookings.stream().flatMap(b -> orEmpty(b.getSeatInstanceIds()).stream())
        .filter(Objects::nonNull).distinct().toList();
    final Map<UUID, SeatInstanceResponse> seats = this.fetch("seat instances", seatIds,
        this.seatClient::getSeatInstancesByIds);

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

  /**
   * Chia lô {@link #BATCH_SIZE} (khớp giới hạn @Size của endpoint batch-by-ids). Service đích lỗi/không với tới được
   * thì trả map rỗng để danh sách booking vẫn hiển thị (thiếu phần làm giàu). Riêng 400 nghĩa là chính request này sai
   * (bug ở đây), nên ném tiếp thay vì âm thầm trả dữ liệu thiếu.
   */
  private <T> Map<UUID, T> fetch(final String what, final List<UUID> ids,
      final Function<List<UUID>, ApiResponse<Map<UUID, T>>> batchCall) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    final Map<UUID, T> result = new HashMap<>(ids.size());
    try {
      for (int from = 0; from < ids.size(); from += BATCH_SIZE) {
        final ApiResponse<Map<UUID, T>> response =
            batchCall.apply(ids.subList(from, Math.min(from + BATCH_SIZE, ids.size())));
        if (response != null && response.getData() != null) {
          result.putAll(response.getData());
        }
      }
      return result;
    } catch (final FeignException.BadRequest ex) {
      throw ex;
    } catch (final RuntimeException ex) {
      log.warn("Could not load {} for booking list: {}", what, ex.getMessage());
      return Map.of();
    }
  }

  private static List<UUID> distinct(final List<Booking> bookings, final Function<Booking, UUID> id) {
    return bookings.stream().map(id).filter(Objects::nonNull).distinct().toList();
  }

  private static <T> List<T> orEmpty(final List<T> list) {
    return list != null ? list : List.of();
  }
}
