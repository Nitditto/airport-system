package com.nitditto.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

import com.nitditto.enums.TicketStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {
    private UUID id;
    private String ticketNumber;
    private TicketStatus status;
    private Instant issuedAt;

    private UUID bookingId;
    private String bookingReference;

    private UUID passengerId;
    private String passengerFirstName;
    private String passengerLastName;
    private String passengerEmail;

    private UUID paymentId;
    private Double paymentAmount;
}
