package com.nitditto.controller;

import com.nitditto.payload.dto.PaymentDTO;
import com.nitditto.payload.request.PaymentInitiateRequest;
import com.nitditto.payload.request.PaymentVerifyRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.PaymentGatewayResponse;
import com.nitditto.payload.response.PaymentInitiateResponse;
import com.nitditto.enums.RefundStatus;
import com.nitditto.payload.request.RefundRequest;
import com.nitditto.payload.response.RefundResponse;
import com.nitditto.service.PaymentService;
import com.nitditto.service.gateway.VnpayIpnResponse;
import com.nitditto.service.RefundService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final RefundService refundService;

    @GetMapping("/gateways")
    public ResponseEntity<ApiResponse<List<PaymentGatewayResponse>>> getAvailableGateways() {
        return ResponseEntity.ok(ApiResponse.success(this.paymentService.getAvailableGateways()));
    }

    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<PaymentInitiateResponse>> initiatePayment(
            @Valid @RequestBody final PaymentInitiateRequest request) {
        final PaymentInitiateResponse response = this.paymentService.initiatePayment(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<PaymentDTO>> verifyPayment(@Valid @RequestBody final PaymentVerifyRequest request,
            @RequestHeader("X-User-Id") final UUID userId) {
        final PaymentDTO response = this.paymentService.verifyPayment(request, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/check-payment-vnpay")
    public ResponseEntity<ApiResponse<PaymentDTO>> checkPaymentVNPay(
            @RequestParam final Map<String, String> allParams) {
        final PaymentDTO response = this.paymentService.checkPaymentVNPay(allParams);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Hoàn tiền: booking-service gọi qua Feign (không qua gateway); qua gateway chỉ ADMIN (đường dẫn chứa "refund").
    @PostMapping("/refund")
    public ResponseEntity<ApiResponse<RefundResponse>> requestRefund(@Valid @RequestBody final RefundRequest request) {
        return ResponseEntity.ok(ApiResponse.success(this.refundService.requestRefund(request)));
    }

    @PostMapping("/refunds/{id}/retry")
    public ResponseEntity<ApiResponse<RefundResponse>> retryRefund(@PathVariable("id") final UUID id,
            @RequestParam(defaultValue = "false") final boolean confirmNotRefunded) {
        return ResponseEntity.ok(ApiResponse.success(this.refundService.retry(id, confirmNotRefunded)));
    }

    @GetMapping("/refunds")
    public ResponseEntity<ApiResponse<Page<RefundResponse>>> listRefunds(
            @RequestParam(required = false) final RefundStatus status,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size) {
        final Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.success(this.refundService.list(status, pageable)));
    }

    /**
     * IPN của VNPay (URL cấu hình trong cổng merchant). Công khai ở gateway (VNPay không có JWT): an toàn nhờ chữ ký. Trả
     * JSON thô {RspCode, Message} theo định dạng VNPay đọc, không bọc ApiResponse. Lỗi bất ngờ trả 99 để VNPay gọi lại.
     */
    @GetMapping("/vnpay-ipn")
    public ResponseEntity<VnpayIpnResponse> vnpayIpn(@RequestParam final Map<String, String> allParams) {
        try {
            return ResponseEntity.ok(this.paymentService.handleVnpayIpn(allParams));
        } catch (final RuntimeException ex) {
            log.error("VNPay IPN failed unexpectedly", ex);
            return ResponseEntity.ok(VnpayIpnResponse.UNKNOWN_ERROR);
        }
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
