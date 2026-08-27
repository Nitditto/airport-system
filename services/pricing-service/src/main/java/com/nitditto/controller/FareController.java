package com.nitditto.controller;

import com.nitditto.payload.request.FareRequest;
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
    public ResponseEntity<FareResponse> createFare(@Valid @RequestBody FareRequest fareRequest) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                fareService.createFare(fareRequest)
        );
    }

    @GetMapping
    public ResponseEntity<?> getFares() throws Exception {
        return ResponseEntity.status(HttpStatus.OK).body(fareService.getFares());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FareResponse> getFareById(@PathVariable("id") UUID fareId) throws Exception {
        return ResponseEntity.ok(fareService.getFareById(fareId));
    }

    @GetMapping("/flight/{flightId}/cabin-class/{cabinClassId}")
    public ResponseEntity<List<FareResponse>> getFaresByFlightAndCabinClass(@PathVariable UUID flightId, @PathVariable UUID cabinClassId) throws Exception {
        return ResponseEntity.ok(fareService.getFaresByFlightIdAndCabinClassId(flightId, cabinClassId));
    }

    @PostMapping("/batch-by-ids")
    public ResponseEntity<Map<UUID, FareResponse>> getFaresByIds(@RequestBody List<UUID> fareIds) throws Exception {
        return ResponseEntity.ok(fareService.getFareByIds(fareIds));
    }

    @PostMapping("/search")
    public ResponseEntity<Map<UUID, FareResponse>> getLowestFarePerFlight(@RequestBody List<UUID> flightIds, @RequestParam UUID cabinClassId) {
        Map<UUID, FareResponse> result = fareService.getLowestFarePerFlight(flightIds, cabinClassId);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FareResponse> updateFare(@PathVariable("id") UUID fareId, @RequestBody FareRequest fareRequest) throws Exception {
        return ResponseEntity.ok(fareService.updateFare(fareId, fareRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFare(@PathVariable("id") UUID fareId) throws Exception {
        fareService.deleteFareById(fareId);
        return ResponseEntity.noContent().build();
    }

}
