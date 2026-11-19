package com.nitditto.service.impl;

import com.nitditto.enums.BookingStatus;
import com.nitditto.mapper.BookingMapper;
import com.nitditto.model.Booking;
import com.nitditto.payload.request.BookingRequest;
import com.nitditto.repository.BookingRepository;
import com.nitditto.service.BookingCreationService;
import com.nitditto.service.PassengerService;
import com.nitditto.service.TicketService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingCreationServiceImpl implements BookingCreationService {

  private final BookingRepository bookingRepository;
  private final PassengerService passengerService;
  private final TicketService ticketService;

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Booking createPending(final BookingRequest request, final UUID userId, final UUID airlineId,
      final List<UUID> seatInstanceIds) {
    final Booking booking = BookingMapper.toEntity(request, userId, this.generateBookingReference(), seatInstanceIds);
    booking.setAirlineId(airlineId);
    final Booking saved = this.bookingRepository.saveAndFlush(booking);

    this.ticketService.generateTicketsForBooking(saved.getId(),
        this.passengerService.createPassengers(request.getPassengers(), saved.getId(), userId));

    log.info("Created pending booking {} (id={}) for user {}", saved.getBookingReference(), saved.getId(), userId);
    return saved;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void abandonPending(final UUID bookingId) {
    // Khoá dòng để không chạy song song với confirmPayment của cùng booking.
    final Booking booking = this.bookingRepository.findByIdForUpdate(bookingId).orElse(null);
    if (booking == null || booking.getBookingStatus() != BookingStatus.PENDING) {
      return;
    }
    booking.setBookingStatus(BookingStatus.CANCELLED);
    this.bookingRepository.save(booking);
    this.ticketService.cancelTicketsForBooking(bookingId);
    log.info("Abandoned pending booking {} because payment could not be initiated", bookingId);
  }

  private String generateBookingReference() {
    String bookingReference;
    do {
      bookingReference = "BK" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    } while (this.bookingRepository.existsByBookingReference(bookingReference));
    return bookingReference;
  }
}
