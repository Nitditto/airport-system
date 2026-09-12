package com.nitditto.repository;

import com.nitditto.model.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking> {

    Page<Booking> findByUserId(UUID userId, Pageable pageable);

    long countByFlightInstanceId(UUID flightInstanceId);

    boolean existsByBookingReference(String bookingReference);
}
