package com.nitditto.service.impl;

import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.mapper.MealMapper;
import com.nitditto.model.Meal;
import com.nitditto.payload.request.MealRequest;
import com.nitditto.payload.response.MealResponse;
import com.nitditto.repository.MealRepository;
import com.nitditto.service.MealService;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MealServiceImpl implements MealService {

    private final MealRepository mealRepository;

    @Override
    @Transactional
    public MealResponse createMeal(final UUID airlineId, final MealRequest request) {
        if (this.mealRepository.existsByCodeAndAirlineId(request.getCode(), airlineId)) {
            throw new BusinessException(
                    "Meal code '%s' already exists for airline %s".formatted(request.getCode(), airlineId));
        }

        final Meal meal = Meal.builder()
                .code(request.getCode())
                .name(request.getName())
                .mealType(request.getMealType())
                .dietaryRestriction(request.getDietaryRestriction())
                .ingredients(request.getIngredients())
                .imageUrl(request.getImageUrl())
                .requiresAdvanceBooking(
                        request.getRequiresAdvanceBooking() != null ? request.getRequiresAdvanceBooking() : false)
                .advanceBookingHours(request.getAdvanceBookingHours())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .airlineId(airlineId)
                .build();

        final Meal saved = this.mealRepository.save(meal);
        return MealMapper.toResponse(saved);
    }

    @Override
    public MealResponse getMealById(final UUID id) {
        final Meal meal = this.mealRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Meal", "id", id));
        return MealMapper.toResponse(meal);
    }

    @Override
    @Transactional
    public MealResponse updateMeal(final UUID airlineId, final UUID id, final MealRequest request) {
        final Meal meal = this.mealRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Meal", "id", id));

        if (this.mealRepository.existsByAirlineIdAndCodeAndIdNot(airlineId, request.getCode(), id)) {
            throw new BusinessException(
                    "Meal code '%s' already exists for airline %s".formatted(request.getCode(), airlineId));
        }

        meal.setCode(request.getCode());
        meal.setName(request.getName());
        meal.setMealType(request.getMealType());
        meal.setDietaryRestriction(request.getDietaryRestriction());
        meal.setIngredients(request.getIngredients());
        meal.setImageUrl(request.getImageUrl());
        if (request.getRequiresAdvanceBooking() != null) {
            meal.setRequiresAdvanceBooking(request.getRequiresAdvanceBooking());
        }
        meal.setAdvanceBookingHours(request.getAdvanceBookingHours());
        if (request.getDisplayOrder() != null) {
            meal.setDisplayOrder(request.getDisplayOrder());
        }

        final Meal updated = this.mealRepository.save(meal);
        return MealMapper.toResponse(updated);
    }

    @Override
    public List<MealResponse> getByAirlineId(final UUID airlineId) {
        return this.mealRepository.findByAirlineId(airlineId)
                .stream().map(MealMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteMealById(final UUID id) {
        if (!this.mealRepository.existsById(id)) {
            throw new ResourceNotFoundException("Meal", "id", id);
        }
        this.mealRepository.deleteById(id);
    }

    @Override
    @Transactional
    public MealResponse updateAvailability(final UUID id, final Boolean availability) {
        final Meal meal = this.mealRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Meal", "id", id));
        meal.setAvailable(availability);
        final Meal updated = this.mealRepository.save(meal);
        return MealMapper.toResponse(updated);
    }
}
