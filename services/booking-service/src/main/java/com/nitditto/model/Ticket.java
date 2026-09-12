package com.nitditto.model;

import java.time.Instant;
import java.util.UUID;

import com.nitditto.entity.BaseEntity;
import com.nitditto.enums.TicketStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tickets", indexes = {
        @Index(name = "idx_ticket_booking_id", columnList = "booking_id"),
        @Index(name = "idx_ticket_passenger_id", columnList = "passenger_id")
})
public class Ticket extends BaseEntity {

    @Column(name = "ticket_number", nullable = false, unique = true)
    private String ticketNumber;

    @Enumerated(EnumType.STRING)
    private TicketStatus ticketStatus;

    private Instant issuedAt;

    @Column(name = "booking_id", columnDefinition = "binary(16)", nullable = false)
    private UUID bookingId;

    @Column(name = "passenger_id", columnDefinition = "binary(16)", nullable = false)
    private UUID passengerId;
}
