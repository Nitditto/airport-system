package com.nitditto.controller;

import com.nitditto.payload.request.FareRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.FareResponse;
import com.nitditto.service.FareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/fares")
@RequiredArgsConstructor
public class FareController {

    private final FareService fareService;

    @PostMapping
    public ResponseEntity<ApiResponse<FareResponse>> createFare(@Valid @RequestBody final FareRequest fareRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(this.fareService.createFare(fareRequest)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FareResponse>>> getFares() {
        return ResponseEntity.ok(ApiResponse.success(this.fareService.getFares()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FareResponse>> getFareById(@PathVariable("id") final UUID fareId) {
        return ResponseEntity.ok(ApiResponse.success(this.fareService.getFareById(fareId)));
    }

    @GetMapping("/flight/{flightId}/cabin-class/{cabinClassId}")
    public ResponseEntity<ApiResponse<List<FareResponse>>> getFaresByFlightAndCabinClass(
            @PathVariable final UUID flightId, @PathVariable final UUID cabinClassId) {
        return ResponseEntity.ok(
                ApiResponse.success(this.fareService.getFaresByFlightIdAndCabinClassId(flightId, cabinClassId)));
    }

    @PostMapping("/batch-by-ids")
    public ResponseEntity<ApiResponse<Map<UUID, FareResponse>>> getFaresByIds(
            @RequestBody final List<UUID> fareIds) {
        return ResponseEntity.ok(ApiResponse.success(this.fareService.getFareByIds(fareIds)));
    }

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<Map<UUID, FareResponse>>> getLowestFarePerFlight(
            @RequestBody final List<UUID> flightIds, @RequestParam final UUID cabinClassId) {
        return ResponseEntity.ok(
                ApiResponse.success(this.fareService.getLowestFarePerFlight(flightIds, cabinClassId)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FareResponse>> updateFare(@PathVariable("id") final UUID fareId,
            @Valid @RequestBody final FareRequest fareRequest) {
        return ResponseEntity.ok(ApiResponse.success(this.fareService.updateFare(fareId, fareRequest)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFare(@PathVariable("id") final UUID fareId) {
        this.fareService.deleteFareById(fareId);
        return ResponseEntity.noContent().build();
    }

}
