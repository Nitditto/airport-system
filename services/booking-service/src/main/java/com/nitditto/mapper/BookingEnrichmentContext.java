package com.nitditto.mapper;

import com.nitditto.payload.dto.PaymentDTO;
import com.nitditto.payload.response.FareResponse;
import com.nitditto.payload.response.FlightCabinAncillaryResponse;
import com.nitditto.payload.response.FlightInstanceResponse;
import com.nitditto.payload.response.FlightMealResponse;
import com.nitditto.payload.response.FlightResponse;
import com.nitditto.payload.response.PassengerResponse;
import com.nitditto.payload.response.SeatInstanceResponse;
import com.nitditto.payload.response.TicketResponse;

import java.util.List;

// Gói toàn bộ dữ liệu đã batch-load từ các service khác để BookingMapper.toResponse chỉ nhận
// đúng 2 tham số (Booking + context) thay vì liệt kê 8 tham số rời rạc.
public record BookingEnrichmentContext(
        PaymentDTO payment,
        FareResponse fare,
        FlightResponse flight,
        FlightInstanceResponse flightInstance,
        List<FlightCabinAncillaryResponse> ancillaries,
        List<FlightMealResponse> meals,
        List<SeatInstanceResponse> seatInstances,
        List<PassengerResponse> passengers,
        List<TicketResponse> tickets) {
}
