package com.nitditto.controller;

import com.nitditto.payload.dto.UserDTO;
import com.nitditto.service.UserService;
import java.util.List;
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
  public ResponseEntity<UserDTO> getUserProfile(@RequestHeader("X-User-Email") String email)
      throws Exception {
    UserDTO user = userService.getUserByEmail(email);
    return ResponseEntity.ok(user);
  }

  @GetMapping("/{id}")
  public ResponseEntity<UserDTO> getUserById(@PathVariable("id") Long id) throws Exception {
    UserDTO user = userService.getUserById(id);
    return ResponseEntity.ok(user);
  }

  @GetMapping
  public ResponseEntity<List<UserDTO>> getAllUsers(@RequestHeader("X-User-Email") String email)
      throws Exception {
    List<UserDTO> users = userService.getAllUsers();
    return ResponseEntity.ok(users);
  }
}
