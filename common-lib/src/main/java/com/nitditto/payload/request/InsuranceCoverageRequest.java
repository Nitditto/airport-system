package com.nitditto.payload.request;

import com.nitditto.enums.CoverageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceCoverageRequest {

    @NotNull(message = "Ancillary ID is required")
    private UUID ancillaryId;

    @NotNull(message = "Coverage type is required")
    private CoverageType  coverageType;

    @NotBlank(message = "Coverage name is required")
    @Size(max = 200, message = "Coverage name cannot be longer than 200 characters")
    private String name;

    @Size(max = 1000, message = "Description cannot be longer than 1000 characters")
    private String description;

    @NotNull(message = "Coverage amount is required")
    @PositiveOrZero(message = "Coverage amount must be zero or positive")
    private Double coverageAmount;

    private Boolean isFlat;

    @Size(max = 500, message = "Claim condition cannot be longer than 500 characters")
    private String claimCondition;

    @Size(max = 100, message = "Emergency contact cannot be longer than 100 characters")
    private String emergencyContact;

    private Integer displayOrder;

    private Boolean active;
}
