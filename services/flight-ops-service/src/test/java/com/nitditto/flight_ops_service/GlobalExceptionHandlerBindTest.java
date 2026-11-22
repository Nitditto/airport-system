package com.nitditto.flight_ops_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nitditto.exception.GlobalExceptionHandler;
import com.nitditto.payload.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerBindTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @SuppressWarnings("unused")
  private void target(final String ignored) {
  }

  private BeanPropertyBindingResult bindingResult() {
    final BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "request");
    // Lỗi ép kiểu: defaultMessage của Spring chứa tên class nội bộ.
    result.addError(new FieldError("request", "cabinClass", "LUXURY", true, new String[] {"typeMismatch"}, null,
        "Failed to convert property value to required type 'com.nitditto.enums.CabinClassType'"));
    result.addError(new FieldError("request", "departureDate", null, false, new String[] {"NotNull"}, null,
        "Departure date is required"));
    return result;
  }

  @Test
  void validationException_masksTypeMismatchButKeepsConstraintMessages() throws Exception {
    final MethodParameter parameter = new MethodParameter(
        GlobalExceptionHandlerBindTest.class.getDeclaredMethod("target", String.class), 0);

    final ResponseEntity<ApiResponse<Void>> response = this.handler.handleValidation(
        new MethodArgumentNotValidException(parameter, bindingResult()));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().getMessage())
        .contains("cabinClass: invalid value", "departureDate: Departure date is required")
        .doesNotContain("com.nitditto");
  }

  @Test
  void bindException_isAlsoMasked() {
    final ResponseEntity<ApiResponse<Void>> response = this.handler.handleBind(
        new org.springframework.validation.BindException(bindingResult()));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().getMessage()).doesNotContain("com.nitditto");
  }
}
