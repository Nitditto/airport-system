package com.nitditto.controller;

import com.nitditto.payload.request.BaggagePolicyRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.BaggagePolicyResponse;
import com.nitditto.service.BaggagePolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/baggage-policies")
@RequiredArgsConstructor
public class BaggagePolicyController {
    private final BaggagePolicyService baggagePolicyService;

    @PostMapping
    public ResponseEntity<ApiResponse<BaggagePolicyResponse>> createBaggagePolicy(
            @Valid @RequestBody final BaggagePolicyRequest baggagePolicyRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(this.baggagePolicyService.createBaggagePolicy(baggagePolicyRequest)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BaggagePolicyResponse>> getBaggagePolicyById(@PathVariable final UUID id) {
        return ResponseEntity.ok(ApiResponse.success(this.baggagePolicyService.getBaggagePolicyById(id)));
    }

    @GetMapping("/fare/{fareId}")
    public ResponseEntity<ApiResponse<BaggagePolicyResponse>> getBaggagePolicyByFareId(
            @PathVariable final UUID fareId) {
        return ResponseEntity.ok(ApiResponse.success(this.baggagePolicyService.getBaggagePolicyByFareId(fareId)));
    }

    @GetMapping("/airline/{airlineId}")
    public ResponseEntity<ApiResponse<List<BaggagePolicyResponse>>> getBaggagePoliciesByAirlineId(
            @PathVariable final UUID airlineId) {
        return ResponseEntity.ok(
                ApiResponse.success(this.baggagePolicyService.getBaggagePoliciesByAirlineId(airlineId)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BaggagePolicyResponse>> updateBaggagePolicy(@PathVariable final UUID id,
            @Valid @RequestBody final BaggagePolicyRequest baggagePolicyRequest) {
        return ResponseEntity.ok(
                ApiResponse.success(this.baggagePolicyService.updateBaggagePolicy(id, baggagePolicyRequest)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBaggagePolicy(@PathVariable final UUID id) {
        this.baggagePolicyService.deleteBaggagePolicy(id);
        return ResponseEntity.noContent().build();
    }
}
