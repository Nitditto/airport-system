package com.nitditto.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Xung đột Idempotency-Key: 409 (request trước cùng key còn đang chạy) hoặc 422 (cùng key, khác body). */
@Getter
public class IdempotencyConflictException extends RuntimeException {

  private final HttpStatus status;

  public IdempotencyConflictException(final HttpStatus status, final String message) {
    super(message);
    this.status = status;
  }
}
