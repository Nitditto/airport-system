package com.nitditto.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

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

    @GetMapping("/api/airlines/admin")
    ApiResponse<AirlineResponse> getAirlineByOwner(
            @RequestHeader("X-User-Id") final UUID userId);
}
