package com.nitditto.service.impl;

import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.mapper.FlightMealMapper;
import com.nitditto.model.FlightMeal;
import com.nitditto.model.Meal;
import com.nitditto.payload.request.FlightMealRequest;
import com.nitditto.payload.response.FlightMealResponse;
import com.nitditto.repository.FlightMealRepository;
import com.nitditto.repository.MealRepository;
import com.nitditto.service.FlightMealService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FlightMealServiceImpl implements FlightMealService {

    private static final int MAX_BATCH_SIZE = 200;

    private final FlightMealRepository flightMealRepository;
    private final MealRepository mealRepository;

    @Override
    @Transactional
    public FlightMealResponse createFlightMeal(final FlightMealRequest request) {
        final Meal meal = this.mealRepository.findById(request.getMealId()).orElseThrow(
                () -> new ResourceNotFoundException("Meal", "id", request.getMealId()));

        if (this.flightMealRepository.existsByFlightIdAndMealId(request.getFlightId(), meal.getId())) {
            throw new BusinessException("Meal %s is already assigned to flight %s"
                    .formatted(meal.getId(), request.getFlightId()));
        }

        final FlightMeal flightMeal = FlightMeal.builder()
                .flightId(request.getFlightId())
                .mealId(meal.getId())
                .available(request.getAvailable() != null ? request.getAvailable() : Boolean.TRUE)
                .price(request.getPrice())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .build();
        final FlightMeal saved = this.flightMealRepository.save(flightMeal);

        return FlightMealMapper.toResponse(saved, meal);
    }

    @Override
    public FlightMealResponse getFlightMealById(final UUID id) {
        final FlightMeal flightMeal = this.flightMealRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Flight Meal", "id", id));
        final Meal meal = this.mealRepository.findById(flightMeal.getMealId()).orElseThrow(
                () -> new ResourceNotFoundException("Meal", "id", flightMeal.getMealId()));
        return FlightMealMapper.toResponse(flightMeal, meal);
    }

    @Override
    public List<FlightMealResponse> getByFlightId(final UUID flightId) {
        return toResponses(this.flightMealRepository.findByFlightId(flightId));
    }

    @Override
    public List<FlightMealResponse> getAllByIds(final List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > MAX_BATCH_SIZE) {
            throw new BusinessException(
                    "Cannot request more than %d flight meals at once".formatted(MAX_BATCH_SIZE));
        }
        return toResponses(this.flightMealRepository.findAllById(ids.stream().distinct().toList()));
    }

    @Override
    @Transactional
    public FlightMealResponse updateFlightMeal(final UUID id, final FlightMealRequest request) {
        final FlightMeal flightMeal = this.flightMealRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Flight Meal", "id", id));

        final UUID targetMealId = request.getMealId() != null
                ? request.getMealId() : flightMeal.getMealId();
        final Meal meal = this.mealRepository.findById(targetMealId).orElseThrow(
                () -> new ResourceNotFoundException("Meal", "id", targetMealId));

        if (this.flightMealRepository.existsByFlightIdAndMealIdAndIdNot(
                request.getFlightId(), targetMealId, id)) {
            throw new BusinessException("Meal %s is already assigned to flight %s"
                    .formatted(targetMealId, request.getFlightId()));
        }

        flightMeal.setFlightId(request.getFlightId());
        flightMeal.setMealId(meal.getId());
        if (request.getAvailable() != null) {
            flightMeal.setAvailable(request.getAvailable());
        }
        flightMeal.setPrice(request.getPrice());
        if (request.getDisplayOrder() != null) {
            flightMeal.setDisplayOrder(request.getDisplayOrder());
        }
        final FlightMeal saved = this.flightMealRepository.save(flightMeal);

        return FlightMealMapper.toResponse(saved, meal);
    }

    @Override
    @Transactional
    public void deleteFlightMealById(final UUID id) {
        if (!this.flightMealRepository.existsById(id)) {
            throw new ResourceNotFoundException("Flight Meal", "id", id);
        }
        this.flightMealRepository.deleteById(id);
    }

    @Override
    @Transactional
    public FlightMealResponse updateFlightMealAvailability(final UUID id, final Boolean available) {
        final FlightMeal flightMeal = this.flightMealRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Flight Meal", "id", id));
        flightMeal.setAvailable(available);
        final FlightMeal saved = this.flightMealRepository.save(flightMeal);
        final Meal meal = this.mealRepository.findById(saved.getMealId()).orElse(null);
        return FlightMealMapper.toResponse(saved, meal);
    }

    @Override
    public Double calculateMealPrice(final List<UUID> flightMealIds) {
        if (flightMealIds == null || flightMealIds.isEmpty()) {
            return 0.0;
        }
        if (flightMealIds.size() > MAX_BATCH_SIZE) {
            throw new BusinessException(
                    "Cannot price more than %d flight meals at once".formatted(MAX_BATCH_SIZE));
        }
        final List<FlightMeal> flightMeals = this.flightMealRepository.findAllById(
                flightMealIds.stream().distinct().toList());

        double total = 0.0;
        for (final FlightMeal flightMeal : flightMeals) {
            if (flightMeal.getPrice() != null) {
                total += flightMeal.getPrice();
            }
        }
        return total;
    }

    private List<FlightMealResponse> toResponses(final List<FlightMeal> flightMeals) {
        if (flightMeals.isEmpty()) {
            return List.of();
        }
        final List<UUID> mealIds = flightMeals.stream()
                .map(FlightMeal::getMealId).distinct().toList();
        final Map<UUID, Meal> mealById = this.mealRepository.findAllById(mealIds)
                .stream().collect(Collectors.toMap(Meal::getId, Function.identity()));

        final List<FlightMealResponse> responses = new ArrayList<>(flightMeals.size());
        for (final FlightMeal flightMeal : flightMeals) {
            responses.add(FlightMealMapper.toResponse(flightMeal, mealById.get(flightMeal.getMealId())));
        }
        return responses;
    }
}
