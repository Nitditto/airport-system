package com.nitditto.payload.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatMapRequest {

    @NotBlank(message = "Seat map name is required")
    private String name;

    @Positive
    @NotNull(message = "Total rows is required")
    private Integer totalRows;

    @Positive
    @NotNull(message = "Left seats per row is required")
    private Integer leftSeatsPerRow;

    @Positive
    @NotNull(message = "Right seats per row is required")
    private Integer rightSeatsPerRow;

    @NotNull(message = "CabinClassId is required")
    private UUID cabinClassId;
}
