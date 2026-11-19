package com.nitditto.client;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.nitditto.payload.response.FlightInstanceResponse;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.FlightResponse;

@FeignClient("flight-ops-service")
public interface FlightClient {

  @PostMapping("/api/flight-instances/batch-by-ids")
  ApiResponse<Map<UUID, FlightInstanceResponse>> getFlightInstancesByIds(@RequestBody List<UUID> ids);

  @GetMapping("/api/flights/{id}")
  ApiResponse<FlightResponse> getFlightById(@PathVariable("id") UUID id);

  @GetMapping("/api/flight-instances/{id}")
  public ResponseEntity<ApiResponse<FlightInstanceResponse>> getFlightInstance(
      @PathVariable final UUID id);

}
