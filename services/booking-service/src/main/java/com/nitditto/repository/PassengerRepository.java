package com.nitditto.repository;

import com.nitditto.model.Passenger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PassengerRepository extends JpaRepository<Passenger, UUID> {

    List<Passenger> findByBookingIdIn(List<UUID> bookingIds);

    void deleteByBookingId(UUID bookingId);
}
