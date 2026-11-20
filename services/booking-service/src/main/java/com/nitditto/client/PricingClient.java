package com.nitditto.client;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.FareResponse;
import com.nitditto.payload.response.FareRulesResponse;

@FeignClient(name = "pricing-service")
public interface PricingClient {

  @PostMapping("/api/fares/batch-by-ids")
  ApiResponse<Map<UUID, FareResponse>> getFaresByIds(@RequestBody final List<UUID> fareIds);

  // 404 khi fare chưa có rules.
  @GetMapping("/api/fare-rules/fare/{fareId}")
  ApiResponse<FareRulesResponse> getFareRulesByFareId(@PathVariable("fareId") final UUID fareId);

  @GetMapping("/api/fares/{id}")
  ApiResponse<FareResponse> getFareById(@PathVariable("id") final UUID fareId);

}
