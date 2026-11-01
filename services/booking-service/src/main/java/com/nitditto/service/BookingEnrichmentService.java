package com.nitditto.service;

import com.nitditto.mapper.BookingEnrichmentContext;
import com.nitditto.model.Booking;
import com.nitditto.payload.response.PassengerResponse;
import com.nitditto.payload.response.TicketResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface BookingEnrichmentService {

  /**
   * Gom dữ liệu chuyến bay, giá vé, thanh toán và ghế của các booking bằng đúng 4 lời gọi batch (không phụ thuộc số
   * booking). Service nào lỗi thì phần dữ liệu đó để trống (kèm log) thay vì làm hỏng cả danh sách vé.
   */
  Map<UUID, BookingEnrichmentContext> load(List<Booking> bookings,
      Map<UUID, List<PassengerResponse>> passengersByBookingId, Map<UUID, List<TicketResponse>> ticketsByBookingId);
}
