package com.nitditto.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.nitditto.payload.request.PaymentInitiateRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.PaymentInitiateResponse;

import jakarta.validation.Valid;

@FeignClient(name = "payment-service")
public interface PaymentClient {
  @PostMapping("/api/payments/initiate")
  ResponseEntity<ApiResponse<PaymentInitiateResponse>> initiatePayment(
      @Valid @RequestBody final PaymentInitiateRequest request);
}
