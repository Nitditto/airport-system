package com.nitditto.client;

import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.FareResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "pricing-service")
public interface PricingClient {

  /** Fare rẻ nhất của từng chuyến (key là flightId) cho một hạng ghế; chuyến chưa có fare thì vắng. */
  @PostMapping("/api/fares/search")
  ApiResponse<Map<UUID, FareResponse>> getLowestFarePerFlight(
      @RequestBody List<UUID> flightIds, @RequestParam("cabinClassId") UUID cabinClassId);
}
