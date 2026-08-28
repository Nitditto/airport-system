package com.nitditto.service;

import com.nitditto.payload.dto.UserDTO;
import java.util.List;
import java.util.UUID;

public interface UserService {

  UserDTO getUserByEmail(String email);

  UserDTO getUserById(UUID id);

  List<UserDTO> getAllUsers();
}
