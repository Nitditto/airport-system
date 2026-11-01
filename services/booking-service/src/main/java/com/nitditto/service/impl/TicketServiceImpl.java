package com.nitditto.service.impl;

import com.nitditto.enums.TicketStatus;
import com.nitditto.mapper.TicketMapper;
import com.nitditto.model.Ticket;
import com.nitditto.payload.response.PassengerResponse;
import com.nitditto.payload.response.TicketResponse;
import com.nitditto.repository.TicketRepository;
import com.nitditto.service.TicketService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;

    @Override
    @Transactional
    public List<TicketResponse> generateTicketsForBooking(final UUID bookingId,
            final List<PassengerResponse> passengers) {
        final List<Ticket> tickets = new ArrayList<>(passengers.size());
        for (final PassengerResponse passenger : passengers) {
            tickets.add(Ticket.builder()
                    .ticketNumber(generateUniqueTicketNumber())
                    .ticketStatus(TicketStatus.BOOKED)
                    .issuedAt(Instant.now())
                    .bookingId(bookingId)
                    .passengerId(passenger.getId())
                    .build());
        }
        return this.ticketRepository.saveAllAndFlush(tickets)
                .stream().map(TicketMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    public Map<UUID, List<TicketResponse>> findByBookingIds(final List<UUID> bookingIds) {
        if (bookingIds.isEmpty()) {
            return Map.of();
        }
        return this.ticketRepository.findByBookingIdIn(bookingIds).stream()
                .collect(Collectors.groupingBy(Ticket::getBookingId,
                        Collectors.mapping(TicketMapper::toResponse, Collectors.toList())));
    }

    @Override
    @Transactional
    public void cancelTicketsForBooking(final UUID bookingId) {
        final List<Ticket> tickets = this.ticketRepository.findByBookingId(bookingId);
        for (final Ticket ticket : tickets) {
            if (ticket.getTicketStatus() == TicketStatus.BOOKED) {
                ticket.setTicketStatus(TicketStatus.CANCELLED);
            }
        }
        this.ticketRepository.saveAll(tickets);
    }

    @Override
    @Transactional
    public void issueTicketsForBooking(final UUID bookingId) {
        final List<Ticket> tickets = this.ticketRepository.findByBookingId(bookingId);
        final Instant now = Instant.now();
        for (final Ticket ticket : tickets) {
            if (ticket.getTicketStatus() == TicketStatus.BOOKED) {
                ticket.setIssuedAt(now);
            }
        }
        this.ticketRepository.saveAll(tickets);
    }

    @Override
    @Transactional
    public void expireTicketsForBooking(final UUID bookingId) {
        final List<Ticket> tickets = this.ticketRepository.findByBookingId(bookingId);
        for (final Ticket ticket : tickets) {
            if (ticket.getTicketStatus() == TicketStatus.BOOKED) {
                ticket.setTicketStatus(TicketStatus.EXPIRED);
            }
        }
        this.ticketRepository.saveAll(tickets);
    }

    @Override
    @Transactional
    public void deleteTicketsForBooking(final UUID bookingId) {
        this.ticketRepository.deleteByBookingId(bookingId);
    }

    private String generateUniqueTicketNumber() {
        String ticketNumber;
        do {
            final String datePart = LocalDate.now().toString();
            final String randomPart = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            ticketNumber = "TKT-%s-%s".formatted(datePart, randomPart);
        } while (this.ticketRepository.existsByTicketNumber(ticketNumber));
        return ticketNumber;
    }
}
