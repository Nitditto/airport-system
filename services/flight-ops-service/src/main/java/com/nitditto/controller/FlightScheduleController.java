package com.nitditto.controller;

import com.nitditto.payload.request.FlightScheduleRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.FlightScheduleResponse;
import com.nitditto.service.FlightScheduleService;
import jakarta.validation.Valid;
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
  public ResponseEntity<FlightScheduleResponse> createFlightSchedule(
      @RequestHeader("X-Airline-Id") Long airlineId,
      @Valid @RequestBody FlightScheduleRequest flightScheduleRequest) throws Exception {
    // todo: watch for airline id
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(flightScheduleService.createFlightSchedule(airlineId, flightScheduleRequest));
  }

  @GetMapping("/{id}")
  public ResponseEntity<FlightScheduleResponse> getFlightScheduleById(@PathVariable Long id)
      throws Exception {
    return ResponseEntity.ok(flightScheduleService.getFlightScheduleById(id));
  }

  @GetMapping
  public ResponseEntity<?> getFlightSchedules(@RequestHeader("X-Airline-Id") Long airlineId) {
    return ResponseEntity.ok(flightScheduleService.getFlightScheduleByAirline(airlineId));
  }

  @PutMapping("/{id}")
  public ResponseEntity<FlightScheduleResponse> updateFlightSchedule(
      @PathVariable Long id, @RequestBody FlightScheduleRequest flightScheduleRequest
  ) throws Exception {
    return ResponseEntity.ok(flightScheduleService.updateFlightSchedule(id, flightScheduleRequest));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<String>> deleteFlightSchedule(@PathVariable Long id) throws Exception {
    flightScheduleService.deleteFlightScheduleById(id);
    return ResponseEntity.ok(ApiResponse.success("Schedule removed successfully"));
  }
}
