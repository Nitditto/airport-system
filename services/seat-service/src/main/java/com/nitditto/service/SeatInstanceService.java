package com.nitditto.service;

import com.nitditto.payload.response.SeatInstanceResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface SeatInstanceService {

  Double calculateSeatPrice(List<UUID> seatInstanceIds);

  /** Batch cho booking-service (hiển thị số ghế trên vé): 1 query seat_instances + 1 query seats. */
  Map<UUID, SeatInstanceResponse> getSeatInstancesByIds(List<UUID> seatInstanceIds);
}
