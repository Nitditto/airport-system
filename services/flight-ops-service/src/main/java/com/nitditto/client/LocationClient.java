package com.nitditto.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.nitditto.payload.response.AirportResponse;
import com.nitditto.payload.response.ApiResponse;

@FeignClient(name = "location-service")
public interface LocationClient {

  @GetMapping("/api/airports/{id}")
  ApiResponse<AirportResponse> getAirportById(@PathVariable("id") UUID id);
}
