package com.nitditto.model;

import com.nitditto.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "flight_meal", indexes = {
        @Index(name = "idx_flight_meal_meal_id", columnList = "meal_id"),
        @Index(name = "idx_flight_meal_flight_id", columnList = "flight_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_flight_meal_flight_meal", columnNames = {"flight_id", "meal_id"})
})
@Entity
public class FlightMeal extends BaseEntity {

    @Column(name = "flight_id", columnDefinition = "binary(16)", nullable = false)
    private UUID flightId;

    @Column(name = "meal_id", columnDefinition = "binary(16)", nullable = false)
    private UUID mealId;

    @Builder.Default
    private Boolean available = true;

    private Double price;

    @Builder.Default
    private Integer displayOrder = 0;
}
