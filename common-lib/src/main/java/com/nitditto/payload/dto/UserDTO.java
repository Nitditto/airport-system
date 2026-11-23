package com.nitditto.payload.dto;

import com.nitditto.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {

  private UUID id;
  @NotBlank(message = "Email is required")
  @Email(message = "Email is invalid")
  private String email;
  @NotBlank(message = "Password is required")
  private String password;
  @NotBlank(message = "Full name is required")
  private String fullName;
  private String phone;
  // Không bắt buộc khi đăng ký: server luôn gán ROLE_USER (AuthServiceImpl.signup).
  private UserRole role;
  private LocalDateTime lastLogin;
}
