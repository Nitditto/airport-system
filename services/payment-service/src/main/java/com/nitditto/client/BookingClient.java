package com.nitditto.client;

import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.BookingResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "booking-service")
public interface BookingClient {

  @PatchMapping("/api/bookings/{id}/confirm-payment")
  ApiResponse<BookingResponse> confirmPayment(@PathVariable("id") UUID bookingId);
}
