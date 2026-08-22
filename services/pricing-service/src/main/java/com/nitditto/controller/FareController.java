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
    public ResponseEntity<FareResponse> getFareById(@PathVariable("id") Long fareId) throws Exception {
        return ResponseEntity.ok(fareService.getFareById(fareId));
    }

    @GetMapping("/flight/{flightId}/cabin-class/{cabinClassId}")
    public ResponseEntity<List<FareResponse>> getFaresByFlightAndCabinClass(@PathVariable Long flightId, @PathVariable Long cabinClassId) throws Exception {
        return ResponseEntity.ok(fareService.getFaresByFlightIdAndCabinClassId(flightId, cabinClassId));
    }

    @PostMapping("/batch-by-ids")
    public ResponseEntity<Map<Long, FareResponse>> getFaresByIds(@RequestBody List<Long> fareIds) throws Exception {
        return ResponseEntity.ok(fareService.getFareByIds(fareIds));
    }

    @PostMapping("/search")
    public ResponseEntity<Map<Long, FareResponse>> getLowestFarePerFlight(@RequestBody List<Long> flightIds, @RequestParam Long cabinClassId) {
        Map<Long, FareResponse> result = fareService.getLowestFarePerFlight(flightIds, cabinClassId);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FareResponse> updateFare(@PathVariable("id") Long fareId, @RequestBody FareRequest fareRequest) throws Exception {
        return ResponseEntity.ok(fareService.updateFare(fareId, fareRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFare(@PathVariable("id") Long fareId) throws Exception {
        fareService.deleteFareById(fareId);
        return ResponseEntity.noContent().build();
    }

}
