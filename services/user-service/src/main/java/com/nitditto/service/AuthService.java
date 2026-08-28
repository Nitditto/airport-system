package com.nitditto.service;

import com.nitditto.payload.dto.UserDTO;
import com.nitditto.payload.response.AuthResponse;

public interface AuthService {

  AuthResponse login(String email, String password);
  AuthResponse signup(UserDTO request);
}
