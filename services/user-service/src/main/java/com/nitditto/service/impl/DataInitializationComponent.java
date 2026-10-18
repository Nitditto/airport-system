package com.nitditto.service.impl;

import com.nitditto.enums.UserRole;
import com.nitditto.model.User;
import com.nitditto.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seed tài khoản SYSTEM_ADMIN đầu tiên. Credential lấy từ biến môi trường
 * ADMIN_EMAIL / ADMIN_PASSWORD; thiếu một trong hai thì bỏ qua, không có giá trị mặc định.
 */
@Slf4j
@Component
public class DataInitializationComponent implements CommandLineRunner {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final String adminEmail;
  private final String adminPassword;

  public DataInitializationComponent(
      final UserRepository userRepository,
      final PasswordEncoder passwordEncoder,
      @Value("${app.admin.email:}") final String adminEmail,
      @Value("${app.admin.password:}") final String adminPassword) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.adminEmail = adminEmail;
    this.adminPassword = adminPassword;
  }

  @Override
  public void run(final String... args) {
    if (this.adminEmail.isBlank() || this.adminPassword.isBlank()) {
      log.info("ADMIN_EMAIL/ADMIN_PASSWORD not set, skipping admin seeding");
      return;
    }
    if (this.userRepository.existsByEmail(this.adminEmail)) {
      return;
    }
    final User adminUser = User.builder()
        .email(this.adminEmail)
        .password(this.passwordEncoder.encode(this.adminPassword))
        .fullName("System Admin")
        .role(UserRole.ROLE_SYSTEM_ADMIN)
        .build();
    this.userRepository.save(adminUser);
    log.info("Seeded system admin account {}", this.adminEmail);
  }
}
