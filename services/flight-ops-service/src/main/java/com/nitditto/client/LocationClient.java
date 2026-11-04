package com.nitditto.client;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.nitditto.payload.response.AirportResponse;
import com.nitditto.payload.response.ApiResponse;

@FeignClient(name = "location-service")
public interface LocationClient {

  @GetMapping("/api/airports/{id}")
  ApiResponse<AirportResponse> getAirportById(@PathVariable("id") UUID id);

  @PostMapping("/api/airports/batch-by-ids")
  ApiResponse<Map<UUID, AirportResponse>> getAirportsByIds(@RequestBody List<UUID> ids);
}
