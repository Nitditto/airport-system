package com.nitditto.controller;

import com.nitditto.payload.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

  @GetMapping
  public ApiResponse HomeController() {
    ApiResponse apiResponse = new ApiResponse("Hello user service");
    return apiResponse;
  }

  ;
}
