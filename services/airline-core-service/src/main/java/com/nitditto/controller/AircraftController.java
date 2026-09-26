package com.nitditto.controller;

import com.nitditto.payload.request.AircraftRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.AircraftResponse;
import com.nitditto.service.AircraftService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/aircrafts")
public class AircraftController {

  private final AircraftService aircraftService;

  @PostMapping
  public ResponseEntity<ApiResponse<AircraftResponse>> createAircraft(
      @RequestBody @Valid final AircraftRequest aircraftRequest,
      @RequestHeader("X-User-Id") final UUID userId) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(this.aircraftService.createAircraft(aircraftRequest, userId)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<AircraftResponse>> getAircraftById(@PathVariable final UUID id) {
    return ResponseEntity.ok(ApiResponse.success(this.aircraftService.getAircraftById(id)));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<AircraftResponse>>> listAllAircrafts(
      @RequestHeader("X-User-Id") final UUID userId) {
    return ResponseEntity.ok(ApiResponse.success(this.aircraftService.listAllAircraftByOwner(userId)));
  }

  @PostMapping("/batch-by-ids")
  public ResponseEntity<ApiResponse<Map<UUID, AircraftResponse>>> getAircraftsByIds(
      @RequestBody final List<UUID> ids) {
    return ResponseEntity.ok(ApiResponse.success(this.aircraftService.getAircraftsByIds(ids)));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<AircraftResponse>> updateAircraft(@PathVariable final UUID id,
      @Valid @RequestBody final AircraftRequest aircraftRequest,
      @RequestHeader("X-User-Id") final UUID userId) {
    return ResponseEntity.ok(ApiResponse.success(this.aircraftService.updateAircraft(id, aircraftRequest, userId)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteAircraft(@PathVariable final UUID id,
      @RequestHeader("X-User-Id") final UUID userId) {
    this.aircraftService.deleteAircraft(id, userId);
    return ResponseEntity.noContent().build();
  }
}
