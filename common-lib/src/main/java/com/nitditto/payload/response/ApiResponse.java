package com.nitditto.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

  private boolean success;
  private String message;
  private T data;

  public static <T> ApiResponse<T> success(final T data) {
    return ApiResponse.<T>builder().success(true).data(data).build();
  }

  public static <T> ApiResponse<T> error(final String message) {
    return ApiResponse.<T>builder().success(false).message(message).build();
  }
}
