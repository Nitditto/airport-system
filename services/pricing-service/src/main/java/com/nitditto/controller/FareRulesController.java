package com.nitditto.controller;

import com.nitditto.payload.request.FareRulesRequest;
import com.nitditto.payload.response.FareResponse;
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
    public ResponseEntity<FareRulesResponse> createFareRules(@Valid @RequestBody FareRulesRequest fareRulesRequest) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                fareRulesService.createFareRules(fareRulesRequest)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<FareRulesResponse> getFareRulesById(@PathVariable UUID id) throws Exception {
        return ResponseEntity.status(HttpStatus.OK).body(fareRulesService.getFareRulesById(id));
    }

    @GetMapping("/fare/{fareId}")
    public ResponseEntity<FareRulesResponse> getFareRulesByFareId(@PathVariable UUID fareId) throws Exception {
        return ResponseEntity.ok(fareRulesService.getFareRulesByFareId(fareId));
    }

    @GetMapping("/airline/{airlineId}")
    public ResponseEntity<List<FareRulesResponse>> getFareRulesByAirlineId(@PathVariable UUID airlineId) throws Exception {
        return ResponseEntity.ok(fareRulesService.getFareRulesByAirlineId(airlineId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FareRulesResponse> updateFareRules(@PathVariable UUID id, @Valid @RequestBody FareRulesRequest fareRulesRequest) throws Exception {
        return ResponseEntity.ok(fareRulesService.updateFareRules(id, fareRulesRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFareRules(@PathVariable UUID id) throws Exception {
        fareRulesService.deleteFareRules(id);
        return ResponseEntity.noContent().build();
    }


}
