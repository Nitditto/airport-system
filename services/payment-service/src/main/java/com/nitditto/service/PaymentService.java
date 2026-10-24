package com.nitditto.service;

import com.nitditto.payload.dto.PaymentDTO;
import com.nitditto.payload.request.PaymentInitiateRequest;
import com.nitditto.payload.request.PaymentVerifyRequest;
import com.nitditto.payload.response.PaymentGatewayResponse;
import com.nitditto.payload.response.PaymentInitiateResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PaymentService {

    PaymentInitiateResponse initiatePayment(PaymentInitiateRequest request);

    PaymentDTO verifyPayment(PaymentVerifyRequest request);

    Page<PaymentDTO> getAllPayments(UUID userId, Pageable pageable);

    Map<UUID, PaymentDTO> getPaymentsByBookingIds(List<UUID> bookingIds);

    PaymentDTO checkPaymentVNPay(Map<String, String> allParams);

    List<PaymentGatewayResponse> getAvailableGateways();
}
