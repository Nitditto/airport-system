package com.nitditto.controller;

import com.nitditto.payload.request.FlightInstanceRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.FlightInstanceResponse;
import com.nitditto.service.FlightInstanceService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/flight-instances")
public class FlightInstanceController {

  private final FlightInstanceService flightInstanceService;

  @PostMapping
  public ResponseEntity<ApiResponse<FlightInstanceResponse>> createFlightInstance(
      @RequestHeader("X-Airline-Id") final UUID airlineId,
      @Valid @RequestBody final FlightInstanceRequest flightInstanceRequest) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(
            this.flightInstanceService.createFlightInstance(airlineId, flightInstanceRequest)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<FlightInstanceResponse>> getFlightInstance(
      @PathVariable final UUID id) {
    return ResponseEntity.ok(ApiResponse.success(this.flightInstanceService.getFlightInstanceById(id)));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<Page<FlightInstanceResponse>>> getByAirlineId(
      @RequestHeader("X-Airline-Id") final UUID airlineId,
      @RequestParam(required = false) final UUID departureAirportId,
      @RequestParam(required = false) final UUID arrivalAirportId,
      @RequestParam(required = false) final UUID flightId,
      @RequestParam(required = false) final LocalDate onDate, final Pageable pageable
  ) {
    return ResponseEntity.ok(ApiResponse.success(
        this.flightInstanceService.getByAirlineId(airlineId, departureAirportId, arrivalAirportId,
            flightId, onDate, pageable)));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<FlightInstanceResponse>> updateFlightInstance(
      @PathVariable final UUID id,
      @Valid @RequestBody final FlightInstanceRequest flightInstanceRequest) {
    return ResponseEntity.ok(
        ApiResponse.success(this.flightInstanceService.updateFlightInstance(id, flightInstanceRequest)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteFlightInstance(@PathVariable final UUID id) {
    this.flightInstanceService.deleteFlightInstance(id);
    return ResponseEntity.noContent().build();
  }
}
