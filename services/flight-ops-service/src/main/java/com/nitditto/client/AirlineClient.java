package com.nitditto.client;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.nitditto.payload.response.AircraftResponse;
import com.nitditto.payload.response.AirlineResponse;
import com.nitditto.payload.response.ApiResponse;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "airline-core-service")
public interface AirlineClient {

    @GetMapping("/api/airlines/{id}")
    ApiResponse<AirlineResponse> getAirlineById(@PathVariable("id") UUID id);

    @GetMapping("/api/aircrafts/{id}")
    ApiResponse<AircraftResponse> getAircraftById(@PathVariable("id") UUID id);

  @PostMapping("/api/airlines/batch-by-ids")
  ApiResponse<Map<UUID, AirlineResponse>> getAirlinesByIds(@RequestBody List<UUID> ids);

  @PostMapping("/api/aircrafts/batch-by-ids")
  ApiResponse<Map<UUID, AircraftResponse>> getAircraftsByIds(@RequestBody List<UUID> ids);

    @GetMapping("/api/airlines/admin")
    ApiResponse<AirlineResponse> getAirlineByOwner(
            @RequestHeader("X-User-Id") final UUID userId);
}
