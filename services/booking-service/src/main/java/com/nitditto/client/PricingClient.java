package com.nitditto.client;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.FareResponse;

@FeignClient(name = "pricing-service")
public interface PricingClient {

  @PostMapping("/api/fares/batch-by-ids")
  ApiResponse<Map<UUID, FareResponse>> getFaresByIds(@RequestBody final List<UUID> fareIds);

  @GetMapping("/api/fares/{id}")
  ApiResponse<FareResponse> getFareById(@PathVariable("id") final UUID fareId);

}
