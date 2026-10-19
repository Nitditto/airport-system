package com.nitditto.controller;

import com.nitditto.enums.FlightStatus;
import com.nitditto.payload.request.FlightRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.FlightResponse;
import com.nitditto.service.FlightService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flights")
@RequiredArgsConstructor
public class FlightController {

  private final FlightService flightService;

  @PostMapping
  public ResponseEntity<ApiResponse<FlightResponse>> createFlight(
      @Valid @RequestBody final FlightRequest flightRequest,
      @RequestHeader("X-User-Id") final UUID userId
  ) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(this.flightService.createFlight(userId, flightRequest)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<FlightResponse>> getFlightById(@PathVariable final UUID id) {
    return ResponseEntity.ok(ApiResponse.success(this.flightService.getFlightById(id)));
  }

  @GetMapping("/airline")
  public ResponseEntity<ApiResponse<Page<FlightResponse>>> getFlightsByAirline(
      @RequestHeader("X-User-Id") final UUID userId,
      @RequestParam(required = false) final UUID departureAirportId,
      @RequestParam(required = false) final UUID arrivalAirportId,
      final Pageable pageable) {
    return ResponseEntity.ok(ApiResponse.success(this.flightService.getFlightsByAirline(
            userId,
        departureAirportId,
        arrivalAirportId,
        pageable
    )));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<FlightResponse>> updateFlight(
      @PathVariable final UUID id,
      @Valid @RequestBody final FlightRequest flightRequest
  ) {
    return ResponseEntity.ok(ApiResponse.success(this.flightService.updateFlight(id, flightRequest)));
  }

  @PatchMapping("/{id}/status")
  public ResponseEntity<ApiResponse<FlightResponse>> changeStatus(
      @PathVariable final UUID id,
      @RequestParam final FlightStatus flightStatus
  ) {
    return ResponseEntity.ok(ApiResponse.success(this.flightService.changeStatus(id, flightStatus)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteFlight(@PathVariable final UUID id,
      @RequestHeader("X-User-Id") final UUID userId) {
    this.flightService.deleteFlight(userId, id);
    return ResponseEntity.noContent().build();
  }
}
