package com.nitditto.booking_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nitditto.enums.PaymentGateway;
import com.nitditto.payload.request.BookingRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class BookingRequestValidationTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void paymentGateway_isRequired_soBookingNeverFallsBackToAnUnconfiguredGateway() {
    final BookingRequest request = BookingRequest.builder().build();

    assertThat(this.validator.validateProperty(request, "paymentGateway"))
        .extracting(violation -> violation.getMessage())
        .containsExactly("Payment gateway is required");
  }

  @Test
  void paymentGateway_whenProvided_isAccepted() {
    final BookingRequest request = BookingRequest.builder().paymentGateway(PaymentGateway.RAZORPAY).build();

    assertThat(this.validator.validateProperty(request, "paymentGateway")).isEmpty();
  }
}
