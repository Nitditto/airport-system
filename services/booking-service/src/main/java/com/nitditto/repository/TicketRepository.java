package com.nitditto.repository;

import com.nitditto.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    List<Ticket> findByBookingId(UUID bookingId);

    List<Ticket> findByBookingIdIn(List<UUID> bookingIds);

    void deleteByBookingId(UUID bookingId);

    boolean existsByTicketNumber(String ticketNumber);
}
