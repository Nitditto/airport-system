package com.nitditto.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.util.UUID;

import com.nitditto.entity.BaseEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "seat_maps",
        indexes = {
                @Index(name = "idx_seat_map_cabin", columnList = "cabin_class_id"),
                @Index(name = "idx_seat_map_airline", columnList = "airline_id")
})
public class SeatMap extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private int totalRows;

    @Column(nullable = false)
    private int rightSeatsPerRow;

    @Column(nullable = false)
    private int leftSeatsPerRow;

    @Column(name = "airline_id", columnDefinition = "binary(16)", nullable = false)
    private UUID airlineId;

    @Column(name = "cabin_class_id", columnDefinition = "binary(16)", nullable = false, unique = true)
    private UUID cabinClassId;

}
