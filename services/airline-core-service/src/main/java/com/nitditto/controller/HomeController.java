package com.nitditto.controller;

import com.nitditto.payload.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

  @GetMapping
  public ApiResponse createApiResponse() {
    ApiResponse apiResponse = new ApiResponse(
        "Hello airline core service"
    );
    return apiResponse;
  }
}
