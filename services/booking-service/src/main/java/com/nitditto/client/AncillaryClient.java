package com.nitditto.client;

import java.util.List;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.nitditto.payload.response.ApiResponse;

@FeignClient(name = "ancillary-service")
public interface AncillaryClient {

  @PostMapping("/api/flight-cabin-ancillaries/price/total")
  ApiResponse<Double> calculateAncillariesPrice(@RequestBody final List<UUID> flightCabinAncillaryIds);

  @PostMapping("/api/flight-meals/calculate-price")
  ApiResponse<Double> calculateMealPrice(@RequestBody final List<UUID> flightMealIds);
}
