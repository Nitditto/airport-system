package com.nitditto.controller;

import com.nitditto.payload.dto.PaymentDTO;
import com.nitditto.payload.request.PaymentInitiateRequest;
import com.nitditto.payload.request.PaymentVerifyRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.PaymentInitiateResponse;
import com.nitditto.service.PaymentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<PaymentInitiateResponse>> initiatePayment(
            @Valid @RequestBody final PaymentInitiateRequest request) {
        final PaymentInitiateResponse response = this.paymentService.initiatePayment(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<PaymentDTO>> verifyPayment(@Valid @RequestBody final PaymentVerifyRequest request) {
        final PaymentDTO response = this.paymentService.verifyPayment(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/check-payment-vnpay")
    public ResponseEntity<ApiResponse<PaymentDTO>> checkPaymentVNPay(
            @RequestParam final Map<String, String> allParams) {
        final PaymentDTO response = this.paymentService.checkPaymentVNPay(allParams);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/batch-by-ids")
    public ResponseEntity<ApiResponse<Map<UUID, PaymentDTO>>> getPaymentsByBookingIds(
            @RequestBody final List<UUID> bookingIds) {
        return ResponseEntity.ok(ApiResponse.success(this.paymentService.getPaymentsByBookingIds(bookingIds)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PaymentDTO>>> getAllPayments(
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size,
            @RequestParam(defaultValue = "createdAt") final String sortBy,
            @RequestParam(defaultValue = "DESC") final String sortDirection,
            @RequestHeader("X-User-Id") final UUID userId) {
        final Sort.Direction direction = sortDirection.equalsIgnoreCase("ASC") ? Sort.Direction.ASC : Sort.Direction.DESC;
        final Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        final Page<PaymentDTO> payments = this.paymentService.getAllPayments(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(payments));
    }
}
