package com.nitditto.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightMealResponse {

    private UUID id;
    private UUID flightId;
    private UUID mealId;
    private MealResponse mealResponse;
    private Boolean available;
    private Double price;
    private Integer displayOrder;
}
