package com.nitditto.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.nitditto.converter.UuidListConverter;
import com.nitditto.embeddable.ContactInfo;
import com.nitditto.entity.BaseEntity;
import com.nitditto.enums.BookingStatus;
import com.nitditto.enums.CabinClassType;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
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
@Entity
@Table(name = "bookings", indexes = {
        @Index(name = "idx_booking_user_id", columnList = "user_id"),
        @Index(name = "idx_booking_flight_id", columnList = "flight_id"),
        @Index(name = "idx_booking_flight_instance_id", columnList = "flight_instance_id"),
        @Index(name = "idx_booking_airline_id", columnList = "airline_id"),
        @Index(name = "idx_booking_fare_id", columnList = "fare_id"),
        @Index(name = "idx_booking_status", columnList = "booking_status")
})
@NoArgsConstructor
@AllArgsConstructor
public class Booking extends BaseEntity {

    @Column(name = "booking_reference", unique = true, nullable = false)
    private String bookingReference;

    @Column(name = "user_id", columnDefinition = "binary(16)", nullable = false)
    private UUID userId;

    @Column(name = "flight_id", columnDefinition = "binary(16)", nullable = false)
    private UUID flightId;

    @Column(name = "flight_instance_id", columnDefinition = "binary(16)", nullable = false)
    private UUID flightInstanceId;

    // Chưa có bước xác minh chuyến bay (out of scope đợt fix này) nên chưa biết được
    // airlineId lúc tạo booking -> để nullable, điền sau khi tích hợp flight-ops-service.
    @Column(name = "airline_id", columnDefinition = "binary(16)")
    private UUID airlineId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CabinClassType cabinClassType = CabinClassType.ECONOMY;

    @Column(name = "fare_id", columnDefinition = "binary(16)")
    private UUID fareId;

    private boolean flexibleTicket;
    private LocalDateTime ticketTimeLimit;

    @Convert(converter = UuidListConverter.class)
    @Column(name = "seat_instance_ids", length = 2000)
    private List<UUID> seatInstanceIds;

    @Convert(converter = UuidListConverter.class)
    @Column(name = "meal_ids", length = 2000)
    private List<UUID> mealIds;

    @Convert(converter = UuidListConverter.class)
    @Column(name = "ancillary_ids", length = 2000)
    private List<UUID> ancillaryIds;

    @Column(name = "payment_id", columnDefinition = "binary(16)")
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    private BookingStatus bookingStatus;

    private boolean ticketIssued;

    @Embedded
    private ContactInfo contactInfo;
}
