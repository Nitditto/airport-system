# 07 — Security & Auth

> Quy tắc gốc: `CLAUDE.md` §10 (Security & Identity Propagation). Trang này giải thích chi tiết và ví dụ.
>
> Mô hình hiện tại: **JWT chỉ được verify ở `api-gateway`**. Gateway gắn danh tính đã xác thực vào header
> `X-User-Id` / `X-User-Email` / `X-User-Roles` rồi forward. Service nghiệp vụ **không** có Spring Security.
> Ngoại lệ duy nhất là `user-service` (phát hành JWT, có Spring Security riêng) — §7.2, §7.3 áp dụng cho nó.

## 7.1 — Danh tính chỉ lấy từ nguồn đã được xác thực

```
Client ─(Bearer JWT)─► api-gateway ── verify chữ ký + hạn ──► SET X-User-* (ghi đè giá trị client tự gửi)
                                   └─ route public / /auth/** ──► XOÁ mọi X-User-*
                                                               ─► service nghiệp vụ: tin X-User-*
```

```java
// ❌ SAI — danh tính đến từ dữ liệu client kiểm soát hoàn toàn
@PostMapping
public ResponseEntity<ApiResponse<AirlineResponse>> create(@RequestBody @Valid final AirlineRequest request) {
    return ...this.airlineService.create(request, request.getOwnerId());   // ownerId trong body
}

@GetMapping("/mine")
public ... getMine(@RequestParam final UUID userId) { ... }               // userId trong query
```

```java
// ✅ ĐÚNG — service nghiệp vụ (đứng sau gateway): đọc header gateway đã đặt
@PostMapping
public ResponseEntity<ApiResponse<AirlineResponse>> create(
        @RequestHeader("X-User-Id") final UUID userId,
        @RequestBody @Valid final AirlineRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(this.airlineService.create(request, userId)));
}
```

```java
// ✅ ĐÚNG — riêng user-service (có Spring Security): lấy từ SecurityContext
final String email = SecurityContextHolder.getContext().getAuthentication().getName();
```

**Vì sao header `X-User-Id` an toàn ở đây:** gateway luôn **`set` (ghi đè)** header sau khi verify JWT
và **xoá** nó ở route public, nên giá trị client tự gửi không bao giờ tới được service
(có test `GatewayJwtE2ETest` kiểm chứng). Điều kiện bắt buộc đi kèm:

- Port của service nghiệp vụ **không được publish ra ngoài** (`docker-compose.yml` chỉ publish gateway).
  Gọi thẳng vào service là bỏ qua toàn bộ lớp xác thực.
- Không thêm Spring Security / `JwtAuthenticationFilter` vào service nghiệp vụ — trùng lặp và dễ lệch logic
  với gateway.

**Phân quyền chia hai tầng:**

| Tầng | Câu hỏi | Ở đâu |
|---|---|---|
| Role | Ai được gọi route / method này? | `RouteConfig` của gateway |
| Ownership | Bản ghi này có thuộc người gọi không? | `*ServiceImpl`, so `X-User-Id` với `ownerId`/`userId` |

```java
// ✅ Ownership check ở tầng service
private void requireOwner(final Booking booking, final UUID requesterUserId) {
    if (!booking.getUserId().equals(requesterUserId)) {
        throw new BusinessException("You are not allowed to cancel this booking");
    }
}
```

**Gọi service khác thay mặt người dùng:** Feign không tự forward header — truyền tường minh
`@RequestHeader("X-User-Id") UUID userId` trong signature của client.

## 7.2 — Phân quyền theo route trong `user-service`

> Service nghiệp vụ khác phân quyền theo role ở gateway (`RouteConfig`), không có `SecurityFilterChain`.

```java
// ❌ SAI — bật security nhưng mở toàn bộ
http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
```

```java
// ✅ ĐÚNG — thứ tự rule quan trọng: KHỚP ĐẦU TIÊN THẮNG, rule hẹp đặt TRƯỚC rule rộng
@Bean
public SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/", "/auth/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/users").hasRole("SYSTEM_ADMIN")
            .anyRequest().authenticated()                      // luôn là dòng CUỐI
        )
        .addFilterBefore(new JwtAuthenticationFilter(this.jwtProvider),
            UsernamePasswordAuthenticationFilter.class);
    return http.build();
}
```

Nếu đặt `.anyRequest().authenticated()` lên trước, mọi rule sau nó **không bao giờ chạy**.

### Cạm bẫy tiền tố `ROLE_`

`hasRole("SYSTEM_ADMIN")` tự thêm tiền tố `ROLE_` khi so khớp, tức nó tìm authority
`"ROLE_SYSTEM_ADMIN"`. Enum `UserRole` của dự án đã **sẵn** có tiền tố đó
(`ROLE_SYSTEM_ADMIN`, `ROLE_USER`, `ROLE_AIRLINE_OWNER`), nên:

```java
hasRole("SYSTEM_ADMIN")          // ✅ ĐÚNG — khớp ROLE_SYSTEM_ADMIN
hasRole("ROLE_SYSTEM_ADMIN")     // ❌ SAI — đi tìm ROLE_ROLE_SYSTEM_ADMIN
hasAuthority("ROLE_SYSTEM_ADMIN")// ✅ cũng đúng — hasAuthority KHÔNG tự thêm tiền tố
```

## 7.3 — Dựng `Authentication` cho đúng

```java
// ❌ SAI — constructor 2 tham số LUÔN trả authorities RỖNG, bất kể truyền gì
final Authentication authentication =
        new UsernamePasswordAuthenticationToken(user.getEmail(), user.getPassword());
final String jwt = this.jwtProvider.generateToken(authentication, user.getId());
// → JWT sinh ra có claim "authorities" rỗng, user không có role nào
```

```java
// ✅ ĐÚNG — constructor 3 tham số
final GrantedAuthority authority = new SimpleGrantedAuthority(user.getRole().toString());
final Authentication authentication = new UsernamePasswordAuthenticationToken(
        user.getEmail(), user.getPassword(), List.of(authority));
```

Constructor 2 tham số của Spring Security được thiết kế cho token **chưa xác thực** (nó set
`authenticated = false` và authorities rỗng cố định). Dùng nhầm nó để sinh JWT là bug thật
đã xảy ra trong `AuthServiceImpl.signup()` — user vừa đăng ký không có role nào cho tới lần
login kế tiếp.

## 7.4 — Không bao giờ commit secret

```yaml
# ❌ SAI — đang có thật trong repo, chấp nhận được ở dev nhưng KHÔNG được lên production
spring:
  datasource:
    username: root
    password: 123456
```

Khi lên môi trường thật: chuyển sang biến môi trường (file `.env`, không commit).
**Tuyệt đối không commit** giá trị thật của DB password, JWT secret, key VNPay/Razorpay.

Ngoài ra: **không log dữ liệu nhạy cảm** — password, JWT, token, số thẻ.

## 7.5 — Không lộ chi tiết lỗi ra client

```java
// ❌ SAI — lộ stack trace / cấu trúc nội bộ cho người tấn công
@ExceptionHandler(Exception.class)
public ResponseEntity<ApiResponse<Void>> handle(final Exception ex) {
    return ResponseEntity.status(500).body(ApiResponse.error(ex.getMessage()));
}
```

```java
// ✅ ĐÚNG — log đầy đủ ở server, trả message chung chung ra client
@ExceptionHandler(Exception.class)
public ResponseEntity<ApiResponse<Void>> handleUnexpected(final Exception ex) {
    log.error("Unexpected error", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("Internal server error"));
}
```

Lỗi Feign cũng vậy: `FeignException.getMessage()` chứa URL nội bộ và body response của service đích.
Mỗi service có Feign client map `FeignException` → 404 / 503 / 502 với message chung (xem `CLAUDE.md` §8).

## Checklist security

- [ ] Service nghiệp vụ lấy danh tính qua `@RequestHeader("X-User-Id")`, **không** từ body/query
- [ ] Không thêm Spring Security / JWT filter vào service nghiệp vụ (chỉ `user-service` có)
- [ ] Route / endpoint mới đã khai báo trong `RouteConfig` với role đúng; endpoint public là danh sách tường minh
- [ ] Thao tác sửa/xoá bản ghi có ownership check ở `*ServiceImpl`
- [ ] Endpoint nội bộ (chỉ service khác gọi) không mở cho nhóm route customer
- [ ] Không publish port service nghiệp vụ ra ngoài
- [ ] (`user-service`) `.anyRequest().authenticated()` là dòng **cuối**; `hasRole("X")` không tự thêm `ROLE_`;
      `Authentication` dùng constructor **3 tham số** khi sinh JWT
- [ ] Không commit secret thật; không log password/token/secret cổng thanh toán
- [ ] Catch-all handler và Feign handler không trả `ex.getMessage()` ra client
