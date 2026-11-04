package com.nitditto.service;

import com.nitditto.enums.BookingStatus;
import com.nitditto.payload.request.BookingRequest;
import com.nitditto.payload.response.BookingResponse;
import com.nitditto.payload.response.PaymentInitiateResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface BookingService {

    PaymentInitiateResponse createBooking(BookingRequest request, UUID userId);

    BookingResponse getBookingById(UUID id);

    /** Xác nhận booking sau khi thanh toán thành công (payment-service gọi); idempotent, tự kiểm tra lại thanh toán. */
    BookingResponse confirmPayment(UUID id);

    Page<BookingResponse> getAllBookingsByAirline(UUID userId, String search, BookingStatus status,
            UUID flightInstanceId, Pageable pageable);

    Page<BookingResponse> getBookingsByUser(UUID userId, Pageable pageable);

    BookingResponse cancelBooking(UUID id, UUID requesterUserId);

    void deleteBooking(UUID id, UUID requesterUserId);
}
