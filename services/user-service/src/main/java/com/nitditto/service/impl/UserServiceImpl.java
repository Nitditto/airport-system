package com.nitditto.service.impl;

import com.nitditto.Mapper.UserMapper;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.model.User;
import com.nitditto.payload.dto.UserDTO;
import com.nitditto.repository.UserRepository;
import com.nitditto.service.UserService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;

  @Override
  public UserDTO getUserByEmail(final String email) {
    final User user = this.userRepository.findByEmail(email);
    if (user == null) {
      throw new ResourceNotFoundException("User", "email", email);
    }
    return UserMapper.toDTO(user);
  }

  @Override
  public UserDTO getUserById(final UUID id) {
    final User user = this.userRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    return UserMapper.toDTO(user);
  }

  @Override
  public List<UserDTO> getAllUsers() {
    final List<User> users = this.userRepository.findAll();
    return UserMapper.toDTOList(users);
  }
}
