package com.nitditto.client;

import com.nitditto.payload.request.SeatHoldRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.SeatHoldResponse;
import com.nitditto.payload.response.SeatInstanceResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "seat-service")
public interface SeatClient {

  @PostMapping("/api/seat-instances/price/price")
  Double calculateSeatPrice(@RequestBody List<UUID> seatInstanceIds);

  @PostMapping("/api/seat-instances/batch-by-ids")
  ApiResponse<Map<UUID, SeatInstanceResponse>> getSeatInstancesByIds(@RequestBody List<UUID> seatInstanceIds);

  @PostMapping("/api/seat-instances/hold")
  ApiResponse<SeatHoldResponse> hold(@RequestHeader("X-User-Id") UUID userId, @RequestBody SeatHoldRequest request);

  @PostMapping("/api/seat-instances/release")
  ApiResponse<Void> release(@RequestHeader("X-User-Id") UUID userId, @RequestBody SeatHoldRequest request);

  @PostMapping("/api/seat-instances/confirm-booked")
  ApiResponse<Void> confirmBooked(@RequestHeader("X-User-Id") UUID userId, @RequestBody SeatHoldRequest request);

  @PostMapping("/api/seat-instances/free")
  ApiResponse<Void> free(@RequestBody SeatHoldRequest request);
}
