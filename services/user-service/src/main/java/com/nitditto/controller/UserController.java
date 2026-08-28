package com.nitditto.controller;

import com.nitditto.payload.dto.UserDTO;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.service.UserService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

  private final UserService userService;

  @GetMapping("/profile")
  public ResponseEntity<ApiResponse<UserDTO>> getUserProfile() {
    final String email = SecurityContextHolder.getContext().getAuthentication().getName();
    return ResponseEntity.ok(ApiResponse.success(this.userService.getUserByEmail(email)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<UserDTO>> getUserById(@PathVariable("id") final UUID id) {
    return ResponseEntity.ok(ApiResponse.success(this.userService.getUserById(id)));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<UserDTO>>> getAllUsers() {
    return ResponseEntity.ok(ApiResponse.success(this.userService.getAllUsers()));
  }
}
