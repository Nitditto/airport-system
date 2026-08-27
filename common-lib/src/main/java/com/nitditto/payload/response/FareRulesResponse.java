package com.nitditto.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FareRulesResponse {
  private UUID id;
  private String ruleName;
  private UUID fareId;
  private UUID airlineId;
  private Boolean isRefundable;
  private Double changeFee;
  private Double cancellationFee;
  private Integer refundDeadlineDays;
  private Integer changeDeadlineHours;
  private Boolean isChangeable;
  private Instant createdAt;
  private Instant updatedAt;
}
