package com.nitditto.controller;

import com.nitditto.payload.request.FlightInstanceRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.FlightInstanceResponse;
import com.nitditto.service.FlightInstanceService;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
  public ResponseEntity<FlightInstanceResponse> createFlightInstance(
      @RequestHeader("X-Airline-Id") Long airlineId,
      @Valid @RequestBody FlightInstanceRequest flightInstanceRequest) throws Exception {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(flightInstanceService.createFlightInstance(airlineId, flightInstanceRequest));
  }

  @GetMapping("/{id}")
  public ResponseEntity<FlightInstanceResponse> getFlightInstance(@PathVariable Long id)
      throws Exception {
    return ResponseEntity.ok(flightInstanceService.getFlightInstanceById(id));
  }

  @GetMapping
  public ResponseEntity<Page<FlightInstanceResponse>> getByAirlineId(
      @RequestHeader("X-Airline-Id") Long airlineId,
      @RequestParam(required = false) Long departureAirportId,
      @RequestParam(required = false) Long arrivalAirportId,
      @RequestParam(required = false) Long flightId,
      @RequestParam(required = false) LocalDate onDate, Pageable pageable
  ) {
    return ResponseEntity.ok(
        flightInstanceService.getByAirlineId(airlineId, departureAirportId, arrivalAirportId,
            flightId, onDate, pageable));
  }

  @PutMapping("/{id}")
  public ResponseEntity<FlightInstanceResponse> updateFlightInstance(@PathVariable Long id,
      @RequestBody FlightInstanceRequest flightInstanceRequest)
      throws Exception {
    return ResponseEntity.ok(flightInstanceService.updateFlightInstance(id, flightInstanceRequest));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse> deleteFlightInstance(@PathVariable Long id) throws Exception {
    flightInstanceService.deleteFlightInstance(id);
    ApiResponse response = new ApiResponse("flight instance deleted");
    return ResponseEntity.ok(response);
  }
}
