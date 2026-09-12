package com.nitditto.payload.response;

import com.nitditto.embeddable.ContactInfo;
import com.nitditto.enums.BookingStatus;
import com.nitditto.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {

    private UUID id;

    private String bookingReference;

    private UUID userId;
    private String userName;
    private String userEmail;

    private UUID flightId;
    private String flightNumber;
    private String flightName;
    private String departureAirport;
    private String arrivalAirport;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;

    private BookingStatus status;
    private Instant bookingDate;
    private Instant lastModified;

    private List<PassengerResponse> passengers;
    private List<SeatInstanceResponse> seatInstances;
    private PaymentLinkResponse payment;
    private List<FlightCabinAncillaryResponse> ancillaries;
    private List<FlightMealResponse> meals;
    private List<TicketResponse> tickets;

    // Payment details
    private PaymentStatus paymentStatus;
    private String paymentLink;

    private UUID fareId;
    private String fareName;
    private Double fareBaseFare;
    private Double fareTaxesAndFees;
    private Double fareAirlineFees;

    private Integer totalPassengers;
    private Double totalAmount;

    private String flightDuration;
    private Boolean isUpcoming;
    private Boolean isPast;

    private ContactInfo contactInfo;

}
