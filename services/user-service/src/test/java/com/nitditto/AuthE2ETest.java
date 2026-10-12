package com.nitditto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nitditto.config.JwtProvider;
import com.nitditto.enums.UserRole;
import com.nitditto.model.User;
import com.nitditto.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * E2E cho luồng auth của user-service: HTTP -> Security filter -> service -> MySQL.
 * Dùng schema riêng {@code airline_user_test} (tự tạo, create-drop) để không đụng dữ liệu dev;
 * mỗi test bắt đầu và kết thúc với bảng users rỗng.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3306/airline_user_test?createDatabaseIfNotExist=true",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "eureka.client.enabled=false",
    "jwt.secret=" + AuthE2ETest.SECRET
})
@AutoConfigureMockMvc
class AuthE2ETest {

  static final String SECRET = "e2e-test-secret-e2e-test-secret-e2e-test-secret";
  private static final String PASSWORD = "Passw0rd!";

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private UserRepository userRepository;
  @Autowired
  private PasswordEncoder passwordEncoder;
  @Autowired
  private JwtProvider jwtProvider;

  @BeforeEach
  void cleanBefore() {
    this.userRepository.deleteAll();
  }

  @AfterEach
  void cleanAfter() {
    this.userRepository.deleteAll();
  }

  @Nested
  @DisplayName("lifecycle")
  class Lifecycle {

    @Test
    void signup_persistsUserWithHashedPassword_andReturnsJwtThatAuthenticatesProfile() throws Exception {
      // Arrange
      final String body = signupBody("alice@test.com", PASSWORD, "Alice", UserRole.ROLE_USER);

      // Act
      final String jwt = jsonField(postJson("/auth/signup", body)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.jwt").isNotEmpty())
          .andExpect(jsonPath("$.data.user.email").value("alice@test.com")), "$.data.jwt");

      // Assert — DB
      final User saved = userRepository.findByEmail("alice@test.com");
      assertThat(saved).isNotNull();
      assertThat(saved.getRole()).isEqualTo(UserRole.ROLE_USER);
      assertThat(saved.getPassword()).isNotEqualTo(PASSWORD);
      assertThat(passwordEncoder.matches(PASSWORD, saved.getPassword())).isTrue();

      // Assert — token dùng được ở endpoint cần xác thực
      mockMvc.perform(get("/api/users/profile").header("Authorization", "Bearer " + jwt))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.email").value("alice@test.com"));
    }

    @Test
    void login_returnsJwt_andRefreshesLastLogin() throws Exception {
      // Arrange
      final LocalDateTime before = LocalDateTime.of(2020, 1, 1, 0, 0);
      saveUser("bob@test.com", UserRole.ROLE_USER, before);

      // Act
      postJson("/auth/login", loginBody("bob@test.com", PASSWORD))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.jwt").isNotEmpty());

      // Assert
      assertThat(userRepository.findByEmail("bob@test.com").getLastLogin()).isAfter(before);
    }
  }

  @Nested
  @DisplayName("auth failures")
  class AuthFailures {

    @Test
    void profile_withoutToken_returns401() throws Exception {
      mockMvc.perform(get("/api/users/profile")).andExpect(status().isUnauthorized());
    }

    @Test
    void profile_withExpiredToken_returns401() throws Exception {
      final String token = rawToken("carol@test.com", UserRole.ROLE_USER, SECRET, -60_000);

      mockMvc.perform(get("/api/users/profile").header("Authorization", "Bearer " + token))
          .andExpect(status().isUnauthorized());
    }

    @Test
    void profile_withTokenSignedByOtherSecret_returns401() throws Exception {
      final String token = rawToken("carol@test.com", UserRole.ROLE_USER,
          "another-secret-another-secret-another-secret-1234", 60_000);

      mockMvc.perform(get("/api/users/profile").header("Authorization", "Bearer " + token))
          .andExpect(status().isUnauthorized());
    }

    @Test
    void listUsers_withUserRole_returns403() throws Exception {
      final User user = saveUser("dave@test.com", UserRole.ROLE_USER, null);

      mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + tokenFor(user)))
          .andExpect(status().isForbidden());
    }

    @Test
    void listUsers_withAdminRole_returns200() throws Exception {
      final User admin = saveUser("admin@test.com", UserRole.ROLE_SYSTEM_ADMIN, null);

      mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + tokenFor(admin)))
          .andExpect(status().isOk());
    }
  }

  @Nested
  @DisplayName("validation (400)")
  class Validation {

    @Test
    void signup_withMissingEmail_returns400_andPersistsNothing() throws Exception {
      postJson("/auth/signup", "{\"password\":\"%s\",\"fullName\":\"No Email\",\"role\":\"ROLE_USER\"}"
          .formatted(PASSWORD))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success").value(false));

      assertThat(userRepository.count()).isZero();
    }

    @Test
    void signup_withInvalidEmailFormat_returns400() throws Exception {
      postJson("/auth/signup", signupBody("not-an-email", PASSWORD, "Bad Email", UserRole.ROLE_USER))
          .andExpect(status().isBadRequest());

      assertThat(userRepository.count()).isZero();
    }

    @Test
    void signup_withBlankPassword_returns400() throws Exception {
      postJson("/auth/signup", signupBody("erin@test.com", " ", "Erin", UserRole.ROLE_USER))
          .andExpect(status().isBadRequest());

      assertThat(userRepository.findByEmail("erin@test.com")).isNull();
    }

    @Test
    void signup_withMissingRole_returns400() throws Exception {
      postJson("/auth/signup",
          "{\"email\":\"frank@test.com\",\"password\":\"%s\",\"fullName\":\"Frank\"}".formatted(PASSWORD))
          .andExpect(status().isBadRequest());

      assertThat(userRepository.findByEmail("frank@test.com")).isNull();
    }

    @Test
    void login_withBlankCredentials_returns400() throws Exception {
      postJson("/auth/login", "{}").andExpect(status().isBadRequest());
    }

    @Test
    void signup_withMalformedJson_returns400() throws Exception {
      postJson("/auth/signup", "{not json").andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("business errors")
  class BusinessErrors {

    @Test
    void signup_withDuplicateEmail_returns400_andKeepsSingleRow() throws Exception {
      saveUser("grace@test.com", UserRole.ROLE_USER, null);

      postJson("/auth/signup", signupBody("grace@test.com", PASSWORD, "Grace Again", UserRole.ROLE_USER))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("Email already registered"));

      assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void signup_asSystemAdmin_returns400_andPersistsNothing() throws Exception {
      postJson("/auth/signup", signupBody("root@test.com", PASSWORD, "Root", UserRole.ROLE_SYSTEM_ADMIN))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("You cannot sign up system admin"));

      assertThat(userRepository.findByEmail("root@test.com")).isNull();
    }

    @Test
    void login_withWrongPassword_returns400() throws Exception {
      saveUser("heidi@test.com", UserRole.ROLE_USER, null);

      postJson("/auth/login", loginBody("heidi@test.com", "wrong-password"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("Invalid password"));
    }

    @Test
    void login_withUnknownEmail_returns404() throws Exception {
      postJson("/auth/login", loginBody("ghost@test.com", PASSWORD))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getUserById_withUnknownId_returns404() throws Exception {
      final User admin = saveUser("admin@test.com", UserRole.ROLE_SYSTEM_ADMIN, null);

      mockMvc.perform(get("/api/users/{id}", UUID.randomUUID())
              .header("Authorization", "Bearer " + tokenFor(admin)))
          .andExpect(status().isNotFound());
    }
  }

  private ResultActions postJson(final String path, final String body) throws Exception {
    return this.mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private static String jsonField(final ResultActions actions, final String path) throws Exception {
    final String content = actions.andReturn().getResponse().getContentAsString();
    return com.jayway.jsonpath.JsonPath.read(content, path);
  }

  private static String signupBody(final String email, final String password, final String fullName,
      final UserRole role) {
    return "{\"email\":\"%s\",\"password\":\"%s\",\"fullName\":\"%s\",\"role\":\"%s\"}"
        .formatted(email, password, fullName, role);
  }

  private static String loginBody(final String email, final String password) {
    return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
  }

  private User saveUser(final String email, final UserRole role, final LocalDateTime lastLogin) {
    return this.userRepository.save(User.builder()
        .email(email)
        .password(this.passwordEncoder.encode(PASSWORD))
        .fullName("E2E " + email)
        .role(role)
        .lastLogin(lastLogin)
        .build());
  }

  private String tokenFor(final User user) {
    return this.jwtProvider.generateToken(
        new UsernamePasswordAuthenticationToken(user.getEmail(), null,
            List.of(new SimpleGrantedAuthority(user.getRole().toString()))),
        user.getId());
  }

  private String rawToken(final String email, final UserRole role, final String secret,
      final long ttlMillis) {
    final long now = System.currentTimeMillis();
    return Jwts.builder()
        .issuedAt(new Date(now - 120_000))
        .expiration(new Date(now + ttlMillis))
        .claim("email", email)
        .claim("authorities", role.toString())
        .claim("userId", UUID.randomUUID().toString())
        .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
        .compact();
  }
}
