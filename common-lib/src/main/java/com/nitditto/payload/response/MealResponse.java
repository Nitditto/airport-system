package com.nitditto.payload.response;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MealResponse {
    private UUID id;
    private String code;
    private String name;
    private String mealType;
    private String dietaryRestriction;
    private String ingredients;
    private String imageUrl;
    private Boolean available;
    private Boolean requiresAdvanceBooking;
    private Integer advanceBookingHours;
    private Integer displayOrder;
    private UUID airlineId;
    private Instant createdAt;
    private Instant updatedAt;
}
