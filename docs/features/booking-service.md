# Booking Service

> Trạng thái: ⬜ **Thiết kế** — chưa có module trong repo. Bám theo
> [`ROADMAP.md`](../ROADMAP.md) Section 11 (Booking), Section 12 (Payment), Section 17 (Kafka).
> Mọi quy tắc entity/repository/service ở đây tuân theo [`../../CLAUDE.md`](../../CLAUDE.md).

## 1. Booking service là gì

Khi hành khách **chọn chuyến bay → chọn ghế → thêm suất ăn và hành lý → bấm thanh toán**,
toàn bộ giao dịch đó do **booking service** sở hữu.

Đây là **service điều phối trung tâm (orchestrator)** của hệ thống. Không service nào chạm
tới nhiều service khác như nó: `user`, `flight-ops`, `seat`, `pricing`, `ancillary`,
`payment`, `notification`.

Không có service này thì các mảnh rời rạc không nối được với nhau: chuyến bay có, ghế có,
giá vé có — nhưng **không có giao dịch nào ràng buộc chúng với một hành khách thật**.

## 2. Ba nhiệm vụ

| # | Nhiệm vụ | Nội dung |
|---|---|---|
| 1 | **Tạo booking** | Gom thông tin hành khách, ghế, add-on (suất ăn, hành lý) và **khởi tạo thanh toán** |
| 2 | **Phản ứng với thanh toán** | Nghe kết quả payment → **confirm** hoặc **cancel** booking |
| 3 | **Thông báo downstream** | Báo cho `seat-service` và `notification-service` biết chuyện gì đã xảy ra |

## 3. Vì sao tách thành service riêng

Booking là mối quan tâm **cross-cutting** nhất hệ thống — nó đụng tới user, flight, seat,
fare, ancillary, meal, payment.

- Nhét logic này vào bất kỳ service nào ở trên sẽ tạo **coupling khổng lồ**.
- Service riêng ⇒ mỗi service khác chỉ tập trung vào domain của nó.
- Giao dịch booking được **quản lý, theo dõi, scale độc lập**.
- Sự kiện payment và luồng confirm đi qua **Kafka** sạch sẽ, không làm rối các service khác.

## 4. Các entity

Booking service có **3 entity**: `Booking`, `Passenger`, `Ticket`.

Tất cả `extends BaseEntity` (PK UUIDv7). **Không** dùng annotation quan hệ JPA —
mọi tham chiếu tới service khác là cột `UUID` thô đặt tên `<entity>Id`
(xem `CLAUDE.md` mục "NO JPA Relationship Annotations").

### 4.1. `Booking` — bản ghi giao dịch

Là **single source of truth** cho những gì hành khách đã đặt. Nó buộc mọi thứ từ các
service khác lại với nhau bằng **ID tham chiếu cross-service**. Một booking có thể có
**nhiều passenger** (ví dụ gia đình đặt chung).

Booking **không lưu** full object của flight / fare / seat — chỉ lưu **ID**, khi cần dữ
liệu mô tả (chi tiết chuyến bay, tên ghế…) thì batch-fetch sang service tương ứng.
**Ngoại lệ: tiền.** Giá phải được **đóng băng tại thời điểm đặt** (ROADMAP §11.1
"snapshot fare") — fetch giá "on-demand" là sai vì giá có thể đổi sau đó và làm tổng tiền
đã thu lệch với booking.

| Nhóm field | Field | Ghi chú |
|---|---|---|
| Định danh | `bookingReference` | Mã booking người dùng thấy (PNR), sinh khi tạo, unique |
| Ai đặt | `userId` | Trỏ sang `user-service` — chủ tài khoản |
| Chuyến bay | `flightId`, `flightInstanceId` | Trỏ sang `flight-ops-service` |
| Giá vé | `fareId`, `cabinClass` | Trỏ sang `pricing-service` / `seat-service` |
| Ghế | `seatInstanceIds` | List — ghế đã chọn (trỏ sang `seat-service`) |
| Add-on | `ancillaryIds` | List — dịch vụ đã chọn (trỏ sang `ancillary-service`) |
| Suất ăn | `mealIds` | List — suất ăn đã chọn (trỏ sang `ancillary-service`) |
| **Giá (snapshot)** | `fareAmount`, `seatAmount`, `ancillaryAmount`, `mealAmount`, `totalAmount`, `currency` | Chốt lúc bước 6, **không** tính lại từ giá live |
| Trạng thái | `status` | `PENDING` → `CONFIRMED` / `CANCELLED` (+ `EXPIRED`) |
| Thanh toán | `paymentId` | **Ban đầu null**, set khi payment confirm |
| Vé | `ticketIssued` | Boolean — đã sinh vé chưa |
| Liên hệ | `contactInfo` | `@Embedded` — email + phone |
| Hạn chót | `ticketTimeLimit` | Deadline hoàn tất thanh toán (booking linh hoạt) |

> **Áp vào codebase:**
> - Field List (`seatInstanceIds`, `ancillaryIds`, `mealIds`): **không** `@ElementCollection`.
>   Hai lựa chọn hợp lệ: (1) `AttributeConverter<List<UUID>, String>` → 1 cột (giống
>   `DayOfWeekSetConverter`); (2) entity con (`BookingSeat`, `BookingAncillary`,
>   `BookingMeal`) giữ `bookingId` là UUID — hướng `ROADMAP.md` §11.1 mô tả, và là chỗ tự
>   nhiên để **snapshot giá từng dòng**.
> - Mọi cột FK UUID khai `columnDefinition = "binary(16)"`; thêm `@Index` cho cột hay lọc:
>   `user_id`, `booking_reference`, `status`, `flight_instance_id`.
> - `contactInfo` nếu tái dùng ở service khác → đưa vào `common-lib/embeddable/`
>   (cùng chỗ `Address`, `GeoCode`, `Support`).

### 4.2. `Passenger` — người bay

Một booking có thể đặt cho **nhiều người**: gia đình 4 người, một cặp, đồng nghiệp đi cùng.
Mỗi người trên chuyến bay là một bản ghi `Passenger`.

**Passenger ≠ User.** User là chủ tài khoản đã mua vé; passenger là người **thực sự lên
máy bay**. Đôi khi trùng nhau, thường thì không.

> Ví dụ: user **Pablo Panday** đặt vé cho 3 passenger: **Rahul Kumar**, **Priya Kumari**,
> **Aryan Kumar**.

Field chính: `bookingId` (UUID), họ tên, ngày sinh, giới tính, loại giấy tờ + số giấy tờ,
quốc tịch, loại hành khách (adult/child/infant).

### 4.3. `Ticket` — chứng từ đi lại chính thức

Sinh cho **mỗi passenger trong một booking**: **1 booking = 1 ticket / passenger**.

Ticket tách khỏi booking vì nó có **số vé riêng in trên boarding pass**, có **trạng thái
riêng** (`ISSUED` / `USED` / `CANCELLED`), và ghi lại **chính xác thời điểm phát hành**.

Vé được sinh **tự động lúc tạo booking**:
- Trước thanh toán: `status = PENDING`
- Payment confirm xong: `status = ISSUED`

| Field | Ghi chú |
|---|---|
| `ticketNumber` | Số vé duy nhất, in trên boarding pass |
| `status` | `PENDING` → `ISSUED` → `USED` / `CANCELLED` |
| `issuedDate` | Thời điểm phát hành |
| `bookingId` | UUID trỏ về booking |
| `passengerId` | UUID trỏ về passenger |

## 5. Luồng tạo booking

```
User bấm "đặt vé"
   │
   ├─ 1. Sinh bookingReference (mã PNR duy nhất)
   ├─ 2. Tìm hoặc tạo bản ghi Passenger cho từng người
   ├─ 3. Xác minh chuyến bay tồn tại (gọi flight-ops-service)
   ├─ 4. Lưu Booking ở status = PENDING
   ├─ 5. Tự sinh Ticket cho mỗi passenger (status = PENDING)
   ├─ 6. Tính tổng giá = fare + seat price + ancillary price + meal price
   │      (batch-fetch pricing-service / seat-service / ancillary-service)
   └─ 7. Khởi tạo thanh toán (gọi payment-service)
             │
             ▼
   payment-service thu tiền từ user
             │
             ▼
   payment-service báo lại booking-service "đã thu tiền thành công"
             │
             ▼
   booking-service:  Booking  PENDING → CONFIRMED
                     Ticket   PENDING → ISSUED
                     → notify seat-service (chốt ghế) + notification-service (gửi vé)
```

## 6. Vòng đời trạng thái

**Booking:** `PENDING` ──(payment thành công)──▶ `CONFIRMED`
`PENDING` ──(payment thất bại / hết `ticketTimeLimit`)──▶ `CANCELLED`

**Ticket:** `PENDING` ──(booking confirmed)──▶ `ISSUED` ──(check-in/bay)──▶ `USED`
bất kỳ lúc nào trước khi bay: ──▶ `CANCELLED`

## 7. Booking service với vai trò orchestrator

1. **Thu thập** mọi thứ: passenger, ghế, fare, add-on.
2. **Khởi tạo thanh toán** (front-end redirect sang cổng thanh toán).
3. **Lắng nghe Kafka** chờ kết quả payment → confirm hoặc cancel booking.
4. **Thông báo tự động** cho seat-service và hành khách.

## 8. Giao tiếp cross-service

| Service | Booking cần gì | Cơ chế |
|---|---|---|
| `user-service` | Xác thực user, thông tin liên hệ | REST (sau này Feign) |
| `flight-ops-service` | Xác minh flight / flightInstance | `POST /batch-by-ids` |
| `seat-service` | Kiểm tra ghế trống, giữ ghế, chốt ghế | REST + có timeout tự nhả ghế |
| `pricing-service` | Lấy fare để tính giá | `POST /api/fares/batch-by-ids` |
| `ancillary-service` | Giá ancillary + meal | `POST /batch-by-ids`, `/calculate-price` |
| `payment-service` | Khởi tạo & theo dõi thanh toán | REST khởi tạo + **Kafka** nhận kết quả |
| `notification-service` | Gửi xác nhận / vé điện tử | **Kafka** phát sự kiện `BookingConfirmed` |

> **Lưu ý trạng thái repo hiện tại:** chưa có Kafka, service registry hay Feign
> (xem `CLAUDE.md` mục "Known Gaps / Nhóm E"). Cho tới khi module `cloud/` được dựng,
> mọi lời gọi là REST thủ công `http://localhost:<port>` và phần "Kafka" ở trên là
> hợp đồng thiết kế, chưa hiện thực.

## 9. Tuân thủ convention repo (`CLAUDE.md`)

| Quy tắc | Áp dụng cho booking-service |
|---|---|
| `@Transactional` khi ghi ≥2 bảng, không còn cascade | `createBooking` ghi `Booking` + N `Passenger` + N `Ticket` trong **một** transaction; các luồng confirm/cancel cũng vậy |
| `saveAll()` chứ không `save()` trong vòng lặp | Sinh N ticket / N passenger bằng `saveAll()` (xem seat-service `SeatServiceImpl`) |
| List endpoint bắt buộc `Page<T>` + `Pageable` | `GET /api/bookings` (theo user) phải phân trang |
| Batch cross-service, không gọi HTTP trong loop | Tính giá ở bước 6: 1 lần `batch-by-ids` mỗi service, không lặp theo từng ghế/add-on |
| `ApiResponse<T>` bọc mọi response; `@Valid` cho `@RequestBody` | Áp cho toàn bộ controller |
| `GlobalExceptionHandler` riêng + `ResourceNotFoundException` / `BusinessException` từ `common-lib` | Ghế đã bán, quá `ticketTimeLimit`, PNR trùng… → `BusinessException` (400), không `throw Exception` thô |
| Kiểm tra tồn tại bằng `existsBy*` | `existsByBookingReference` khi sinh PNR (đụng thì sinh lại) |
| Inject qua interface, không `*Impl`; class Service > 200 dòng thì tách | Tách `BookingCreationService` / `BookingPaymentCallbackService` nếu phình |
| Open/Closed cho chuyển trạng thái | Chuyển `status` qua một `BookingStateMachine` / Strategy, không rải `if (status == …)` khắp service |

## 10. Điểm thiết kế cần chốt (transcript chưa nói tới)

- **Saga + hành động bù trừ.** Đây là orchestration saga: nếu payment **fail sau khi** đã
  sinh ticket + giữ ghế thì phải bù trừ — ticket `PENDING → CANCELLED`, gọi seat-service
  **nhả ghế**, booking `→ CANCELLED`. "Cancel booking" không chỉ là đổi 1 cột.
- **Idempotency của consumer Kafka.** Sự kiện payment có thể được giao >1 lần → trước khi
  confirm phải check `status == PENDING` (hoặc lưu `paymentEventId` đã xử lý), tránh
  confirm 2 lần / gửi vé 2 lần.
- **Giữ ghế tạm (seat hold).** Luồng ở mục 5 thiếu bước **giữ ghế** trước khi thu tiền
  (ROADMAP §11.5). Phải giữ ghế ngay sau khi verify flight, có TTL, fail/timeout thì nhả.
- **`ticketTimeLimit` expiry cần scheduler.** Repo **chưa có** `@Scheduled` / `@EnableScheduling`
  ở bất kỳ service nào → đây là hạ tầng mới, phải flag như Kafka.
- **Bước 5 trong transcript nói nhầm.** "auto generate tickets… status will change to Issued"
  — đúng phải là sinh ticket ở `PENDING`, chỉ `ISSUED` **sau** khi payment confirm.

## 11. Việc cần làm (bám ROADMAP Section 11)

- [ ] Khởi tạo module `services/booking-service` (port kế tiếp còn trống, vd 5009)
- [ ] Entity `Booking`, `Passenger`, `Ticket` + (khuyến nghị) `BookingSeat/Ancillary/Meal` để snapshot giá
- [ ] `BookingRequest` / `BookingResponse` trong `common-lib/payload`; `ContactInfo` cân nhắc `common-lib/embeddable`
- [ ] `@Transactional` cho create/confirm/cancel; sinh ticket bằng `saveAll()`
- [ ] `GET /api/bookings` phân trang; mọi endpoint bọc `ApiResponse<T>` + `@Valid`
- [ ] `GlobalExceptionHandler` + dùng `ResourceNotFoundException` / `BusinessException`
- [ ] State machine `PENDING → CONFIRMED / CANCELLED / EXPIRED` + hành động bù trừ
- [ ] Tích hợp giữ/nhả ghế với seat-service, có TTL
- [ ] Scheduler quét booking `PENDING` quá `ticketTimeLimit` → cancel + nhả ghế
- [ ] Consumer Kafka cho sự kiện payment (Section 17), đảm bảo idempotent
- [ ] Test: đặt vé thành công, payment thất bại + bù trừ, hết hạn giữ ghế, giao sự kiện trùng, huỷ booking
