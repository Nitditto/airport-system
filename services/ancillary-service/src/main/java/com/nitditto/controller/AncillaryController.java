package com.nitditto.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.nitditto.payload.request.AncillaryRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.AncillaryResponse;
import com.nitditto.service.AncillaryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ancillaries")
@RequiredArgsConstructor
public class AncillaryController {

  private final AncillaryService ancillaryService;

  @PostMapping
  public ResponseEntity<ApiResponse<AncillaryResponse>> createAncillary(
      @RequestHeader("X-User-Id") final UUID userId,
      @Valid @RequestBody final AncillaryRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(this.ancillaryService.createAncillary(userId, request)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<AncillaryResponse>> getById(@PathVariable final UUID id) {
    return ResponseEntity.ok(ApiResponse.success(this.ancillaryService.getById(id)));
  }

  @GetMapping("/airline")
  public ResponseEntity<ApiResponse<List<AncillaryResponse>>> getByAirlineId(
      @RequestHeader("X-User-Id") final UUID userId) {
    return ResponseEntity.ok(ApiResponse.success(this.ancillaryService.getByAirlineId(userId)));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<AncillaryResponse>> updateAncillary(
      @PathVariable final UUID id,
      @Valid @RequestBody final AncillaryRequest request) {
    return ResponseEntity.ok(ApiResponse.success(this.ancillaryService.updateAncillary(id, request)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteAncillary(@PathVariable final UUID id) {
    this.ancillaryService.deleteAncillary(id);
    return ResponseEntity.noContent().build();
  }
}
