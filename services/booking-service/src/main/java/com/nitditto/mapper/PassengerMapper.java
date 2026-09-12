package com.nitditto.mapper;

import com.nitditto.model.Passenger;
import com.nitditto.payload.request.PassengerRequest;
import com.nitditto.payload.response.PassengerResponse;

public class PassengerMapper {

    private PassengerMapper() {
    }

    public static Passenger toEntity(final PassengerRequest passengerRequest) {
        return Passenger.builder()
                .firstName(passengerRequest.getFirstName())
                .lastName(passengerRequest.getLastName())
                .email(passengerRequest.getEmail())
                .phone(passengerRequest.getPhone())
                .dateOfBirth(passengerRequest.getDateOfBirth())
                .gender(passengerRequest.getGender())
                .nationality(passengerRequest.getNationality())
                .build();
    }

    public static PassengerResponse toResponse(final Passenger passenger) {
        if (passenger == null) {
            return null;
        }
        return PassengerResponse.builder()
                .id(passenger.getId())
                .bookingId(passenger.getBookingId())
                .firstName(passenger.getFirstName())
                .lastName(passenger.getLastName())
                .email(passenger.getEmail())
                .phone(passenger.getPhone())
                .dateOfBirth(passenger.getDateOfBirth())
                .gender(passenger.getGender())
                .nationality(passenger.getNationality())
                .primaryUserId(passenger.getPrimaryUserId())
                .isActive(passenger.getIsActive())
                .age(passenger.getAge())
                .fullName(passenger.getFullName())
                .isAdult(passenger.isAdult())
                .createdAt(passenger.getCreatedAt())
                .updatedAt(passenger.getUpdatedAt())
                .build();
    }
}
