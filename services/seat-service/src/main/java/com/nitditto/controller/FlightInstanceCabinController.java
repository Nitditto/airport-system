package com.nitditto.controller;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nitditto.payload.request.FlightInstanceCabinRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.FlightInstanceCabinResponse;
import com.nitditto.service.FlightInstanceCabinService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/flight-instance-cabins")
@RequiredArgsConstructor
public class FlightInstanceCabinController {

    private final FlightInstanceCabinService flightInstanceCabinService;

    @PostMapping
    public ResponseEntity<ApiResponse<FlightInstanceCabinResponse>> createFlightInstanceCabin(
            @Valid @RequestBody final FlightInstanceCabinRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(this.flightInstanceCabinService.createFlightInstanceCabin(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FlightInstanceCabinResponse>> getFlightInstanceCabinById(
            @PathVariable final UUID id) {
        return ResponseEntity.ok(
                ApiResponse.success(this.flightInstanceCabinService.getFlightInstanceCabinById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<FlightInstanceCabinResponse>>> getByFlightInstanceId(
            @RequestParam final UUID flightInstanceId, final Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                this.flightInstanceCabinService.getByFlightInstanceId(flightInstanceId, pageable)));
    }

    @GetMapping("/flight-instance/{flightInstanceId}/cabin-class/{cabinClassId}")
    public ResponseEntity<ApiResponse<FlightInstanceCabinResponse>> getByFlightInstanceIdAndCabinClassId(
            @PathVariable final UUID flightInstanceId, @PathVariable final UUID cabinClassId) {
        return ResponseEntity.ok(ApiResponse.success(
                this.flightInstanceCabinService.getByFlightInstanceIdAndCabinClassId(flightInstanceId, cabinClassId)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FlightInstanceCabinResponse>> updateFlightInstanceCabin(
            @PathVariable final UUID id, @Valid @RequestBody final FlightInstanceCabinRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(this.flightInstanceCabinService.updateFlightInstanceCabin(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFlightInstanceCabin(@PathVariable final UUID id) {
        this.flightInstanceCabinService.deleteFlightInstanceCabin(id);
        return ResponseEntity.noContent().build();
    }

}
