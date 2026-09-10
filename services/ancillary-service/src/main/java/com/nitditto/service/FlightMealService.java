package com.nitditto.service;

import com.nitditto.payload.request.FlightMealRequest;
import com.nitditto.payload.response.FlightMealResponse;

import java.util.List;
import java.util.UUID;

public interface FlightMealService {
    FlightMealResponse createFlightMeal(FlightMealRequest request);

    FlightMealResponse getFlightMealById(UUID id);

    List<FlightMealResponse> getByFlightId(UUID flightId);

    List<FlightMealResponse> getAllByIds(List<UUID> ids);

    FlightMealResponse updateFlightMeal(UUID id, FlightMealRequest request);

    void deleteFlightMealById(UUID id);

    FlightMealResponse updateFlightMealAvailability(UUID id, Boolean available);

    Double calculateMealPrice(List<UUID> mealIds);
}
