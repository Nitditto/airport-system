package com.nitditto.controller;

import com.nitditto.enums.FlightStatus;
import com.nitditto.payload.request.FlightRequest;
import com.nitditto.payload.response.FlightResponse;
import com.nitditto.service.FlightService;
import jakarta.validation.Valid;
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
  public ResponseEntity<FlightResponse> createFlight(
      @Valid @RequestBody FlightRequest flightRequest,
      @RequestHeader("X-Airline-Id") Long airlineId
  ) throws Exception {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(flightService.createFlight(airlineId, flightRequest));
  }

  @GetMapping("/{id}")
  public ResponseEntity<FlightResponse> getFlightById(@PathVariable Long id) throws Exception {
    return ResponseEntity.ok(flightService.getFlightById(id));
  }

  @GetMapping("/airline")
  public ResponseEntity<Page<FlightResponse>> getFlightsByAirline(
      @RequestHeader("X-Airline-Id") Long airlineId,
      @RequestParam(required = false) Long departureAirportId,
      @RequestParam(required = false) Long arrivalAirportId,
      Pageable pageable) {
    return ResponseEntity.ok(flightService.getFlightsByAirline(
        airlineId,
        departureAirportId,
        arrivalAirportId,
        pageable
    ));
  }

  @PutMapping("/{id}")
  public ResponseEntity<FlightResponse> updateFlight(
      @PathVariable Long id,
      @RequestBody FlightRequest flightRequest
  ) throws Exception {
    return ResponseEntity.ok(flightService.updateFlight(id, flightRequest));
  }

  @PatchMapping("/{id}/status")
  public ResponseEntity<FlightResponse> changeStatus(
      @PathVariable Long id,
      @RequestParam FlightStatus flightStatus
  ) throws Exception {
    return ResponseEntity.ok(flightService.changeStatus(id, flightStatus));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteFlight(@PathVariable Long id,
      @RequestHeader("X-Airline-Id") Long airlineId) throws Exception {
    flightService.deleteFlight(airlineId, id);
    return ResponseEntity.noContent().build();
  }
}
