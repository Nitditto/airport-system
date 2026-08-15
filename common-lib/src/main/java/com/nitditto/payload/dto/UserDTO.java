package com.nitditto.payload.dto;

import com.nitditto.enums.UserRole;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {

  private Long id;
  private String email;
  private String password;
  private String fullName;
  private String phone;
  private UserRole role;
  private LocalDateTime lastLogin;
}
