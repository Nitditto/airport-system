package com.nitditto.exception;

import com.nitditto.payload.response.ApiResponse;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotFound(final ResourceNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
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

  // UUID sai định dạng ở @PathVariable, hoặc CabinClassType không khớp enum ở
  // GET /api/cabin-classes/aircraft/{id}/name/{cabinClass}. Lỗi của client -> 400, không phải 500.
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(final MethodArgumentTypeMismatchException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error("Invalid value for parameter '%s': %s".formatted(ex.getName(), ex.getValue())));
  }

  // Nguồn chính: CabinClassType.valueOf(request.getName()) trong CabinClassMapper khi client
  // gửi name không khớp enum (vd. "economy" thay vì "ECONOMY").
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(final IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
  }

  // Vi phạm unique constraint: seat_maps.cabin_class_id, seats(seat_map_id, seat_number).
  // Không trả ex.getMessage() vì message của driver lộ tên bảng/cột ra ngoài.
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(final DataIntegrityViolationException ex) {
    log.warn("Data integrity violation", ex);
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiResponse.error("Operation conflicts with existing data"));
  }

  // Seat có @Version -> 2 request cùng sửa 1 ghế thì request đến sau ném lỗi này.
  // Đây là tranh chấp bình thường, client nên tải lại rồi thử lại, không phải lỗi server.
  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(final ObjectOptimisticLockingFailureException ex) {
    log.warn("Optimistic locking conflict", ex);
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiResponse.error("This record was modified by another request, please reload and retry"));
  }

  // Lưới an toàn cuối cùng - không bao giờ để lộ stack trace/thông điệp nội bộ ra client.
  // Log đầy đủ ở server, trả về client một message chung chung.
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpected(final Exception ex) {
    log.error("Unexpected error", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error("Internal server error"));
  }
}
