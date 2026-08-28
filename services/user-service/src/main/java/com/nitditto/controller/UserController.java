package com.nitditto.controller;

import com.nitditto.payload.dto.UserDTO;
import com.nitditto.payload.response.ApiResponse;
import com.nitditto.service.UserService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

  private final UserService userService;

  @GetMapping("/profile")
  public ResponseEntity<ApiResponse<UserDTO>> getUserProfile(
      @RequestHeader("X-User-Email") final String email) {
    return ResponseEntity.ok(ApiResponse.success(this.userService.getUserByEmail(email)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<UserDTO>> getUserById(@PathVariable("id") final UUID id) {
    return ResponseEntity.ok(ApiResponse.success(this.userService.getUserById(id)));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<UserDTO>>> getAllUsers(
      @RequestHeader("X-User-Email") final String email) {
    return ResponseEntity.ok(ApiResponse.success(this.userService.getAllUsers()));
  }
}
