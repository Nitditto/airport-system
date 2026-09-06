package com.nitditto.controller;

import java.util.List;
import java.util.UUID;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nitditto.payload.request.InsuranceCoverageRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.InsuranceCoverageResponse;
import com.nitditto.service.InsuranceCoverageService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/insurance-coverages")
@RequiredArgsConstructor
public class InsuranceCoverageController {

  private final InsuranceCoverageService insuranceCoverageService;

  @PostMapping
  public ResponseEntity<ApiResponse<InsuranceCoverageResponse>> createCoverage(
      @Valid @RequestBody final InsuranceCoverageRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(this.insuranceCoverageService.createCoverage(request)));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<Page<InsuranceCoverageResponse>>> getAllCoverages(
      final Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(this.insuranceCoverageService.getAllCoverages(pageable)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<InsuranceCoverageResponse>> getCoverage(
      @PathVariable final UUID id) {
    return ResponseEntity.ok(ApiResponse.success(this.insuranceCoverageService.getCoverage(id)));
  }

  @GetMapping("/ancillary/{ancillaryId}")
  public ResponseEntity<ApiResponse<List<InsuranceCoverageResponse>>> getCoverageByAncillaryId(
      @PathVariable final UUID ancillaryId) {
    return ResponseEntity.ok(
        ApiResponse.success(this.insuranceCoverageService.getCoverageByAncillaryId(ancillaryId)));
  }

  @GetMapping("/ancillary/{ancillaryId}/active")
  public ResponseEntity<ApiResponse<List<InsuranceCoverageResponse>>> getActiveCoverageByAncillaryId(
      @PathVariable final UUID ancillaryId) {
    return ResponseEntity.ok(ApiResponse.success(
        this.insuranceCoverageService.getActiveCoverageByAncillaryId(ancillaryId)));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<InsuranceCoverageResponse>> updateCoverage(
      @PathVariable final UUID id,
      @Valid @RequestBody final InsuranceCoverageRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(this.insuranceCoverageService.updateCoverage(id, request)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteCoverage(@PathVariable final UUID id) {
    this.insuranceCoverageService.deleteCoverage(id);
    return ResponseEntity.noContent().build();
  }
}
