package com.nitditto;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * E2E cho api-gateway: request thật đi qua JWT filter + route + load balancer tới một
 * downstream stub. Side effect được kiểm chứng ở phía stub (có nhận được request không,
 * nhận header danh tính nào) vì gateway không có database.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "jwt.secret=" + GatewayJwtE2ETest.SECRET,
        "eureka.client.enabled=false"
    })
class GatewayJwtE2ETest {

  static final String SECRET = "e2e-test-secret-e2e-test-secret-e2e-test-secret";
  private static final String OTHER_SECRET = "another-secret-another-secret-another-secret-1234";

  private static final String ROLE_ADMIN = "ROLE_SYSTEM_ADMIN";
  private static final String ROLE_USER = "ROLE_USER";
  private static final String ROLE_OWNER = "ROLE_AIRLINE_OWNER";

  private static final List<StubRequest> RECEIVED = new CopyOnWriteArrayList<>();
  private static final HttpServer DOWNSTREAM = startDownstream();

  private final HttpClient client = HttpClient.newHttpClient();

  @Value("${local.server.port}")
  private int port;

  @DynamicPropertySource
  static void registerDownstream(final DynamicPropertyRegistry registry) {
    final String uri = "http://localhost:" + DOWNSTREAM.getAddress().getPort();
    for (final String service : List.of("user-service", "airline-core-service", "location-service")) {
      registry.add("spring.cloud.discovery.client.simple.instances." + service + "[0].uri",
          () -> uri);
    }
  }

  @AfterAll
  static void stopDownstream() {
    DOWNSTREAM.stop(0);
  }

  @BeforeEach
  void resetDownstream() {
    RECEIVED.clear();
  }

  @Nested
  @DisplayName("auth failures")
  class AuthFailures {

    @Test
    void missingToken_returns401_andNeverReachesDownstream() throws Exception {
      // Arrange + Act
      final HttpResponse<String> response = send("GET", "/api/airlines", null, Map.of(), null);

      // Assert
      assertThat(response.statusCode()).isEqualTo(401);
      assertThat(RECEIVED).isEmpty();
    }

    @Test
    void malformedToken_returns401() throws Exception {
      final HttpResponse<String> response = send("GET", "/api/airlines", "abc.def.ghi", Map.of(), null);

      assertThat(response.statusCode()).isEqualTo(401);
      assertThat(RECEIVED).isEmpty();
    }

    @Test
    void expiredToken_returns401() throws Exception {
      final String token = token(UUID.randomUUID(), ROLE_ADMIN, SECRET, -60_000);

      final HttpResponse<String> response = send("GET", "/api/airlines", token, Map.of(), null);

      assertThat(response.statusCode()).isEqualTo(401);
      assertThat(RECEIVED).isEmpty();
    }

    @Test
    void tokenSignedWithOtherSecret_returns401() throws Exception {
      final String token = token(UUID.randomUUID(), ROLE_ADMIN, OTHER_SECRET, 60_000);

      final HttpResponse<String> response = send("GET", "/api/airlines", token, Map.of(), null);

      assertThat(response.statusCode()).isEqualTo(401);
      assertThat(RECEIVED).isEmpty();
    }

    @Test
    void tokenWithoutUserIdClaim_returns401_insteadOfForwardingNullIdentity() throws Exception {
      final long now = System.currentTimeMillis();
      final String token = Jwts.builder()
          .issuedAt(new Date(now - 1_000))
          .expiration(new Date(now + 60_000))
          .claim("email", "e2e@test.com")
          .claim("authorities", ROLE_ADMIN)
          .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
          .compact();

      final HttpResponse<String> response = send("GET", "/api/airlines", token, Map.of(), null);

      assertThat(response.statusCode()).isEqualTo(401);
      assertThat(RECEIVED).isEmpty();
    }

    @Test
    void tokenWithoutExpiration_returns401() throws Exception {
      final String token = Jwts.builder()
          .claim("email", "e2e@test.com")
          .claim("authorities", ROLE_ADMIN)
          .claim("userId", UUID.randomUUID().toString())
          .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
          .compact();

      final HttpResponse<String> response = send("GET", "/api/airlines", token, Map.of(), null);

      assertThat(response.statusCode()).isEqualTo(401);
      assertThat(RECEIVED).isEmpty();
    }

    @Test
    void tokenWithArrayAuthoritiesClaim_returns401_notServerError() throws Exception {
      final String token = Jwts.builder()
          .expiration(new Date(System.currentTimeMillis() + 60_000))
          .claim("email", "e2e@test.com")
          .claim("authorities", List.of(ROLE_ADMIN))
          .claim("userId", UUID.randomUUID().toString())
          .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
          .compact();

      final HttpResponse<String> response = send("GET", "/api/airlines", token, Map.of(), null);

      assertThat(response.statusCode()).isEqualTo(401);
      assertThat(RECEIVED).isEmpty();
    }

    @Test
    void tokenWithNonUuidUserId_returns401() throws Exception {
      final String token = Jwts.builder()
          .expiration(new Date(System.currentTimeMillis() + 60_000))
          .claim("email", "e2e@test.com")
          .claim("authorities", ROLE_ADMIN)
          .claim("userId", "not-a-uuid")
          .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
          .compact();

      final HttpResponse<String> response = send("GET", "/api/airlines", token, Map.of(), null);

      assertThat(response.statusCode()).isEqualTo(401);
      assertThat(RECEIVED).isEmpty();
    }

    @Test
    void routeToServiceWithoutInstances_withoutToken_returns401NotServerError() throws Exception {
      // booking-service không được đăng ký trong discovery của test này
      final HttpResponse<String> response = send("GET", "/api/bookings/anything", null, Map.of(), null);

      assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void nonBearerAuthorizationHeader_returns401() throws Exception {
      final HttpResponse<String> response = send("GET", "/api/airlines", null,
          Map.of("Authorization", "Basic dXNlcjpwYXNz"), null);

      assertThat(response.statusCode()).isEqualTo(401);
    }
  }

  @Nested
  @DisplayName("forbidden roles")
  class ForbiddenRoles {

    @Test
    void userRole_cannotListAllAirlines() throws Exception {
      final String token = token(UUID.randomUUID(), ROLE_USER, SECRET, 60_000);

      final HttpResponse<String> response = send("GET", "/api/airlines", token, Map.of(), null);

      assertThat(response.statusCode()).isEqualTo(403);
      assertThat(RECEIVED).isEmpty();
    }

    @Test
    void userRole_cannotCreateCity_evenThoughGenericLocationRouteMatches() throws Exception {
      final String token = token(UUID.randomUUID(), ROLE_USER, SECRET, 60_000);

      final HttpResponse<String> response = send("POST", "/api/cities", token, Map.of(), "{}");

      assertThat(response.statusCode()).isEqualTo(403);
      assertThat(RECEIVED).isEmpty();
    }

    @Test
    void userRole_cannotCreateAirport() throws Exception {
      final String token = token(UUID.randomUUID(), ROLE_USER, SECRET, 60_000);

      final HttpResponse<String> response = send("POST", "/api/airports", token, Map.of(), "{}");

      assertThat(response.statusCode()).isEqualTo(403);
      assertThat(RECEIVED).isEmpty();
    }

    @Test
    void spoofedRolesHeader_doesNotGrantAdminAccess() throws Exception {
      final String token = token(UUID.randomUUID(), ROLE_USER, SECRET, 60_000);

      final HttpResponse<String> response = send("GET", "/api/airlines", token,
          Map.of("X-User-Roles", ROLE_ADMIN), null);

      assertThat(response.statusCode()).isEqualTo(403);
      assertThat(RECEIVED).isEmpty();
    }

    @Test
    void roleWithAdminAsSubstring_isNotTreatedAsAdmin() throws Exception {
      final String token = token(UUID.randomUUID(), ROLE_ADMIN + "_READONLY", SECRET, 60_000);

      final HttpResponse<String> response = send("GET", "/api/airlines", token, Map.of(), null);

      assertThat(response.statusCode()).isEqualTo(403);
    }
  }

  @Nested
  @DisplayName("happy path")
  class HappyPath {

    @Test
    void admin_listsAirlines_andDownstreamReceivesIdentityFromToken() throws Exception {
      // Arrange
      final UUID adminId = UUID.randomUUID();
      final String token = token(adminId, ROLE_ADMIN, SECRET, 60_000);

      // Act
      final HttpResponse<String> response = send("GET", "/api/airlines", token, Map.of(), null);

      // Assert
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(RECEIVED).hasSize(1);
      final StubRequest forwarded = RECEIVED.get(0);
      assertThat(forwarded.path()).isEqualTo("/api/airlines");
      assertThat(forwarded.headers("X-User-Id")).containsExactly(adminId.toString());
      assertThat(forwarded.headers("X-User-Roles")).containsExactly(ROLE_ADMIN);
    }

    @Test
    void admin_createsCity_requestIsForwardedToLocationService() throws Exception {
      final String token = token(UUID.randomUUID(), ROLE_ADMIN, SECRET, 60_000);

      final HttpResponse<String> response = send("POST", "/api/cities", token, Map.of(), "{}");

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(RECEIVED).hasSize(1);
      assertThat(RECEIVED.get(0).method()).isEqualTo("POST");
    }

    @Test
    void owner_getsOwnAirline_andSpoofedUserIdHeaderIsOverwritten() throws Exception {
      // Arrange
      final UUID ownerId = UUID.randomUUID();
      final String token = token(ownerId, ROLE_OWNER, SECRET, 60_000);

      // Act
      final HttpResponse<String> response = send("GET", "/api/airlines/admin", token,
          Map.of("X-User-Id", "11111111-1111-1111-1111-111111111111"), null);

      // Assert: downstream chỉ thấy đúng một giá trị — id lấy từ token
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(RECEIVED).hasSize(1);
      assertThat(RECEIVED.get(0).headers("X-User-Id")).containsExactly(ownerId.toString());
    }
  }

  @Nested
  @DisplayName("write method rules")
  class WriteRules {

    @Test
    void userRole_cannotUpdateOrDeleteLocationData() throws Exception {
      final String token = token(UUID.randomUUID(), ROLE_USER, SECRET, 60_000);

      assertThat(send("PUT", "/api/cities/" + UUID.randomUUID(), token, Map.of(), "{}").statusCode())
          .isEqualTo(403);
      assertThat(send("DELETE", "/api/airports/" + UUID.randomUUID(), token, Map.of(), null).statusCode())
          .isEqualTo(403);
      assertThat(RECEIVED).isEmpty();
    }

    @Test
    void userRole_cannotWriteAircraftsOrAirlines() throws Exception {
      final String token = token(UUID.randomUUID(), ROLE_USER, SECRET, 60_000);

      assertThat(send("POST", "/api/aircrafts", token, Map.of(), "{}").statusCode()).isEqualTo(403);
      assertThat(send("DELETE", "/api/airlines/" + UUID.randomUUID(), token, Map.of(), null).statusCode())
          .isEqualTo(403);
      assertThat(RECEIVED).isEmpty();
    }

    @Test
    void userRole_canReadWithJwtOnly() throws Exception {
      final String token = token(UUID.randomUUID(), ROLE_USER, SECRET, 60_000);

      assertThat(send("GET", "/api/cities", token, Map.of(), null).statusCode()).isEqualTo(200);
      assertThat(send("GET", "/api/airlines/" + UUID.randomUUID(), token, Map.of(), null).statusCode())
          .isEqualTo(200);
    }

    @Test
    void userRole_canCallBatchByIds_becauseItIsARead() throws Exception {
      final String token = token(UUID.randomUUID(), ROLE_USER, SECRET, 60_000);

      assertThat(send("POST", "/api/airlines/batch-by-ids", token, Map.of(), "[]").statusCode()).isEqualTo(200);
      assertThat(send("POST", "/api/aircrafts/batch-by-ids", token, Map.of(), "[]").statusCode()).isEqualTo(200);
    }

    @Test
    void batchByIdsExemption_doesNotApplyToNestedOrOtherPaths() throws Exception {
      final String token = token(UUID.randomUUID(), ROLE_USER, SECRET, 60_000);

      assertThat(send("POST", "/api/airlines/x/batch-by-ids", token, Map.of(), "[]").statusCode())
          .isEqualTo(403);
      assertThat(send("PUT", "/api/airlines/batch-by-ids", token, Map.of(), "[]").statusCode())
          .isEqualTo(403);
    }

    @Test
    void owner_canWriteAircrafts_butNotLocationNorAirlineListing() throws Exception {
      final String token = token(UUID.randomUUID(), ROLE_OWNER, SECRET, 60_000);

      assertThat(send("POST", "/api/aircrafts", token, Map.of(), "{}").statusCode()).isEqualTo(200);
      assertThat(send("POST", "/api/cities", token, Map.of(), "{}").statusCode()).isEqualTo(403);
      assertThat(send("GET", "/api/airlines", token, Map.of(), null).statusCode()).isEqualTo(403);
    }

    @Test
    void admin_canDeleteAircraftsAndUpdateCities() throws Exception {
      final String token = token(UUID.randomUUID(), ROLE_ADMIN, SECRET, 60_000);

      assertThat(send("DELETE", "/api/aircrafts/" + UUID.randomUUID(), token, Map.of(), null).statusCode())
          .isEqualTo(200);
      assertThat(send("PUT", "/api/cities/" + UUID.randomUUID(), token, Map.of(), "{}").statusCode())
          .isEqualTo(200);
    }
  }

  @Nested
  @DisplayName("public routes")
  class PublicRoutes {

    @Test
    void authRoute_isForwardedWithoutToken_andStripsClientIdentityHeaders() throws Exception {
      final HttpResponse<String> response = send("POST", "/auth/login", null,
          Map.of("X-User-Id", UUID.randomUUID().toString(), "X-User-Roles", ROLE_ADMIN), "{}");

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(RECEIVED).hasSize(1);
      final StubRequest forwarded = RECEIVED.get(0);
      assertThat(forwarded.headers("X-User-Id")).isEmpty();
      assertThat(forwarded.headers("X-User-Roles")).isEmpty();
      assertThat(forwarded.headers("X-User-Email")).isEmpty();
    }
  }

  private HttpResponse<String> send(final String method, final String path, final String token,
      final Map<String, String> headers, final String body) throws Exception {
    final HttpRequest.Builder builder = HttpRequest.newBuilder(
        URI.create("http://localhost:" + this.port + path));
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    headers.forEach(builder::header);
    if (body != null) {
      builder.header("Content-Type", "application/json");
    }
    builder.method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body));
    return this.client.send(builder.build(), BodyHandlers.ofString());
  }

  private static String token(final UUID userId, final String authorities, final String secret,
      final long ttlMillis) {
    final long now = System.currentTimeMillis();
    return Jwts.builder()
        .issuedAt(new Date(now - 120_000))
        .expiration(new Date(now + ttlMillis))
        .claim("email", "e2e@test.com")
        .claim("authorities", authorities)
        .claim("userId", userId.toString())
        .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
        .compact();
  }

  private static HttpServer startDownstream() {
    try {
      final HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext("/", GatewayJwtE2ETest::record);
      server.start();
      return server;
    } catch (final IOException ex) {
      throw new IllegalStateException("Cannot start downstream stub", ex);
    }
  }

  private static void record(final HttpExchange exchange) throws IOException {
    final Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    exchange.getRequestHeaders().forEach((name, values) -> headers.put(name, new ArrayList<>(values)));
    RECEIVED.add(new StubRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), headers));
    exchange.getRequestBody().readAllBytes();

    final byte[] body = "{\"success\":true}".getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }

  private record StubRequest(String method, String path, Map<String, List<String>> headerMap) {

    List<String> headers(final String name) {
      return this.headerMap.getOrDefault(name, List.of());
    }
  }
}
