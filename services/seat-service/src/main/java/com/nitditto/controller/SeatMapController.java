package com.nitditto.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.nitditto.payload.request.SeatMapRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.SeatMapResponse;
import com.nitditto.service.SeatMapService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/seat-maps")
@RequiredArgsConstructor
public class SeatMapController {

    private final SeatMapService seatMapService;

    @PostMapping
    public ResponseEntity<ApiResponse<SeatMapResponse>> createSeatMap(
            @RequestHeader("X-User-Id") final UUID userId,
            @Valid @RequestBody final SeatMapRequest seatMapRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(this.seatMapService.createSeatMap(userId, seatMapRequest)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SeatMapResponse>> getSeatMapById(@PathVariable final UUID id) {
        return ResponseEntity.ok(ApiResponse.success(this.seatMapService.getSeatMapById(id)));
    }

    @GetMapping("/cabin-class/{cabinClassId}")
    public ResponseEntity<ApiResponse<SeatMapResponse>> getSeatMapByCabinClassId(
            @PathVariable final UUID cabinClassId) {
        return ResponseEntity.ok(ApiResponse.success(this.seatMapService.getSeatMapByCabinClassId(cabinClassId)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SeatMapResponse>> updateSeatMap(
            @PathVariable final UUID id,
            @Valid @RequestBody final SeatMapRequest seatMapRequest) {
        return ResponseEntity.ok(ApiResponse.success(this.seatMapService.updateSeatMap(id, seatMapRequest)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSeatMap(@PathVariable final UUID id) {
        this.seatMapService.deleteSeatMap(id);
        return ResponseEntity.noContent().build();
    }

}
