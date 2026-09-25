package com.nitditto.client;

import java.util.List;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "seat-service")
public interface SeatClient {

  @PostMapping("/api/seat-instances/price/price")
  Double calculateSeatPrice(@RequestBody List<UUID> seatInstanceIds);

}
