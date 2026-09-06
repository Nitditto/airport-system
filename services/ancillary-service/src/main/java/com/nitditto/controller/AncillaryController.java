package com.nitditto.controller;

import java.util.List;
import java.util.UUID;

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

  @PostMapping("/airline/{airlineId}")
  public ResponseEntity<ApiResponse<AncillaryResponse>> createAncillary(
      @PathVariable final UUID airlineId,
      @Valid @RequestBody final AncillaryRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(this.ancillaryService.createAncillary(airlineId, request)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<AncillaryResponse>> getById(@PathVariable final UUID id) {
    return ResponseEntity.ok(ApiResponse.success(this.ancillaryService.getById(id)));
  }

  @GetMapping("/airline/{airlineId}")
  public ResponseEntity<ApiResponse<List<AncillaryResponse>>> getByAirlineId(
      @PathVariable final UUID airlineId) {
    return ResponseEntity.ok(ApiResponse.success(this.ancillaryService.getByAirlineId(airlineId)));
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
