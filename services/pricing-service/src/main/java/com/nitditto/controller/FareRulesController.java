package com.nitditto.controller;

import com.nitditto.payload.request.FareRulesRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.FareRulesResponse;
import com.nitditto.service.FareRulesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequestMapping("/api/fare-rules")
@RestController
@RequiredArgsConstructor
public class FareRulesController {

    private final FareRulesService fareRulesService;

    @PostMapping
    public ResponseEntity<ApiResponse<FareRulesResponse>> createFareRules(
            @Valid @RequestBody final FareRulesRequest fareRulesRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(this.fareRulesService.createFareRules(fareRulesRequest)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FareRulesResponse>> getFareRulesById(@PathVariable final UUID id) {
        return ResponseEntity.ok(ApiResponse.success(this.fareRulesService.getFareRulesById(id)));
    }

    @GetMapping("/fare/{fareId}")
    public ResponseEntity<ApiResponse<FareRulesResponse>> getFareRulesByFareId(@PathVariable final UUID fareId) {
        return ResponseEntity.ok(ApiResponse.success(this.fareRulesService.getFareRulesByFareId(fareId)));
    }

    @GetMapping("/airline/{airlineId}")
    public ResponseEntity<ApiResponse<List<FareRulesResponse>>> getFareRulesByAirlineId(
            @PathVariable final UUID airlineId) {
        return ResponseEntity.ok(ApiResponse.success(this.fareRulesService.getFareRulesByAirlineId(airlineId)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FareRulesResponse>> updateFareRules(@PathVariable final UUID id,
            @Valid @RequestBody final FareRulesRequest fareRulesRequest) {
        return ResponseEntity.ok(ApiResponse.success(this.fareRulesService.updateFareRules(id, fareRulesRequest)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFareRules(@PathVariable final UUID id) {
        this.fareRulesService.deleteFareRules(id);
        return ResponseEntity.noContent().build();
    }


}
