package com.nitditto.flight_ops_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nitditto.exception.GlobalExceptionHandler;
import com.nitditto.payload.response.ApiResponse;
import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerFeignTest {

  private static final String INTERNAL_URL = "http://location-service/api/airports/42";
  private static final String INTERNAL_BODY = "{\"message\":\"secret internal detail\"}";

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void upstreamNotFound_becomes404() {
    final ResponseEntity<ApiResponse<Void>> response = this.handler.handleFeign(upstreamError(404));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertDoesNotLeakInternals(response);
  }

  @Test
  void upstreamServerError_becomes502() {
    final ResponseEntity<ApiResponse<Void>> response = this.handler.handleFeign(upstreamError(500));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertDoesNotLeakInternals(response);
  }

  @Test
  void upstreamAuthErrors_areNotForwardedToTheClient() {
    // Token của client hợp lệ (đã qua gateway); 401/403 nội bộ là lỗi tích hợp, không phải lỗi của client.
    assertThat(this.handler.handleFeign(upstreamError(401)).getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(this.handler.handleFeign(upstreamError(403)).getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
  }

  @Test
  void noInstanceInEureka_becomes503() {
    final ResponseEntity<ApiResponse<Void>> response = this.handler.handleFeign(upstreamError(503));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void connectionFailure_becomes503() {
    final RetryableException ex = new RetryableException(-1, "Connection refused executing GET " + INTERNAL_URL,
        Request.HttpMethod.GET, (Long) null, request());

    final ResponseEntity<ApiResponse<Void>> response = this.handler.handleFeign(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertDoesNotLeakInternals(response);
  }

  private static FeignException upstreamError(final int status) {
    final Response response = Response.builder()
        .status(status)
        .reason("upstream")
        .request(request())
        .headers(Map.of())
        .body(INTERNAL_BODY, StandardCharsets.UTF_8)
        .build();
    return FeignException.errorStatus("LocationClient#getAirportById(UUID)", response);
  }

  private static Request request() {
    return Request.create(Request.HttpMethod.GET, INTERNAL_URL, Map.of(), null, StandardCharsets.UTF_8, null);
  }

  private static void assertDoesNotLeakInternals(final ResponseEntity<ApiResponse<Void>> response) {
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isSuccess()).isFalse();
    assertThat(response.getBody().getMessage())
        .doesNotContain("location-service", "secret", "/api/airports");
  }
}
