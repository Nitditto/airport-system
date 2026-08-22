package com.nitditto.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FareRulesRequest {

    @NotBlank(message = "Rule name is required")
    private String ruleName;

    @NotNull(message = "Fare ID is required")
    private Long fareId;

    private Long airlineId;

    private Boolean isRefundable;

    @PositiveOrZero(message = "Change fee must be positive or zero")
    private Double changeFee;

    @PositiveOrZero(message = "Cancellation fee must be positive or zero")
    private Double cancellationFee;

    @PositiveOrZero(message = "Refund deadline days must be positive or zero")
    private Integer refundDeadlineDays;

    @PositiveOrZero(message = "Change deadline hours must be positive or zero")
    private Integer changeDeadlineHours;

    private Boolean isChangeable;
}
