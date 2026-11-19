package com.nitditto.controller;

import com.nitditto.payload.request.AirportRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.AirportResponse;
import com.nitditto.service.AirportService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/airports")
public class AirportController {

  private final AirportService airportService;

  @PostMapping
  public ResponseEntity<ApiResponse<AirportResponse>> createAirport(
      @Valid @RequestBody final AirportRequest airportRequest) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(this.airportService.createAirport(airportRequest)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<AirportResponse>> getAirportById(@PathVariable final UUID id) {
    return ResponseEntity.ok(ApiResponse.success(this.airportService.getAirportById(id)));
  }

  /** POST nhưng là thao tác đọc (gateway coi batch-by-ids là đọc) — dùng cho service khác tra nhiều sân bay. */
  @PostMapping("/batch-by-ids")
  public ResponseEntity<ApiResponse<Map<UUID, AirportResponse>>> getAirportsByIds(
      @RequestBody final List<UUID> ids) {
    return ResponseEntity.ok(ApiResponse.success(this.airportService.getAirportsByIds(ids)));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<AirportResponse>>> getAllAirports() {
    return ResponseEntity.ok(ApiResponse.success(this.airportService.getAllAirports()));
  }

  @GetMapping("/city/{cityId}")
  public ResponseEntity<ApiResponse<List<AirportResponse>>> getAirportsByCityId(
      @PathVariable final UUID cityId) {
    return ResponseEntity.ok(ApiResponse.success(this.airportService.getAirportsByCityId(cityId)));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<AirportResponse>> updateAirport(@PathVariable final UUID id,
      @Valid @RequestBody final AirportRequest airportRequest) {
    return ResponseEntity.ok(ApiResponse.success(this.airportService.updateAirport(id, airportRequest)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteAirport(@PathVariable final UUID id) {
    this.airportService.deleteAirportById(id);
    return ResponseEntity.noContent().build();
  }
}
