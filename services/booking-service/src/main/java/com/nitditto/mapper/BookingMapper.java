package com.nitditto.mapper;

import com.nitditto.enums.BookingStatus;
import com.nitditto.model.Booking;
import com.nitditto.payload.dto.PaymentDTO;
import com.nitditto.payload.request.BookingRequest;
import com.nitditto.payload.response.BookingResponse;
import com.nitditto.payload.response.FareResponse;
import com.nitditto.payload.response.FlightInstanceResponse;
import com.nitditto.payload.response.FlightResponse;

import java.util.List;
import java.util.UUID;

public class BookingMapper {

    private BookingMapper() {
    }

    public static Booking toEntity(final BookingRequest request, final UUID userId, final String bookingReference,
            final List<UUID> seatInstanceIds) {
        return Booking.builder()
                .bookingReference(bookingReference)
                .userId(userId)
                .flightId(request.getFlightId())
                .flightInstanceId(request.getFlightInstanceId())
                .fareId(request.getFareId())
                .contactInfo(request.getContactInfo())
                .cabinClassType(request.getCabinClassType())
                .ancillaryIds(request.getAncillaryIds())
                .mealIds(request.getMealIds())
                .seatInstanceIds(seatInstanceIds)
                .bookingStatus(BookingStatus.PENDING)
                .build();
    }

    public static BookingResponse toResponse(final Booking booking, final BookingEnrichmentContext context) {
        final FlightInstanceResponse flightInstance = context.flightInstance();
        final FlightResponse flight = context.flight();
        final FareResponse fare = context.fare();
        final PaymentDTO payment = context.payment();

        return BookingResponse.builder()
                .id(booking.getId())
                .bookingReference(booking.getBookingReference())
                .userId(booking.getUserId())

                .flightId(booking.getFlightId())
                .flightNumber(flightInstance != null ? flightInstance.getFlightNumber() : null)
                // todo: nguồn "flightName" thật (tên hãng/route) sẽ lấy từ flight/airline response
                // một khi tích hợp flight-ops-service; trước đây bị gán trùng flightNumber, để trống
                // thay vì lặp lại một giá trị sai.
                .departureTime(flightInstance != null ? flightInstance.getDepartureDateTime() : null)
                .arrivalTime(flightInstance != null ? flightInstance.getArrivalDateTime() : null)
                .flightDuration(flightInstance != null ? flightInstance.getFormatedDuration() : null)

                .departureAirport(flight != null && flight.getDepartureAirport() != null
                        ? flight.getDepartureAirport().getIataCode() : null)
                .arrivalAirport(flight != null && flight.getArrivalAirport() != null
                        ? flight.getArrivalAirport().getIataCode() : null)

                .status(booking.getBookingStatus())
                .bookingDate(booking.getCreatedAt())
                .lastModified(booking.getUpdatedAt())
                .fareId(booking.getFareId())

                .passengers(context.passengers())
                .tickets(context.tickets())
                .totalPassengers(context.passengers() != null ? context.passengers().size() : 0)

                .ancillaries(context.ancillaries())
                .meals(context.meals())
                .seatInstances(context.seatInstances())

                .paymentStatus(payment != null ? payment.getPaymentStatus() : null)

                .fareName(fare != null ? fare.getName() : null)
                .fareBaseFare(fare != null ? fare.getBaseFare() : null)
                .fareTaxesAndFees(fare != null ? fare.getTaxesAndFees() : null)
                .fareAirlineFees(fare != null ? fare.getAirlineFees() : null)
                .totalAmount(fare != null ? fare.getTotalPrice() : null)

                .contactInfo(booking.getContactInfo())
                .build();
    }
}
