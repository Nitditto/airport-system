package com.nitditto.service.impl;

import com.nitditto.Mapper.UserMapper;
import com.nitditto.config.JwtProvider;
import com.nitditto.enums.UserRole;
import com.nitditto.exception.BusinessException;
import com.nitditto.model.User;
import com.nitditto.payload.dto.UserDTO;
import com.nitditto.payload.response.AuthResponse;
import com.nitditto.repository.UserRepository;
import com.nitditto.service.AuthService;
import com.nitditto.service.CustomUserDetailsService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtProvider jwtProvider;
  private final CustomUserDetailsService customUserDetailsService;
  /*
   * 1. Check if email already exists
   * 2. Encode password using BCrypt
   * 3. Save user in database
   * 4. Generate JWT token
   * 5. Return token and user information
   */

  @Override
  @Transactional
  public AuthResponse signup(final UserDTO request) {
    final User existingUser = this.userRepository.findByEmail(request.getEmail());
    if (existingUser != null) {
      throw new BusinessException("Email already registered");
    }
    if (request.getRole() == UserRole.ROLE_SYSTEM_ADMIN) {
      throw new BusinessException("You cannot sign up system admin");
    }
    final User newUser = User.builder()
        .email(request.getEmail())
        .password(this.passwordEncoder.encode(request.getPassword()))
        .phone(request.getPhone())
        .role(request.getRole())
        .fullName(request.getFullName())
        .lastLogin(LocalDateTime.now())
        .build();

    final User savedUser = this.userRepository.save(newUser);

    final GrantedAuthority authority = new SimpleGrantedAuthority(savedUser.getRole().toString());
    final Authentication authentication = new UsernamePasswordAuthenticationToken(
        savedUser.getEmail(), savedUser.getPassword(), List.of(authority));

    final String jwt = this.jwtProvider.generateToken(
        authentication, savedUser.getId());

    final AuthResponse authResponse = new AuthResponse();
    authResponse.setJwt(jwt);
    authResponse.setUser(UserMapper.toDTO(savedUser));
    authResponse.setTitle("Welcome " + savedUser.getFullName());
    authResponse.setMessage("Registered Successfully");

    return authResponse;
  }

  /*
   * 1. Load user by email
   * 2. Compare password with BCrypt
   * 3. Update "lastLogin" time
   * 4. Generate Jwt token
   * 5. Return token and user information
   */
  @Override
  @Transactional
  public AuthResponse login(final String email, final String password) {
    final Authentication authentication = this.authenticate(email, password);

    final User user = this.userRepository.findByEmail(email);
    user.setLastLogin(LocalDateTime.now());
    this.userRepository.save(user);

    final String jwt = this.jwtProvider.generateToken(authentication, user.getId());
    final AuthResponse authResponse = new AuthResponse();
    authResponse.setJwt(jwt);
    authResponse.setUser(UserMapper.toDTO(user));
    authResponse.setTitle("Welcome back" + user.getFullName());
    authResponse.setMessage("Login Successfully");
    return authResponse;

  }

  private Authentication authenticate(final String email, final String password) {

    final UserDetails userDetails = this.customUserDetailsService.loadUserByUsername(email);

    if (!this.passwordEncoder.matches(password, userDetails.getPassword())) {
      throw new BusinessException("Invalid password");
    }
    return new UsernamePasswordAuthenticationToken(
        userDetails, null, userDetails.getAuthorities());
  }
}
