package com.nitditto.service.impl;

import com.nitditto.Mapper.UserMapper;
import com.nitditto.model.User;
import com.nitditto.payload.dto.UserDTO;
import com.nitditto.repository.UserRepository;
import com.nitditto.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;

  @Override
  public UserDTO getUserByEmail(String email) throws Exception {
    User user = userRepository.findByEmail(email);
    if (user == null) {
      throw new Exception("User not found with email " + email);
    }
    return UserMapper.toDTO(user);
  }

  @Override
  public UserDTO getUserById(Long id) throws Exception {
    User user = userRepository.findById(id)
        .orElseThrow(() -> new Exception("User not found with id " + id));
    return UserMapper.toDTO(user);
  }

  @Override
  public List<UserDTO> getAllUsers() {
    List<User> users = userRepository.findAll();
    return UserMapper.toDTOList(users);
  }
}
