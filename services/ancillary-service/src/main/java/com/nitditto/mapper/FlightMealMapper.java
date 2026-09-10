package com.nitditto.mapper;

import com.nitditto.model.FlightMeal;
import com.nitditto.model.Meal;
import com.nitditto.payload.response.FlightMealResponse;
import com.nitditto.payload.response.MealResponse;

public class FlightMealMapper {

    public static FlightMealResponse toResponse(FlightMeal flightMeal) {
        return toResponse(flightMeal, (MealResponse) null);
    }

    public static FlightMealResponse toResponse(FlightMeal flightMeal, Meal meal) {
        return toResponse(flightMeal, MealMapper.toResponse(meal));
    }

    public static FlightMealResponse toResponse(FlightMeal flightMeal, MealResponse mealResponse) {
        if (flightMeal == null) {
            return null;
        }

        return FlightMealResponse.builder()
                .id(flightMeal.getId())
                .flightId(flightMeal.getFlightId())
                .mealId(flightMeal.getMealId())
                .mealResponse(mealResponse)
                .available(flightMeal.getAvailable())
                .price(flightMeal.getPrice())
                .displayOrder(flightMeal.getDisplayOrder())
                .build();
    }
}

