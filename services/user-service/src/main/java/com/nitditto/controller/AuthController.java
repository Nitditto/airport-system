package com.nitditto.controller;

import com.nitditto.payload.dto.UserDTO;
import com.nitditto.payload.request.LoginRequest;
import com.nitditto.payload.response.AuthResponse;
import com.nitditto.repository.UserRepository;
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
  public ResponseEntity<AuthResponse> signup(@RequestBody @Valid UserDTO userDTO) throws Exception {
    AuthResponse authResponse = authService.signup(userDTO);
    return ResponseEntity.ok(authResponse);
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request)
      throws Exception {
    AuthResponse authResponse = authService.login(request.getEmail(), request.getPassword());
    return ResponseEntity.ok(authResponse);
  }

}
