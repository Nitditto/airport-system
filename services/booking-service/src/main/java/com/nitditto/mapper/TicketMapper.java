package com.nitditto.mapper;

import com.nitditto.model.Ticket;
import com.nitditto.payload.response.TicketResponse;

public class TicketMapper {

    private TicketMapper() {
    }

    // Chỉ map field của chính Ticket. Các field phẳng "denormalized" (bookingReference,
    // passengerFirstName/LastName/Email) do BookingServiceImpl điền sau, vì cần dữ liệu
    // của Booking/Passenger mà mapper không được phép tự đi lấy.
    public static TicketResponse toResponse(final Ticket ticket) {
        if (ticket == null) {
            return null;
        }
        return TicketResponse.builder()
                .id(ticket.getId())
                .ticketNumber(ticket.getTicketNumber())
                .status(ticket.getTicketStatus())
                .issuedAt(ticket.getIssuedAt())
                .bookingId(ticket.getBookingId())
                .passengerId(ticket.getPassengerId())
                .build();
    }
}
