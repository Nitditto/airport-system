package com.nitditto.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FareRulesResponse {
  private Long id;
  private String ruleName;
  private Long fareId;
  private Long airlineId;
  private Boolean isRefundable;
  private Double changeFee;
  private Double cancellationFee;
  private Integer refundDeadlineDays;
  private Integer changeDeadlineHours;
  private Boolean isChangeable;
  private Instant createdAt;
  private Instant updatedAt;
}
