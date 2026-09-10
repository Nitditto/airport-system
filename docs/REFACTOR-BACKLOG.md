# Refactor Backlog

Trạng thái codebase, bug đã biết, và việc còn tồn đọng. Nội dung ở đây **không phải rule** — rule/convention nằm ở [`CLAUDE.md`](../CLAUDE.md). Đọc file này trước khi bắt đầu một đợt refactor.

Ký hiệu: ✅ đã xong · ⏳ còn tồn đọng · 🔭 ngoài phạm vi hiện tại.

---

## Nhóm A — Refactor nền tảng ✅

Đã hoàn thành cho cả 5 service. Pilot trên `pricing-service` (PR #9) để chốt công thức, sau đó nhân rộng ra `location-service`, `airline-core-service`, `flight-ops-service`, `user-service` cùng một đợt (PR #11).

1. ✅ **Chuyển toàn bộ ID sang UUIDv7** — mọi entity ở cả 5 service giờ `extends BaseEntity` (`common-lib/entity/BaseEntity.java`), PK là UUIDv7 lưu `binary(16)`. Đã drop và tạo lại cả 5 database dev (`airline_pricing`, `airline_location`, `airline`, `airline_flight`, `airline_user`).
   - Trước đây: mỗi entity tự khai `id`/`createdAt`/`updatedAt` riêng lẻ với `Long` + generation strategy không đồng nhất (`AUTO` ở location/pricing/user, `IDENTITY` ở airline-core/flight-ops).
2. ✅ **Gỡ toàn bộ relationship annotation** — cả 8 chỗ vi phạm đã gỡ `@ManyToOne`/`@OneToOne`/`@ElementCollection`, thay bằng cột `UUID` thô + (riêng `FlightSchedule.operatingDays`) `DayOfWeekSetConverter`. Vì mất `cascade`, đã thêm chặn tường minh (`BusinessException`) ở tầng service cho mọi cặp cha-con từng dựa vào FK constraint thật của MySQL: `City`↔`Airport`, `Airline`↔`Aircraft`, `Flight`↔`FlightSchedule`/`FlightInstance`; và xoá cha-con thủ công có `@Transactional` cho `Fare`↔`FareRules`/`BaggagePolicy`.
3. ✅ **Sửa `@Query` sau khi gỡ quan hệ** — `FlightInstanceRepository` đổi `fi.flight.id = :flightId` → `fi.flightId = :flightId`; `FlightScheduleRepository.findByFlightAirlineId(Long)` (derived query đi qua nested property không còn tồn tại) đổi thành 2 bước `FlightRepository.findIdsByAirlineId()` + `findByFlightIdIn()`. Đã verify chạy thật, không chỉ compile.

### Vi phạm relationship annotation đã gỡ (lịch sử)

| Service | Entity | Annotation trước đây | Đã thay bằng |
|---|---|---|---|
| pricing | `Fare.baggagePolicy` | `@OneToOne(mappedBy)` | bỏ hẳn; `BaggagePolicy.fareId` là `UUID` |
| pricing | `Fare.fareRules` | `@OneToOne(mappedBy)` | bỏ hẳn; `FareRules.fareId` là `UUID` |
| pricing | `FareRules.fare`, `BaggagePolicy.fare` | `@OneToOne` | `private UUID fareId;` |
| airline-core | `Aircraft.airline` | `@ManyToOne` | `private UUID airlineId;` |
| flight-ops | `FlightSchedule.flight` | `@ManyToOne` | `private UUID flightId;` |
| flight-ops | `FlightInstance.flight` | `@ManyToOne` | `private UUID flightId;` |
| flight-ops | `FlightSchedule.operatingDays` | `@ElementCollection` | `@Convert` + `DayOfWeekSetConverter` |
| location | `Airport.city` | `@ManyToOne` | `private UUID cityId;` |

### Bug có sẵn phát hiện lúc test end-to-end ⏳

Không phải do migration gây ra, chỉ lộ ra vì `GlobalExceptionHandler` giờ bắt được thay vì crash 500 thô. Đã tách task riêng, **chưa sửa**:

- ⏳ `FareMapper.toEntity()` (pricing) — NPE khi thiếu `taxesAndFees`/`airlineFees` trong request, cộng trực tiếp `Double` có thể null.
- ⏳ `Aircraft.getTotalSeats()` (airline-core) — NPE khi thiếu bất kỳ field seat nào (`economySeats`/`premiumEconomySeats`/`businessSeats`/`firstClassSeats`), cùng lý do.

---

## Nhóm B — Bug cấu hình ✅

4. ✅ **Port collision** — `flight-ops-service` đã đổi sang `server.port: 5003` (PR #6). Port hiện tại: `user=5001, airline-core=5002, flight-ops=5003, location=5004, pricing=5005`.
5. ✅ **`user-service` sai datasource** — đổi từ `jdbc:mysql://localhost:3306/airline_location` (copy nhầm từ location-service) sang `jdbc:mysql://localhost:3306/airline_user`. Cần tự tạo DB trước khi chạy: `CREATE DATABASE airline_user;`.
6. ✅ **`SecurityConfig` của user-service `permitAll()` toàn bộ request** — thêm `JwtAuthenticationFilter` (`OncePerRequestFilter`) đọc header `Authorization: Bearer <token>`, validate bằng `JwtProvider.parseClaims()`, dựng `Authentication` từ claim `email`/`authorities` rồi nạp vào `SecurityContextHolder`. `SecurityConfig` giờ chỉ `permitAll()` cho `/` và `/auth/**`, còn lại `authenticated()`. Filter gắn trước `UsernamePasswordAuthenticationFilter` bằng `addFilterBefore`.

### Nợ kỹ thuật phát sinh từ fix #6 ⏳

- ⏳ **`UserController.getUserProfile()`/`getAllUsers()` tin header `X-User-Email` do client tự gửi** để xác định danh tính. Endpoint đã yêu cầu đăng nhập nhưng **chưa** kiểm tra danh tính đó khớp với người dùng trong JWT — một user đã đăng nhập vẫn đọc được profile người khác bằng cách tự đổi header. Cần lấy email từ `SecurityContextHolder` thay vì tin header client.
- ⏳ **`AuthServiceImpl.signup()` tạo `Authentication` bằng constructor 2 tham số** (`new UsernamePasswordAuthenticationToken(email, password)`) → `getAuthorities()` luôn rỗng → JWT sinh lúc đăng ký **không có claim `authorities`**, user vừa signup chưa có role nào cho tới lần login kế tiếp. Cần dùng cùng cách tạo `Authentication` như `login()`.

---

## Nhóm C — Chất lượng tầng dữ liệu ⏳

7. ⏳ **Không có một `@Index` nào trong toàn repo** — mọi truy vấn lọc theo `flightId`, `cabinClassId`, `airlineId`, `cityId`... đang full table scan. Các cột cần index: `fares(flight_id, cabin_class_id)`, `flights(airline_id)`, `flight_instances(airline_id, departure_date_time)`, `airports(city_id)`, `aircrafts(airline_id)`.
8. ⏳ **`@Transactional` dùng không nhất quán** — chỉ `flight-ops-service` có (`FlightServiceImpl`, `FlightScheduleServiceImpl`); các luồng xoá cha-con thủ công ở service khác cần bổ sung. Cấp thiết sau khi bỏ `cascade`.
9. ⏳ **`pricing-service` thiếu phân trang** — `getFares()` dùng `findAll()` không giới hạn, trong khi 3 service khác đã dùng `Page<T>` + `Pageable`.
10. ⏳ **`FareServiceImpl.getFares()` trả `List<Fare>` (entity thô)** thay vì `List<FareResponse>` — rò rỉ entity ra ngoài tầng service.
11. ⏳ **Chưa bật JDBC batching** — không service nào cấu hình `hibernate.jdbc.batch_size`. Sau khi chuyển UUIDv7, batch insert đã khả thi nên bật cùng lúc.
12. ⏳ **`open-in-view` vẫn bật mặc định** — giữ DB connection tới tận lúc render response. Đặt `spring.jpa.open-in-view: false`.

---

## Nhóm D — Chuẩn hoá tầng API

13. ✅ **Exception handling** — `ResourceNotFoundException`/`BusinessException` trong `common-lib/exception/`; mỗi service có `GlobalExceptionHandler` riêng (`@RestControllerAdvice`) xử lý cả 2 exception này + `MethodArgumentNotValidException` (gom field error) + catch-all `Exception.class` (log đầy đủ ở server bằng `@Slf4j`, trả `ApiResponse.error("Internal server error")` chung chung).
14. ✅ **`ApiResponse<T>` generic wrapper** — có `success`/`message`/`data` + static factory `success()`/`error()`, áp dụng cho MỌI endpoint có trả dữ liệu ở cả 5 service. `DELETE` giữ `204 No Content` không body.
15. ✅ **Thiếu `@Valid`** — đã thêm cho `FareController.updateFare`, `AircraftController.updateAircraft`, `FlightController.updateFlight`, `FlightScheduleController.updateFlightSchedule`. Rà thêm nếu phát hiện chỗ mới.
16. ⏳ **Không có SLF4J logging ở tầng service** — `@Slf4j` mới chỉ có ở `GlobalExceptionHandler` của mỗi service; chưa có log `info` cho hành động ghi (create/update/delete).

---

## Nhóm E — Ngoài phạm vi 🔭

17. 🔭 **`cloud/` module rỗng** — service registry / config server / API gateway mới là comment trong `pom.xml`. Cho tới khi dựng xong, service gọi nhau phải hardcode `localhost:<port>`.
18. 🔭 **Không có test, không có CI/CD, không có Docker/docker-compose** — lưu ý khi ước lượng công sức refactor.

---

## Việc vặt khác ⏳

- ⏳ **`user-service/pom.xml` tự ghim `<version>0.13.0</version>`** cho 3 dependency `jjwt-*` thay vì khai ở `dependencyManagement` gốc. Dọn cho đúng convention quản lý version.
- ⏳ **`application.yaml` hardcode user/password MySQL** — chuyển sang biến môi trường hoặc config server trước khi lên môi trường thật; không bao giờ commit giá trị thật.

---

## Git housekeeping ⏳

- ⏳ **Đổi tên nhánh `feat/seat-service` → `feature/seat-service`** (chưa merge, còn đang làm dở). Repo có 5 nhánh dùng `feature/` nhưng nhánh này lệch sang `feat/`:
  ```bash
  git branch -m feat/seat-service feature/seat-service
  git push origin :feat/seat-service
  git push -u origin feature/seat-service
  ```
- ⏳ **Dọn 7 nhánh đã merge** vẫn còn cả local lẫn `origin` (`docs/build-base`, `fix/flight-ops`, và 5 nhánh `feature/*-service`):
  ```bash
  git branch --merged develop | grep -v develop | xargs -r git branch -d
  git remote prune origin
  ```
  Giữ lại `feat/seat-service` (chưa merge).
- ⏳ Vài commit lịch sử thiếu scope dù chỉ đụng một module (vd. `feat: add location-service...` lẽ ra `feat(location): ...`). Từ giờ áp dụng đủ scope theo [`CLAUDE.md` §17](../CLAUDE.md#17-git-convention-bắt-buộc).
