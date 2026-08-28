package com.nitditto.exception;

public class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(final String resource, final String field, final Object value) {
    super("%s not found with %s: %s".formatted(resource, field, value));
  }
}
