package com.nitditto.exception;

import com.nitditto.payload.response.ApiResponse;
import feign.FeignException;
import feign.RetryableException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotFound(final ResourceNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ApiResponse<Void>> handleForbidden(final ForbiddenException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusiness(final BusinessException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(final MethodArgumentNotValidException ex) {
    final String message = ex.getBindingResult().getFieldErrors().stream()
        .map(error -> "%s: %s".formatted(error.getField(), error.getDefaultMessage()))
        .collect(Collectors.joining("; "));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(message));
  }

  // Lỗi từ service khác mà tầng service chưa dịch sang BusinessException/ResourceNotFoundException (CLAUDE.md §8).
  // Không trả ex.getMessage(): nó chứa URL nội bộ và body response của service đích. Chi tiết chỉ ghi log.
  @ExceptionHandler(FeignException.class)
  public ResponseEntity<ApiResponse<Void>> handleFeign(final FeignException ex) {
    final String target = ex.request() != null ? ex.request().httpMethod() + " " + ex.request().url() : "unknown";
    if (ex instanceof FeignException.NotFound) {
      // Id do client gửi trỏ tới bản ghi không tồn tại ở service đích.
      log.warn("Upstream resource not found: {}", target);
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(ApiResponse.error("Referenced resource was not found"));
    }
    if (ex instanceof RetryableException || ex.status() < 0
        || ex.status() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
      // Không kết nối được / timeout / chưa có instance trong Eureka — client có thể thử lại.
      log.error("Upstream service unavailable: {}", target, ex);
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(ApiResponse.error("A dependent service is temporarily unavailable, please retry"));
    }
    log.error("Upstream call failed with status {}: {}", ex.status(), target, ex);
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .body(ApiResponse.error("A dependent service failed to process the request"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpected(final Exception ex) {
    log.error("Unexpected error", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error("Internal server error"));
  }
}
