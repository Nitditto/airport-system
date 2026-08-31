package com.nitditto.controller;

import java.util.List;
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

import com.nitditto.enums.CabinClassType;
import com.nitditto.payload.request.CabinClassRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.CabinClassResponse;
import com.nitditto.service.CabinClassService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cabin-classes")
@RequiredArgsConstructor
public class CabinClassController {

    private final CabinClassService cabinClassService;

    @PostMapping
    public ResponseEntity<ApiResponse<CabinClassResponse>> createCabinClass(
            @Valid @RequestBody CabinClassRequest cabinClassRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(this.cabinClassService.createCabinClass(cabinClassRequest)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CabinClassResponse>> getCabinClassById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.success(this.cabinClassService.getCabinClassById(id)));
    }

    @GetMapping("/aircraft/{id}/name/{cabinClass}")
    public ResponseEntity<ApiResponse<CabinClassResponse>> getCabinClassesByAircraftIdAndName(
            @PathVariable("cabinClass") CabinClassType name,
            @PathVariable("id") UUID id) {
        return ResponseEntity.ok(
                ApiResponse.success(this.cabinClassService.getByAircraftIdAndName(id, name)));
    }

    @GetMapping("/aircraft/{id}")
    public ResponseEntity<ApiResponse<List<CabinClassResponse>>> getCabinClassesByAircraftId(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(this.cabinClassService.getCabinClassesByAircraftId(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CabinClassResponse>> updateCabinClass(
            @PathVariable UUID id,
            @Valid @RequestBody CabinClassRequest cabinClassRequest) {
        return ResponseEntity.ok(ApiResponse.success(this.cabinClassService.updateCabinClass(id, cabinClassRequest)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCabinClass(@PathVariable UUID id) {
        this.cabinClassService.deleteCabinClass(id);
        return ResponseEntity.noContent().build();
    }

}
