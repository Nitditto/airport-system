package com.nitditto.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.nitditto.payload.dto.UserDTO;
import com.nitditto.payload.response.ApiResponse;

@FeignClient(name = "user-service")
public interface UserClient {

  @GetMapping("/api/users/{id}")
  public ResponseEntity<ApiResponse<UserDTO>> getUserById(@PathVariable("id") final UUID id);
}
