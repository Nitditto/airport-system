package com.nitditto.exception;

public class BusinessException extends RuntimeException {

  public BusinessException(final String message) {
    super(message);
  }
}
