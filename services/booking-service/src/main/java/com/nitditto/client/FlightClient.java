package com.nitditto.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.FlightResponse;

@FeignClient("flight-ops-service")
public interface FlightClient {

  @GetMapping("/api/flights/{id}")
  public ResponseEntity<ApiResponse<FlightResponse>> getFlightById(@PathVariable("id") final UUID id);

}
