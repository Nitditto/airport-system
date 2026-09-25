package com.nitditto.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.FareResponse;

@FeignClient(name = "pricing-service")
public interface PricingClient {

  @GetMapping("/api/fares/{id}")
  ApiResponse<FareResponse> getFareById(@PathVariable("id") final UUID fareId);

}
