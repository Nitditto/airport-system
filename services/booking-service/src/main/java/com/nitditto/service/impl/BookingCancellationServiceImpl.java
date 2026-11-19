package com.nitditto.service.impl;

import com.nitditto.enums.BookingStatus;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.model.Booking;
import com.nitditto.repository.BookingRepository;
import com.nitditto.service.BookingCancellationService;
import com.nitditto.service.TicketService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingCancellationServiceImpl implements BookingCancellationService {

  private final BookingRepository bookingRepository;
  private final TicketService ticketService;

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Booking markCancelled(final UUID bookingId) {
    final Booking booking = this.bookingRepository.findById(bookingId)
        .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", bookingId));
    booking.setBookingStatus(BookingStatus.CANCELLED);
    final Booking saved = this.bookingRepository.save(booking);
    this.ticketService.cancelTicketsForBooking(bookingId);
    return saved;
  }
}
