package com.nitditto.service.impl;

import com.nitditto.client.PaymentClient;
import com.nitditto.enums.BookingStatus;
import com.nitditto.enums.PaymentStatus;
import com.nitditto.exception.BusinessException;
import com.nitditto.exception.PaidBookingNotConfirmableException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.model.Booking;
import com.nitditto.payload.dto.PaymentDTO;
import com.nitditto.repository.BookingRepository;
import com.nitditto.service.BookingConfirmationService;
import com.nitditto.service.PaymentWindow;
import com.nitditto.service.SeatReservationService;
import com.nitditto.service.TicketService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mỗi method chạy trong transaction RIÊNG (REQUIRES_NEW): được gọi cả từ webhook của payment-service lẫn từ việc
 * tự đối soát khi đọc danh sách booking (lúc đó transaction ngoài là read-only và không ghi được).
 */
@Slf4j
@Service
public class BookingConfirmationServiceImpl implements BookingConfirmationService {

  private final BookingRepository bookingRepository;
  private final TicketService ticketService;
  private final PaymentClient paymentClient;
  private final SeatReservationService seatReservationService;
  private final Clock clock;
  private final PaymentWindow paymentWindow;

  public BookingConfirmationServiceImpl(final BookingRepository bookingRepository,
      final TicketService ticketService, final PaymentClient paymentClient,
      final SeatReservationService seatReservationService, final Clock clock,
      final PaymentWindow paymentWindow) {
    this.bookingRepository = bookingRepository;
    this.ticketService = ticketService;
    this.paymentClient = paymentClient;
    this.seatReservationService = seatReservationService;
    this.clock = clock;
    this.paymentWindow = paymentWindow;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public BookingStatus confirmPayment(final UUID bookingId) {
    final Booking booking = this.lock(bookingId);
    if (booking.getBookingStatus() == BookingStatus.CONFIRMED) {
      return BookingStatus.CONFIRMED;
    }
    if (booking.getBookingStatus() != BookingStatus.PENDING) {
      // paymentId chỉ được gán khi booking từng được xác nhận: null nghĩa là tiền (nếu có) về SAU khi booking đã huỷ/hết hạn,
      // chưa từng được dùng cho vé nào nên phải hoàn. Có paymentId thì đây là event gửi lại của booking đã xác nhận rồi
      // huỷ: tiền đã được xử lý theo chính sách hoàn vé, không được hoàn thêm.
      if (booking.getPaymentId() == null) {
        final PaymentDTO late = this.findPayment(bookingId);
        if (late != null && late.getPaymentStatus() == PaymentStatus.SUCCESS) {
          throw new PaidBookingNotConfirmableException(bookingId, late.getId(), booking.getBookingStatus(),
              "Booking is " + booking.getBookingStatus() + " but a payment was received: it must be refunded");
        }
      }
      throw new BusinessException("Booking is " + booking.getBookingStatus() + " and cannot be confirmed");
    }

    final PaymentDTO payment = this.findPayment(bookingId);
    if (payment == null || payment.getPaymentStatus() != PaymentStatus.SUCCESS) {
      throw new BusinessException("Booking has no successful payment");
    }

    // Chốt ghế trước: nếu ghế đã bị người khác lấy (hold hết hạn trước khi thanh toán xong) thì không xác nhận,
    // tránh hai người cùng có vé cho một ghế. Khách đã trả tiền nên ném lỗi riêng để hoàn tiền.
    if (booking.getSeatInstanceIds() != null && !booking.getSeatInstanceIds().isEmpty()) {
      try {
        this.seatReservationService.confirmBooked(booking.getUserId(), booking.getSeatInstanceIds());
      } catch (final PaidBookingNotConfirmableException ex) {
        throw ex;
      } catch (final BusinessException ex) {
        throw new PaidBookingNotConfirmableException(bookingId, payment.getId(), booking.getBookingStatus(),
            "Seats can no longer be confirmed for a paid booking: it must be refunded");
      }
    }
    booking.setBookingStatus(BookingStatus.CONFIRMED);
    booking.setPaymentId(payment.getId());
    booking.setTicketIssued(true);
    this.bookingRepository.save(booking);
    this.ticketService.issueTicketsForBooking(bookingId);
    log.info("Booking {} confirmed after successful payment {}", booking.getBookingReference(), payment.getId());
    return BookingStatus.CONFIRMED;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public BookingStatus expireIfStale(final UUID bookingId) {
    final Booking booking = this.lock(bookingId);
    if (booking.getBookingStatus() != BookingStatus.PENDING) {
      return booking.getBookingStatus();
    }
    final Instant deadline = this.paymentWindow.expiresAt(booking);
    final PaymentDTO payment = this.findPayment(bookingId);
    final boolean paid = payment != null && payment.getPaymentStatus() == PaymentStatus.SUCCESS;
    if (paid || this.clock.instant().isBefore(deadline)) {
      return BookingStatus.PENDING;
    }

    booking.setBookingStatus(BookingStatus.EXPIRED);
    this.bookingRepository.save(booking);
    this.ticketService.expireTicketsForBooking(bookingId);
    if (booking.getSeatInstanceIds() != null && !booking.getSeatInstanceIds().isEmpty()) {
      this.seatReservationService.releaseQuietly(booking.getUserId(), booking.getSeatInstanceIds());
    }
    log.info("Booking {} expired: no successful payment within {}", booking.getBookingReference(), this.paymentWindow.duration());
    return BookingStatus.EXPIRED;
  }

  private Booking lock(final UUID bookingId) {
    return this.bookingRepository.findByIdForUpdate(bookingId)
        .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", bookingId));
  }

  private PaymentDTO findPayment(final UUID bookingId) {
    return this.paymentClient.getPaymentsByBookingIds(List.of(bookingId)).getData().get(bookingId);
  }
}
