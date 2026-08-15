package com.nitditto.controller;

import com.nitditto.payload.request.AirportRequest;
import com.nitditto.payload.response.AirportResponse;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.repository.AirportRepository;
import com.nitditto.service.AirportService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/airports")
public class AirportController {

  private final AirportService airportService;

  @PostMapping
  public ResponseEntity<AirportResponse> createAirport(
      @Valid @RequestBody AirportRequest airportRequest) throws Exception {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(airportService.createAirport(airportRequest));
  }

  @GetMapping("/{id}")
  public ResponseEntity<AirportResponse> getAirportById(@PathVariable Long id) throws Exception {
    return ResponseEntity.ok(airportService.getAirportById(id));
  }

  @GetMapping
  public ResponseEntity<List<AirportResponse>> getAllAirports() {
    return ResponseEntity.ok(airportService.getAllAirports());
  }

  @GetMapping("/city/{cityId}")
  public ResponseEntity<List<AirportResponse>> getAirportsByCityId(@PathVariable Long cityId) {
    return ResponseEntity.ok(airportService.getAirportsByCityId(cityId));
  }

  @PutMapping("/{id}")
  public ResponseEntity<AirportResponse> updateAirport(@PathVariable Long id,
      @Valid @RequestBody AirportRequest airportRequest) throws Exception {
    return ResponseEntity.ok(airportService.updateAirport(id, airportRequest));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse> deleteAirport(@PathVariable Long id) throws Exception {
    return ResponseEntity.ok(new ApiResponse("Airport deleted successfully"));
  }
}
