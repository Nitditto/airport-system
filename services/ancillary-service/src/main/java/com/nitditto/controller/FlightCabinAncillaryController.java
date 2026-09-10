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

import com.nitditto.enums.AncillaryType;
import com.nitditto.payload.request.FlightCabinAncillaryRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.FlightCabinAncillaryResponse;
import com.nitditto.service.FlightCabinAncillaryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/flight-cabin-ancillaries")
@RequiredArgsConstructor
public class FlightCabinAncillaryController {

  private final FlightCabinAncillaryService flightCabinAncillaryService;

  @PostMapping
  public ResponseEntity<ApiResponse<FlightCabinAncillaryResponse>> create(
      @Valid @RequestBody final FlightCabinAncillaryRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(this.flightCabinAncillaryService.createFlightCabinAncillary(request)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<FlightCabinAncillaryResponse>> getById(@PathVariable final UUID id) {
    return ResponseEntity.ok(ApiResponse.success(this.flightCabinAncillaryService.getById(id)));
  }

  @GetMapping("/flight/{flightId}/cabin-class/{cabinClassId}")
  public ResponseEntity<ApiResponse<List<FlightCabinAncillaryResponse>>> getByFlightAndCabinClass(
      @PathVariable final UUID flightId,
      @PathVariable final UUID cabinClassId) {
    return ResponseEntity.ok(ApiResponse.success(
        this.flightCabinAncillaryService.getByFlightAndCabinClass(flightId, cabinClassId)));
  }

  @GetMapping("/flight/{flightId}/cabin-class/{cabinClassId}/type/{ancillaryType}")
  public ResponseEntity<ApiResponse<FlightCabinAncillaryResponse>> getByFlightCabinAndType(
      @PathVariable final UUID flightId,
      @PathVariable final UUID cabinClassId,
      @PathVariable final AncillaryType ancillaryType) {
    return ResponseEntity.ok(ApiResponse.success(
        this.flightCabinAncillaryService.getByFlightIdAndCabinClassIdAndType(
            flightId, cabinClassId, ancillaryType)));
  }

  @GetMapping("/flight/{flightId}/cabin-class/{cabinClassId}/type/{ancillaryType}/all")
  public ResponseEntity<ApiResponse<List<FlightCabinAncillaryResponse>>> getAllByFlightCabinAndType(
      @PathVariable final UUID flightId,
      @PathVariable final UUID cabinClassId,
      @PathVariable final AncillaryType ancillaryType) {
    return ResponseEntity.ok(ApiResponse.success(
        this.flightCabinAncillaryService.getAllByFlightIdAndCabinClassIdAndType(
            flightId, cabinClassId, ancillaryType)));
  }

  @PostMapping("/batch-by-ids")
  public ResponseEntity<ApiResponse<List<FlightCabinAncillaryResponse>>> getAllByIds(
      @RequestBody final List<UUID> ids) {
    return ResponseEntity.ok(ApiResponse.success(this.flightCabinAncillaryService.getAllByIds(ids)));
  }

  @PostMapping("/calculate-price")
  public ResponseEntity<ApiResponse<Double>> calculatePrice(
      @RequestBody final List<UUID> flightCabinAncillaryIds) {
    return ResponseEntity.ok(ApiResponse.success(
        this.flightCabinAncillaryService.calculateAncillaryPrice(flightCabinAncillaryIds)));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<FlightCabinAncillaryResponse>> update(
      @PathVariable final UUID id,
      @Valid @RequestBody final FlightCabinAncillaryRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(this.flightCabinAncillaryService.update(id, request)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable final UUID id) {
    this.flightCabinAncillaryService.deleteById(id);
    return ResponseEntity.noContent().build();
  }
}
