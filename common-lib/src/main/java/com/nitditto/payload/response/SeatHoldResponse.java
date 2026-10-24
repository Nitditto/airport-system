package com.nitditto.payload.response;

import java.time.Instant;
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
public class SeatHoldResponse {

  private List<UUID> seatInstanceIds;
  // Mốc hết hạn giữ chỗ theo đồng hồ server (FE hiệu chỉnh timer theo header Date của response).
  private Instant expiresAt;
}
