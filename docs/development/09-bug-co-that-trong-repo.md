# 09 — Bảo tàng bug có thật trong repo

Trang này ghi lại các bug **thật đã xảy ra trong chính dự án này**, kèm cơ chế gây lỗi và
cách vá. Đây không phải ví dụ giả định — mỗi mục đều tra được trong `git log`.

Mục đích: khi code service mới, bạn nhận ra pattern nguy hiểm **trước** khi lặp lại nó.

---

## Bug 1 — NPE do unboxing `Double` từ request

**Nơi xảy ra:** `pricing-service`, `FareMapper.toEntity()`
**Commit vá:** `fix(pricing): guard FareMapper against null taxesAndFees/airlineFees`

```java
// ❌ Code gây lỗi
Double calculatedPrice = request.getCurrentPrice();
if (calculatedPrice == null) {
    calculatedPrice = request.getBaseFare() + request.getTaxesAndFees() + request.getAirlineFees();
}
```

**Cơ chế:** `taxesAndFees` và `airlineFees` **không** có `@NotNull` trong `FareRequest`, tức
client được phép không gửi. Phép `+` giữa các `Double` buộc Java tự unbox sang `double`;
gặp `null` là ném `NullPointerException` ngay.

**Kích hoạt:** gửi `POST /api/fares` không có `currentPrice` **và** thiếu một trong hai field kia.

```java
// ✅ Bản vá
Double calculatedPrice = request.getCurrentPrice();
if (calculatedPrice == null) {
    final double taxes = request.getTaxesAndFees() != null ? request.getTaxesAndFees() : 0.0;
    final double fees = request.getAirlineFees() != null ? request.getAirlineFees() : 0.0;
    calculatedPrice = request.getBaseFare() + taxes + fees;
}
```

**Bài học:** field không có `@NotNull` là một **cam kết** rằng mọi chỗ dùng nó đều xử lý được
`null`. Kiểm tra lại toàn bộ phép tính số học trên kiểu bọc đến từ request.

---

## Bug 2 — NPE vì `@Builder` bỏ qua default của field

**Nơi xảy ra:** `airline-core-service`, `Aircraft.getTotalSeats()`
**Commit vá:** `fix(airline-core): guard Aircraft.getTotalSeats against null seat fields`

```java
// Entity — trông như đã có default an toàn
@Column(name = "economy_seats")
private Integer economySeats = 0;

@Column(name = "premium_economy_seats")
private Integer premiumEconomySeats = 0;

// ❌ Code gây lỗi
public Integer getTotalSeats() {
    return this.economySeats + this.premiumEconomySeats + this.businessSeats + this.firstClassSeats;
}
```

**Cơ chế — đây là cái bẫy tinh vi nhất trong repo:** field initializer `= 0` **chỉ chạy khi
gọi `new Aircraft()`**. Khi entity được dựng bằng Lombok `@Builder` (đúng cách
`AircraftMapper.toEntity()` đang làm), field nào không set trong builder sẽ là `null`, **không
phải `0`**. Default trông như có mà thực ra không có.

**Kích hoạt:** tạo aircraft chỉ điền `economySeats`, bỏ trống 3 hạng ghế còn lại.

```java
// ✅ Bản vá
public Integer getTotalSeats() {
    final int economy = this.economySeats != null ? this.economySeats : 0;
    final int premiumEconomy = this.premiumEconomySeats != null ? this.premiumEconomySeats : 0;
    final int business = this.businessSeats != null ? this.businessSeats : 0;
    final int firstClass = this.firstClassSeats != null ? this.firstClassSeats : 0;
    return economy + premiumEconomy + business + firstClass;
}
```

**Bài học:** trong dự án này mọi entity đều có `@Builder`, nên **không được tin field
initializer** làm cơ chế bảo đảm giá trị. Muốn default thật thì dùng `@Builder.Default`, hoặc
null-safe ở nơi đọc. `seat-service` hiện có `displayOrder = 0`, `isActive = true`,
`isBookable = true` — đúng ba field dính bẫy này.

---

## Bug 3 — Tin header do client gửi để xác định danh tính

**Nơi xảy ra:** `user-service`, `UserController.getUserProfile()`
**Commit vá:** `fix(user): stop trusting X-User-Email header and fix signup authorities`

```java
// ❌ Code gây lỗi
@GetMapping("/profile")
public ResponseEntity<ApiResponse<UserDTO>> getUserProfile(
        @RequestHeader("X-User-Email") final String email) {
    return ResponseEntity.ok(ApiResponse.success(this.userService.getUserByEmail(email)));
}
```

**Cơ chế:** header do client kiểm soát hoàn toàn. Sau khi bật `JwtAuthenticationFilter`,
endpoint đã yêu cầu đăng nhập — nhưng **không kiểm tra** danh tính trong header có khớp
người trong JWT hay không. Bất kỳ user đã đăng nhập nào đổi header là đọc được profile người khác.

**Kích hoạt:** đăng nhập bằng tài khoản A, gọi `/api/users/profile` với `X-User-Email` là email của B.

```java
// ✅ Bản vá
@GetMapping("/profile")
public ResponseEntity<ApiResponse<UserDTO>> getUserProfile() {
    final String email = SecurityContextHolder.getContext().getAuthentication().getName();
    return ResponseEntity.ok(ApiResponse.success(this.userService.getUserByEmail(email)));
}
```

**Bài học:** "endpoint đã yêu cầu đăng nhập" **không** đồng nghĩa "endpoint đã an toàn".
Authentication (anh là ai) khác Authorization (anh được xem gì). Xem [07-security.md](07-security.md).

> Cùng đợt vá này, `GET /api/users` cũng bị siết lại chỉ cho `ROLE_SYSTEM_ADMIN` — trước đó
> nó trả `phone`/`role`/`lastLogin` của **mọi user** cho bất kỳ ai đã đăng nhập, và còn nhận
> một tham số `X-User-Email` **không hề được dùng** trong thân hàm.

---

## Bug 4 — Constructor `Authentication` sai làm JWT mất role

**Nơi xảy ra:** `user-service`, `AuthServiceImpl.signup()`

```java
// ❌ Code gây lỗi
final Authentication authentication =
        new UsernamePasswordAuthenticationToken(savedUser.getEmail(), savedUser.getPassword());
final String jwt = this.jwtProvider.generateToken(authentication, savedUser.getId());
```

**Cơ chế:** constructor **2 tham số** của `UsernamePasswordAuthenticationToken` được thiết kế
cho token *chưa xác thực* — nó luôn set `authenticated = false` và `getAuthorities()` trả về
**rỗng cố định**, bất kể truyền gì. `JwtProvider.generateToken()` đọc `auth.getAuthorities()`
để sinh claim, nên JWT lúc đăng ký có `"authorities": ""`.

**Triệu chứng:** user vừa signup **không có role nào** cho tới lần login kế tiếp — vì
`login()` đi qua đường khác (`CustomUserDetailsService`) và dựng authorities đúng.

```java
// ✅ Bản vá — dùng constructor 3 tham số, giống hệt cách login() đang làm
final GrantedAuthority authority = new SimpleGrantedAuthority(savedUser.getRole().toString());
final Authentication authentication = new UsernamePasswordAuthenticationToken(
        savedUser.getEmail(), savedUser.getPassword(), List.of(authority));
```

**Bài học:** bug này không ném exception, không log gì, và luồng đăng ký vẫn trả `200`. Chỉ
lộ khi decode JWT ra xem, hoặc khi user mới bị `403` ở đâu đó. **Verify bằng cách nhìn vào
kết quả thật**, đừng tin "chạy không lỗi là đúng".

---

## Bug 5 — `@Query` trỏ vào property không còn tồn tại

**Nơi xảy ra:** `flight-ops-service`, `FlightInstanceRepository` và `FlightScheduleRepository`

```java
// ❌ Code gây lỗi — sau khi gỡ @ManyToOne, "fi.flight" không còn
@Query("select fi from FlightInstance fi where fi.flight.id = :flightId")
List<FlightInstance> findByFlightId(@Param("flightId") UUID flightId);

// ❌ Derived query đi qua nested property cũng chết cùng lý do
List<FlightSchedule> findByFlightAirlineId(Long airlineId);
```

```java
// ✅ Bản vá
@Query("select fi from FlightInstance fi where fi.flightId = :flightId")
List<FlightInstance> findByFlightId(@Param("flightId") UUID flightId);

// Derived query qua nested property → tách thành 2 bước batch
final List<UUID> flightIds = this.flightRepository.findIdsByAirlineId(airlineId);
final List<FlightSchedule> schedules = this.flightScheduleRepository.findByFlightIdIn(flightIds);
```

**Bài học:** JPQL trong `@Query` và derived query theo tên method **không được compiler kiểm
tra** — cả hai compile sạch và chỉ chết lúc Hibernate khởi động. Sau mỗi lần đổi tên/xoá field
entity, phải **chạy thật**, không chỉ build.

---

## Bug 6 — Schema cũ còn sót gây lỗi DDL

**Nơi xảy ra:** database `airline_pricing` khi thêm `@Index` ở Nhóm C

**Triệu chứng:** thêm một `@Index` mới thì Hibernate báo lỗi DDL lúc khởi động.

**Nguyên nhân:** `fare_rules.fare_id` vẫn là `bigint` với FK constraint thật trỏ vào một bảng
`fare` (số ít) mồ côi, còn sót từ trước lần đổi tên bảng.

**Cơ chế:** `ddl-auto: update` **chỉ thêm** bảng/cột mới. Nó **không** đổi kiểu cột đã tồn
tại, **không** đổi tên bảng, **không** xoá bảng cũ. Việc chuyển `Long` sang `UUID` vì thế
không có đường migrate tự động.

**Cách xử lý:** drop và tạo lại database dev.

```sql
DROP DATABASE airline_pricing;
CREATE DATABASE airline_pricing;
```

**Bài học:** với `ddl-auto: update`, database dev tích tụ rác theo thời gian và có thể lệch
hẳn khỏi entity. Khi gặp lỗi DDL khó hiểu, kiểm tra schema thật trước khi nghi ngờ code:

```sql
SHOW CREATE TABLE fare_rules;
```

---

## Tóm tắt pattern nguy hiểm

| Pattern | Dấu hiệu nhận biết | Trang liên quan |
|---|---|---|
| Unboxing kiểu bọc từ request | Phép `+ - * /` trên `Double`/`Integer` của DTO | [05](05-service-layer.md) |
| Tin field initializer trong entity có `@Builder` | `private Integer x = 0;` + `@Builder` | [03](03-entity-and-persistence.md) |
| Tin dữ liệu client gửi để xác định danh tính | `@RequestHeader("X-User-...")` | [07](07-security.md) |
| Constructor `Authentication` 2 tham số | `new UsernamePasswordAuthenticationToken(a, b)` | [07](07-security.md) |
| JPQL/derived query trỏ nested property | `x.y.id` trong `@Query`, `findByAB` | [04](04-repository-and-queries.md) |
| Tin `ddl-auto: update` migrate được schema | Đổi kiểu cột / đổi tên bảng | [02](02-new-service-checklist.md) |

**Điểm chung của cả 6 bug:** không cái nào bị compiler bắt được, và 4/6 không ném exception
tại chỗ. Đó là lý do [08-definition-of-done.md](08-definition-of-done.md) bắt buộc
**chạy thật và nhìn vào kết quả**, chứ không dừng ở "build pass".
