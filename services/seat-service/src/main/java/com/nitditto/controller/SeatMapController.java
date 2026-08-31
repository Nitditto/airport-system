package com.nitditto.controller;

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

    @PostMapping("/airline/{airlineId}")
    public ResponseEntity<ApiResponse<SeatMapResponse>> createSeatMap(
            @PathVariable final UUID airlineId,
            @Valid @RequestBody final SeatMapRequest seatMapRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(this.seatMapService.createSeatMap(airlineId, seatMapRequest)));
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
