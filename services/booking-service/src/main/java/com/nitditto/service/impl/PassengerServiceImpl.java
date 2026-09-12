package com.nitditto.service.impl;

import com.nitditto.mapper.PassengerMapper;
import com.nitditto.model.Passenger;
import com.nitditto.payload.request.PassengerRequest;
import com.nitditto.payload.response.PassengerResponse;
import com.nitditto.repository.PassengerRepository;
import com.nitditto.service.PassengerService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PassengerServiceImpl implements PassengerService {

    private final PassengerRepository passengerRepository;

    @Override
    @Transactional
    public List<PassengerResponse> createPassengers(final List<PassengerRequest> requests, final UUID bookingId,
            final UUID userId) {
        final List<Passenger> passengers = new ArrayList<>(requests.size());
        for (final PassengerRequest request : requests) {
            final Passenger passenger = PassengerMapper.toEntity(request);
            passenger.setPrimaryUserId(userId);
            passenger.setBookingId(bookingId);
            passengers.add(passenger);
        }
        return this.passengerRepository.saveAllAndFlush(passengers)
                .stream().map(PassengerMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    public Map<UUID, List<PassengerResponse>> findByBookingIds(final List<UUID> bookingIds) {
        if (bookingIds.isEmpty()) {
            return Map.of();
        }
        return this.passengerRepository.findByBookingIdIn(bookingIds).stream()
                .collect(Collectors.groupingBy(Passenger::getBookingId,
                        Collectors.mapping(PassengerMapper::toResponse, Collectors.toList())));
    }

    @Override
    @Transactional
    public void deletePassengersForBooking(final UUID bookingId) {
        this.passengerRepository.deleteByBookingId(bookingId);
    }
}
