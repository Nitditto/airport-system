package com.nitditto.client;

import com.nitditto.payload.response.AirlineResponse;
import com.nitditto.payload.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@FeignClient(name = "airline-core-service")
public interface AirlineClient {

    @GetMapping("/api/airlines/admin")
    ApiResponse<AirlineResponse> getAirlineByOwner(
            @RequestHeader("X-User-Id") final UUID userId);
}
