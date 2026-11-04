package com.nitditto.client;

import com.nitditto.payload.dto.PaymentDTO;
import com.nitditto.payload.request.PaymentInitiateRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.PaymentInitiateResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service")
public interface PaymentClient {

  @PostMapping("/api/payments/initiate")
  ApiResponse<PaymentInitiateResponse> initiatePayment(@RequestBody PaymentInitiateRequest request);

  // Map<bookingId, payment>; nếu một booking có nhiều lần thanh toán thì payment SUCCESS được ưu tiên.
  @PostMapping("/api/payments/batch-by-ids")
  ApiResponse<Map<UUID, PaymentDTO>> getPaymentsByBookingIds(@RequestBody List<UUID> bookingIds);
}
