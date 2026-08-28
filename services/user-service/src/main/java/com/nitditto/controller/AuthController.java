package com.nitditto.controller;

import com.nitditto.payload.dto.UserDTO;
import com.nitditto.payload.request.LoginRequest;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.payload.response.AuthResponse;
import com.nitditto.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/signup")
  public ResponseEntity<ApiResponse<AuthResponse>> signup(@RequestBody @Valid final UserDTO userDTO) {
    return ResponseEntity.ok(ApiResponse.success(this.authService.signup(userDTO)));
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody @Valid final LoginRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(this.authService.login(request.getEmail(), request.getPassword())));
  }

}
