package com.nitditto.mapper;

import com.nitditto.enums.BookingStatus;
import com.nitditto.model.Booking;
import com.nitditto.payload.dto.PaymentDTO;
import com.nitditto.payload.request.BookingRequest;
import com.nitditto.payload.response.AirportResponse;
import com.nitditto.payload.response.BookingResponse;
import com.nitditto.payload.response.FareResponse;
import com.nitditto.payload.response.FlightInstanceResponse;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
        final FareResponse fare = context.fare();
        final PaymentDTO payment = context.payment();
        final Boolean upcoming = isUpcoming(flightInstance);

        return BookingResponse.builder()
                .id(booking.getId())
                .bookingReference(booking.getBookingReference())
                .userId(booking.getUserId())

                .flightId(booking.getFlightId())
                .flightNumber(flightInstance != null ? flightInstance.getFlightNumber() : null)
                .flightName(flightInstance != null ? flightInstance.getAirlineName() : null)
                .departureTime(flightInstance != null ? flightInstance.getDepartureDateTime() : null)
                .arrivalTime(flightInstance != null ? flightInstance.getArrivalDateTime() : null)
                .flightDuration(flightInstance != null ? flightInstance.getFormatedDuration() : null)
                .departureAirport(iata(flightInstance != null ? flightInstance.getDepartureAirport() : null))
                .arrivalAirport(iata(flightInstance != null ? flightInstance.getArrivalAirport() : null))
                .isUpcoming(upcoming)
                .isPast(upcoming != null ? !upcoming : null)

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
                // Số tiền đã thanh toán/được yêu cầu thanh toán là nguồn chính xác nhất cho tổng đơn.
                .totalAmount(payment != null ? payment.getAmount() : null)

                .contactInfo(booking.getContactInfo())
                .build();
    }

    private static String iata(final AirportResponse airport) {
        return airport != null ? airport.getIataCode() : null;
    }

    // Giờ bay là giờ địa phương của sân bay đi nên so với "bây giờ" ở múi giờ đó; không biết giờ bay thì trả null.
    private static Boolean isUpcoming(final FlightInstanceResponse flightInstance) {
        if (flightInstance == null || flightInstance.getDepartureDateTime() == null) {
            return null;
        }
        final AirportResponse departure = flightInstance.getDepartureAirport();
        final ZoneId zone = departure != null && departure.getTimeZone() != null
                ? departure.getTimeZone() : ZoneId.systemDefault();
        return flightInstance.getDepartureDateTime().isAfter(LocalDateTime.now(zone));
    }
}
