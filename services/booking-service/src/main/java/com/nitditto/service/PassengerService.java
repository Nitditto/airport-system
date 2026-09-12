package com.nitditto.service;

import com.nitditto.payload.request.PassengerRequest;
import com.nitditto.payload.response.PassengerResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PassengerService {

    List<PassengerResponse> createPassengers(List<PassengerRequest> requests, UUID bookingId, UUID userId);

    Map<UUID, List<PassengerResponse>> findByBookingIds(List<UUID> bookingIds);

    void deletePassengersForBooking(UUID bookingId);
}
