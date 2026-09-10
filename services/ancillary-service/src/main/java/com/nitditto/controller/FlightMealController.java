package com.nitditto.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nitditto.payload.request.FlightMealRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.FlightMealResponse;
import com.nitditto.service.FlightMealService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/flight-meals")
@RequiredArgsConstructor
public class FlightMealController {

  private final FlightMealService flightMealService;

  @PostMapping
  public ResponseEntity<ApiResponse<FlightMealResponse>> createFlightMeal(
      @Valid @RequestBody final FlightMealRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(this.flightMealService.createFlightMeal(request)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<FlightMealResponse>> getFlightMealById(
      @PathVariable final UUID id) {
    return ResponseEntity.ok(ApiResponse.success(this.flightMealService.getFlightMealById(id)));
  }

  @GetMapping("/flight/{flightId}")
  public ResponseEntity<ApiResponse<List<FlightMealResponse>>> getByFlightId(
      @PathVariable final UUID flightId) {
    return ResponseEntity.ok(ApiResponse.success(this.flightMealService.getByFlightId(flightId)));
  }

  @PostMapping("/batch-by-ids")
  public ResponseEntity<ApiResponse<List<FlightMealResponse>>> getAllByIds(
      @RequestBody final List<UUID> ids) {
    return ResponseEntity.ok(ApiResponse.success(this.flightMealService.getAllByIds(ids)));
  }

  @PostMapping("/calculate-price")
  public ResponseEntity<ApiResponse<Double>> calculateMealPrice(
      @RequestBody final List<UUID> flightMealIds) {
    return ResponseEntity.ok(
        ApiResponse.success(this.flightMealService.calculateMealPrice(flightMealIds)));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<FlightMealResponse>> updateFlightMeal(
      @PathVariable final UUID id,
      @Valid @RequestBody final FlightMealRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(this.flightMealService.updateFlightMeal(id, request)));
  }

  @PatchMapping("/{id}/availability")
  public ResponseEntity<ApiResponse<FlightMealResponse>> updateAvailability(
      @PathVariable final UUID id,
      @RequestParam final boolean available) {
    return ResponseEntity.ok(
        ApiResponse.success(this.flightMealService.updateFlightMealAvailability(id, available)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteFlightMeal(@PathVariable final UUID id) {
    this.flightMealService.deleteFlightMealById(id);
    return ResponseEntity.noContent().build();
  }
}
