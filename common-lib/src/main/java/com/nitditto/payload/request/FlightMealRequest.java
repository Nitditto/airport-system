package com.nitditto.payload.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlightMealRequest {

    @NotNull(message = "Flight ID is required")
    private UUID flightId;

    @NotNull(message = "Meal ID is required")
    private UUID mealId;

    private Boolean available;

    @DecimalMin(value = "0.0", message = "Price cannot be negative")
    private Double price;

    @Min(value = 0, message = "Display order cannot be negative")
    private Integer displayOrder;
}
