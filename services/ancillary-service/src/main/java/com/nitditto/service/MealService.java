package com.nitditto.service;

import com.nitditto.payload.request.MealRequest;
import com.nitditto.payload.response.MealResponse;

import java.util.List;
import java.util.UUID;

public interface MealService {
  MealResponse createMeal(UUID userId, MealRequest request);

  MealResponse getMealById(UUID id);

  MealResponse updateMeal(UUID userId, UUID id, MealRequest request);

  List<MealResponse> getByAirlineId(UUID userId);

  void deleteMealById(UUID id);

  MealResponse updateAvailability(UUID id, Boolean availability);

}
