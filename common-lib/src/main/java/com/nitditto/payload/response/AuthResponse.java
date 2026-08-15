package com.nitditto.payload.response;

import com.nitditto.payload.dto.UserDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {

  private String jwt;
  private String message;
  private String title;
  private UserDTO user;
}
