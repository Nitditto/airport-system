# 07 — Security & Auth

> Hiện thực: `CLAUDE.md` mục "Known Gaps / Nhóm B mục 6".
> Hiện chỉ `user-service` có Spring Security + JWT. Trang này là chuẩn để áp dụng khi các
> service khác bắt đầu cần phân quyền.

## 7.1 — Không bao giờ tin dữ liệu client gửi để xác định danh tính

Đây là lỗ hổng thật đã tồn tại trong repo và mới được vá.

```java
// ❌ SAI — client tự khai "tôi là ai"
@GetMapping("/profile")
public ResponseEntity<ApiResponse<UserDTO>> getUserProfile(
        @RequestHeader("X-User-Email") final String email) {
    return ResponseEntity.ok(ApiResponse.success(this.userService.getUserByEmail(email)));
}
```

**Vì sao nguy hiểm:** header do client gửi, server không xác thực gì cả. Bất kỳ user nào
đã đăng nhập chỉ cần đổi `X-User-Email` thành email người khác là đọc được profile của họ.
JWT hợp lệ vẫn được gửi kèm, nhưng code **không dùng tới nó** để xác định danh tính.

```java
// ✅ ĐÚNG — lấy danh tính từ token đã được server verify chữ ký
@GetMapping("/profile")
public ResponseEntity<ApiResponse<UserDTO>> getUserProfile() {
    final String email = SecurityContextHolder.getContext().getAuthentication().getName();
    return ResponseEntity.ok(ApiResponse.success(this.userService.getUserByEmail(email)));
}
```

`JwtAuthenticationFilter` dựng `Authentication` với `principal = email` lấy từ claim của
token đã verify, nên `getAuthentication().getName()` trả về đúng người gọi và **không thể
giả mạo**.

**Quy tắc tổng quát:** mọi thông tin dùng để quyết định *"được phép làm gì"* hoặc *"là ai"*
phải đến từ `SecurityContextHolder`, không bao giờ từ header/body/query do client kiểm soát.

> **Lưu ý cho service khác:** `airline-core-service` hiện đang nhận `@RequestHeader("X-User-Id")`
> để xác định chủ sở hữu hãng bay. Pattern này **chỉ an toàn khi đứng sau API Gateway**
> tự đặt header đó sau khi verify JWT (và gateway phải strip header client gửi lên). Cho tới
> khi module `cloud/` có Gateway thật, đây vẫn là lỗ hổng cùng loại — đừng nhân bản sang
> service mới.

## 7.2 — Phân quyền theo route

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

Khi lên môi trường thật: chuyển sang biến môi trường hoặc config server.
**Tuyệt đối không commit** giá trị thật của DB password hay JWT secret.

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

## Checklist security

- [ ] Danh tính lấy từ `SecurityContextHolder`, **không** từ header client gửi
- [ ] `.anyRequest().authenticated()` là dòng **cuối cùng** trong chuỗi rule
- [ ] `hasRole("X")` — không tự thêm tiền tố `ROLE_` vào chuỗi truyền vào
- [ ] `Authentication` dùng constructor **3 tham số** khi sinh JWT
- [ ] Endpoint trả dữ liệu của nhiều người dùng đã giới hạn theo role
- [ ] Không commit secret thật; không log password/token
- [ ] Catch-all handler không trả `ex.getMessage()` ra client
