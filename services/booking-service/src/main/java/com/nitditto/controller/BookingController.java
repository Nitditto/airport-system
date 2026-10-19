package com.nitditto.controller;

import lombok.RequiredArgsConstructor;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nitditto.enums.BookingStatus;
import com.nitditto.payload.request.BookingRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.BookingResponse;
import com.nitditto.payload.response.PaymentInitiateResponse;
import com.nitditto.service.BookingService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

  private final BookingService bookingService;

  @PostMapping
  public ResponseEntity<ApiResponse<PaymentInitiateResponse>> createBooking(
      @RequestHeader("X-User-Id") final UUID userId,
      @Valid @RequestBody final BookingRequest bookingRequest) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(this.bookingService.createBooking(bookingRequest, userId)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<BookingResponse>> getBookingById(@PathVariable final UUID id) {
    return ResponseEntity.ok(ApiResponse.success(this.bookingService.getBookingById(id)));
  }

  @GetMapping("/airline")
  public ResponseEntity<ApiResponse<Page<BookingResponse>>> getBookingsByAirline(
      @RequestParam(required = false) final String search,
      @RequestParam(required = false) final BookingStatus status,
      @RequestParam(required = false) final UUID flightInstanceId,
      @RequestHeader("X-User-Id") final UUID userId,
      @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) final Pageable pageable) {
    return ResponseEntity.ok(ApiResponse.success(
        this.bookingService.getAllBookingsByAirline(userId, search, status, flightInstanceId, pageable)));
  }

  @GetMapping("/user/history")
  public ResponseEntity<ApiResponse<Page<BookingResponse>>> getBookingsByUser(
      @RequestHeader("X-User-Id") final UUID userId,
      @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) final Pageable pageable) {
    return ResponseEntity.ok(ApiResponse.success(this.bookingService.getBookingsByUser(userId, pageable)));
  }

  @PatchMapping("/{id}/cancel")
  public ResponseEntity<ApiResponse<BookingResponse>> cancelBooking(@PathVariable final UUID id,
      @RequestHeader("X-User-Id") final UUID userId) {
    return ResponseEntity.ok(ApiResponse.success(this.bookingService.cancelBooking(id, userId)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteBooking(@PathVariable final UUID id,
      @RequestHeader("X-User-Id") final UUID userId) {
    this.bookingService.deleteBooking(id, userId);
    return ResponseEntity.noContent().build();
  }

}
