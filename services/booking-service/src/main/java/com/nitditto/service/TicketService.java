package com.nitditto.service;

import com.nitditto.payload.response.PassengerResponse;
import com.nitditto.payload.response.TicketResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface TicketService {

    List<TicketResponse> generateTicketsForBooking(UUID bookingId, List<PassengerResponse> passengers);

    Map<UUID, List<TicketResponse>> findByBookingIds(List<UUID> bookingIds);

    void cancelTicketsForBooking(UUID bookingId);

    /** Phát hành vé sau khi thanh toán thành công: vé ở trạng thái BOOKED và cập nhật thời điểm phát hành. */
    void issueTicketsForBooking(UUID bookingId);

    void expireTicketsForBooking(UUID bookingId);

    void deleteTicketsForBooking(UUID bookingId);
}
