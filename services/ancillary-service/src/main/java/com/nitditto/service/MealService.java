package com.nitditto.service;

import com.nitditto.payload.request.MealRequest;
import com.nitditto.payload.response.MealResponse;

import java.util.List;
import java.util.UUID;

public interface MealService {
  MealResponse createMeal(UUID airlineId, MealRequest request);

  MealResponse getMealById(UUID id);

  MealResponse updateMeal(UUID airlineId, UUID id, MealRequest request);

  List<MealResponse> getByAirlineId(UUID airlineId);

  void deleteMealById(UUID id);

  MealResponse updateAvailability(UUID id, Boolean availability);

}
