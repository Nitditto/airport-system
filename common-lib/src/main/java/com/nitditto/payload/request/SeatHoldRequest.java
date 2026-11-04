package com.nitditto.payload.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatHoldRequest {

  @NotEmpty(message = "At least one seat instance is required")
  @Size(max = 9, message = "Cannot hold more than 9 seats at once")
  private List<UUID> seatInstanceIds;
}
