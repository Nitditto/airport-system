package com.nitditto.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nitditto.payload.request.MealRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.MealResponse;
import com.nitditto.service.MealService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/meals")
@RequiredArgsConstructor
public class MealController {

  private final MealService mealService;

    @PostMapping
  public ResponseEntity<ApiResponse<MealResponse>> createMeal(
            @RequestHeader("X-User-Id") final UUID userId,
      @Valid @RequestBody final MealRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(this.mealService.createMeal(userId, request)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<MealResponse>> getMealById(@PathVariable final UUID id) {
    return ResponseEntity.ok(ApiResponse.success(this.mealService.getMealById(id)));
  }

    @GetMapping("/airline")
  public ResponseEntity<ApiResponse<List<MealResponse>>> getByAirlineId(
            @RequestHeader("X-User-Id") final UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(this.mealService.getByAirlineId(userId)));
  }

    @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<MealResponse>> updateMeal(
            @RequestHeader("X-User-Id") final UUID userId,
      @PathVariable final UUID id,
      @Valid @RequestBody final MealRequest request) {
    return ResponseEntity.ok(
                ApiResponse.success(this.mealService.updateMeal(userId, id, request)));
  }

  @PatchMapping("/{id}/availability")
  public ResponseEntity<ApiResponse<MealResponse>> updateAvailability(
      @PathVariable final UUID id,
      @RequestParam final boolean available) {
    return ResponseEntity.ok(
        ApiResponse.success(this.mealService.updateAvailability(id, available)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteMeal(@PathVariable final UUID id) {
    this.mealService.deleteMealById(id);
    return ResponseEntity.noContent().build();
  }
}
