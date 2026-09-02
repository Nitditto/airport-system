package com.nitditto.model;

import java.util.UUID;

import com.nitditto.entity.BaseEntity;
import com.nitditto.enums.SeatAvailabilityStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "seat_instances", indexes = {
        @Index(name = "idx_seat_instance_cabin", columnList = "flight_instance_cabin_id"),
        @Index(name = "idx_seat_instance_seat", columnList = "seat_id"),
        @Index(name = "idx_seat_instance_flight_instance", columnList = "flight_instance_id")
}, uniqueConstraints = {
        // 1 ghế vật lý chỉ có đúng 1 seat instance trên 1 flight instance cabin
        @UniqueConstraint(name = "uk_seat_instance_cabin_seat", columnNames = {"flight_instance_cabin_id", "seat_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatInstance extends BaseEntity {

    @Column(name = "flight_id", columnDefinition = "binary(16)", nullable = false)
    private UUID flightId;

    // Denormalize từ FlightInstanceCabin.flightInstanceId (giống Seat.cabinClassId denormalize
    // từ SeatMap.cabinClassId) -> tránh phải nhảy qua bảng cha khi query theo flightInstanceId.
    @Column(name = "flight_instance_id", columnDefinition = "binary(16)", nullable = false)
    private UUID flightInstanceId;

    @Column(name = "flight_instance_cabin_id", columnDefinition = "binary(16)", nullable = false)
    private UUID flightInstanceCabinId;

    @Column(name = "seat_id", columnDefinition = "binary(16)", nullable = false)
    private UUID seatId;

    @Column(name = "seat_map_id", columnDefinition = "binary(16)", nullable = false)
    private UUID seatMapId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatAvailabilityStatus status = SeatAvailabilityStatus.AVAILABLE;

    @Builder.Default
    @Column(nullable = false)
    private boolean isBooked = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean isAvailable = true;

    private Double fare;
    private Double premiumSuperCharge;

    private UUID flightScheduleId;

    @Version
    @Column(name = "version")
    private Long version;

}
