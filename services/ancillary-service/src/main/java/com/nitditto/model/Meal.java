package com.nitditto.model;

import java.util.UUID;

import com.nitditto.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "meals", indexes = {
        @Index(name = "idx_meals_airline_id", columnList = "airline_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Meal extends BaseEntity {

    @Column(nullable = false)
    private String code;

    private String name;
    private String mealType;
    private String dietaryRestriction;
    private String ingredients;
    private String imageUrl;

    @Builder.Default
    private Boolean available = true;

    @Builder.Default
    private Boolean requiresAdvanceBooking = false;

    private Integer advanceBookingHours;

    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "airline_id", columnDefinition = "binary(16)", nullable = false)
    private UUID airlineId;
}
