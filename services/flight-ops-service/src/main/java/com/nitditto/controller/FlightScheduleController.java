package com.nitditto.controller;

import com.nitditto.payload.request.FlightScheduleRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.FlightScheduleResponse;
import com.nitditto.service.FlightScheduleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/flight-schedules")
public class FlightScheduleController {

  private final FlightScheduleService flightScheduleService;

  @PostMapping
  public ResponseEntity<ApiResponse<FlightScheduleResponse>> createFlightSchedule(
      @RequestHeader("X-Airline-Id") final UUID airlineId,
      @Valid @RequestBody final FlightScheduleRequest flightScheduleRequest) {
    // todo: watch for airline id
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(
            this.flightScheduleService.createFlightSchedule(airlineId, flightScheduleRequest)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<FlightScheduleResponse>> getFlightScheduleById(
      @PathVariable final UUID id) {
    return ResponseEntity.ok(ApiResponse.success(this.flightScheduleService.getFlightScheduleById(id)));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<FlightScheduleResponse>>> getFlightSchedules(
      @RequestHeader("X-Airline-Id") final UUID airlineId) {
    return ResponseEntity.ok(
        ApiResponse.success(this.flightScheduleService.getFlightScheduleByAirline(airlineId)));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<FlightScheduleResponse>> updateFlightSchedule(
      @PathVariable final UUID id, @Valid @RequestBody final FlightScheduleRequest flightScheduleRequest
  ) {
    return ResponseEntity.ok(
        ApiResponse.success(this.flightScheduleService.updateFlightSchedule(id, flightScheduleRequest)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteFlightSchedule(@PathVariable final UUID id) {
    this.flightScheduleService.deleteFlightScheduleById(id);
    return ResponseEntity.noContent().build();
  }
}
