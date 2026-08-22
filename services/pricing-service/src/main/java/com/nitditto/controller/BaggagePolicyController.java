package com.nitditto.controller;

import com.nitditto.payload.request.BaggagePolicyRequest;
import com.nitditto.payload.response.BaggagePolicyResponse;
import com.nitditto.service.BaggagePolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/baggage-policies")
@RequiredArgsConstructor
public class BaggagePolicyController {
    private final BaggagePolicyService baggagePolicyService;

    @PostMapping
    public ResponseEntity<BaggagePolicyResponse> createBaggagePolicy(@Valid @RequestBody BaggagePolicyRequest baggagePolicyRequest) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED).body(baggagePolicyService.createBaggagePolicy(baggagePolicyRequest));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaggagePolicyResponse> getBaggagePolicyById(@PathVariable Long id) throws Exception {
        return ResponseEntity.ok(baggagePolicyService.getBaggagePolicyById(id));
    }

    @GetMapping("/fare/{fareId}")
    public ResponseEntity<BaggagePolicyResponse> getBaggagePolicyByFareId(@PathVariable Long fareId) throws Exception {
        return ResponseEntity.ok(baggagePolicyService.getBaggagePolicyByFareId(fareId));
    }

    @GetMapping("/airline/{airlineId}")
    public ResponseEntity<List<BaggagePolicyResponse>> getBaggagePoliciesByAirlineId(@PathVariable Long airlineId) throws Exception {
        return ResponseEntity.ok(baggagePolicyService.getBaggagePoliciesByAirlineId(airlineId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BaggagePolicyResponse> updateBaggagePolicy(@PathVariable Long id, @Valid @RequestBody BaggagePolicyRequest baggagePolicyRequest) throws Exception {
        return ResponseEntity.ok(baggagePolicyService.updateBaggagePolicy(id, baggagePolicyRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBaggagePolicy(@PathVariable Long id) throws Exception {
        baggagePolicyService.deleteBaggagePolicy(id);
        return ResponseEntity.noContent().build();
    }
}
