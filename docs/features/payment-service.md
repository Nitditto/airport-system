# Payment Service

> Trạng thái: ⬜ **Thiết kế** — chưa có module trong repo. Bám theo
> [`ROADMAP.md`](../ROADMAP.md) Section 12 (Payment), Section 13 (Inter-Service Communication),
> Section 17 (Kafka). Mọi quy tắc entity/repository/service ở đây tuân theo
> [`../../CLAUDE.md`](../../CLAUDE.md). Tham chiếu chéo: [`booking-service.md`](booking-service.md)
> §5, §8, §10 (booking-service là consumer trực tiếp của service này).

## 1. Payment service là gì

Sau khi `booking-service` tạo một booking ở trạng thái `PENDING`, hành khách phải **trả tiền**.
`payment-service` là service chuyên trách **duy nhất mọi thứ liên quan tới tiền**: tạo link
thanh toán, xử lý kết quả, và báo lại cho phần còn lại của hệ thống.

**Không service nào khác được xử lý tiền.** Mọi thao tác liên quan thanh toán trong toàn hệ
thống đi qua service này.

## 2. Ba nhiệm vụ

| # | Nhiệm vụ | Nội dung |
|---|---|---|
| 1 | **Initiate** | Tạo bản ghi `Payment` + sinh checkout link qua Razorpay |
| 2 | **Verify** | Nhận/truy vấn kết quả từ Razorpay → xác nhận hoặc từ chối payment |
| 3 | **Publish** | Báo kết quả cho `booking-service` để nó phản ứng ngay |

## 3. Vì sao tách thành service riêng

- **Logic thanh toán nhạy cảm, yêu cầu bảo mật chặt** và thay đổi độc lập với logic booking.
- Tách riêng ⇒ **credential cổng thanh toán** (API key/secret) bị cô lập trong đúng một service,
  không rải rác sang `booking-service` hay service khác.
- Nếu đổi cổng thanh toán (Razorpay ↔ VNPay/Momo/Stripe — xem `ROADMAP.md` §12.4 liệt kê các
  lựa chọn khác cho thị trường VN), **payment failure và retry được quản lý tại một chỗ**,
  không ảnh hưởng trực tiếp trạng thái booking.
- **Compliance và audit** thu hẹp phạm vi về một service duy nhất — dễ kiểm toán hơn nhiều so
  với rải logic tiền bạc khắp nơi.

> **Ghi chú đối chiếu ROADMAP:** `ROADMAP.md` §12.4 gợi ý VNPay/Momo/Stripe cho thị trường Việt
> Nam, không nhắc Razorpay. Plan này dùng **Razorpay** theo xác nhận của bạn (transcript gốc
> mô tả đúng Razorpay Payment Links API — amount tính bằng paisa, field `notes` nhúng metadata,
> `notify.sms`/`notify.email`, `callback_url`, status `captured`). Vì gateway đã được cô lập
> hoàn toàn trong `RazorpayService` (xem §5), đổi sang gateway khác sau này chỉ đụng đúng class
> đó — đây chính là lý do transcript đưa ra ở trên để tách riêng lớp gateway.

## 4. Entity `Payment`

Payment service có **đúng một entity**: `Payment` — bản ghi giao dịch tài chính gắn với một
booking.

**Vì sao entity này tồn tại:** mọi booking đi qua checkout cần một bản ghi vĩnh viễn: đổi qua
gateway nào, kết quả ra sao, lúc nào. Đây là **nguồn sự thật (source of truth)** cho tài chính,
hoàn tiền, và tra cứu hỗ trợ khách hàng.

`Payment extends BaseEntity` (PK UUIDv7, có sẵn `createdAt`/`updatedAt` — **không** khai lại 2
field audit này, xem `CLAUDE.md` §10). **Không** dùng annotation quan hệ JPA — tham chiếu sang
`booking-service`/`user-service` là cột `UUID` thô.

| Nhóm field | Field | Kiểu | Ghi chú |
|---|---|---|---|
| Ai trả | `userId` | `UUID` | Trỏ sang `user-service` |
| Trả cho gì | `bookingId` | `UUID` | Trỏ sang `booking-service` — **không unique tuyệt đối** (một booking có thể có nhiều lần thử payment thất bại trước khi thành công) |
| Số tiền | `amount` | `Double` | Tổng tiền charge — **không** nhận trực tiếp từ client, xem §10 |
| Gateway | `provider` | `String` | Vd. `"RAZORPAY"`. Giữ `String` phẳng thay vì enum vì hiện chỉ có 1 provider — thêm enum khi có provider thứ 2, tránh over-engineering theo `CLAUDE.md` §11 |
| ID bên gateway | `providerPaymentId` | `String` | `razorpay_payment_link_id` lúc initiate, cập nhật thành `razorpay_payment_id` lúc verify |
| Mã giao dịch | `transactionId` | `String` | Mã giao dịch cuối khi `captured` |
| Phương thức | `method` | `PaymentMethod` (enum mới) | `CARD`, `UPI`, `NETBANKING`, `WALLET` — Razorpay trả field này trong response fetch payment |
| Trạng thái | `status` | `PaymentStatus` (đã có sẵn trong `common-lib`) | `PENDING → SUCCESS` / `FAILED` / `CANCELLED`; `REFUNDED` và `PROCESSING` chưa dùng ở bản đầu, xem §10 |
| Lý do fail | `failureReason` | `String`, nullable | Lấy từ `error_description` của Razorpay khi status không phải `captured` |
| Thời điểm trả xong | `paidAt` | `Instant`, nullable | Set khi verify ra `SUCCESS` |
| Hoàn tiền | `refundId` | `String`, nullable | Chưa có luồng tạo giá trị này ở bản đầu — trường để sẵn chỗ, xem §10 |

> **Áp vào codebase:**
> - `@Table(name = "payments", indexes = {@Index(columnList = "booking_id"), @Index(columnList = "booking_id, status"), @Index(columnList = "user_id")})`
>   — index tổ hợp `(booking_id, status)` phục vụ đúng truy vấn "đã có payment SUCCESS cho
>   booking này chưa" ở bước initiate (`existsByBookingIdAndStatus`), theo đúng công thức
>   `CLAUDE.md` §10 "Index cho cột được query thường xuyên".
> - Mọi cột FK khai `columnDefinition = "binary(16)"` giống mọi entity khác trong repo.
> - `PaymentMethod` là enum **mới**, thêm vào `common-lib/enums/` (dùng chung nếu sau này
>   service khác cần đọc).
> - `PaymentStatus` **đã có sẵn** ở `common-lib/enums/PaymentStatus.java` — dùng lại, không tạo
>   enum trùng.
> - `common-lib` cũng đã có sẵn `PaymentDTO` (`id`, `paymentStatus`) và `PaymentLinkResponse`
>   (`id`, `paymentUrl`, `status`, `expiresAt`) — đây là 2 DTO stub `booking-service` đang dùng
>   tạm (`BookingEnrichmentContext.payment` kiểu `PaymentDTO`, xem
>   [`booking-service.md`](booking-service.md)). Payment-service **populate 2 DTO này thật**
>   thay vì tạo response DTO cạnh tranh; chỉ cần thêm mới `PaymentRequest`
>   (`common-lib/payload/request/`) cho request initiate.
> - `expiresAt` của `PaymentLinkResponse` ánh xạ từ field `expire_by` (unix timestamp) khi tạo
>   Razorpay payment link — transcript gốc không nhắc, nhưng field đã tồn tại sẵn trong
>   `common-lib` nên nên set luôn lúc tạo link.

## 5. Gateway layer: `RazorpayService`

Class chuyên trách **duy nhất giao tiếp trực tiếp với Razorpay API**, tách hoàn toàn khỏi
business logic — đặt trong package `gateway/` (mới, riêng cho service này) hoặc `service/` nếu
muốn gộp, miễn nó **không** implement `PaymentService` interface (nó là API client, không phải
business service theo domain).

### 5.1. `createPaymentLink(Payment payment, UserDTO user)`

`POST https://api.razorpay.com/v1/payment_links`, HTTP Basic Auth bằng `key_id:key_secret` (2
biến môi trường — **không commit**, theo `CLAUDE.md` §16).

1. Convert `amount` sang paisa: `long amountInPaisa = Math.round(payment.getAmount() * 100)`.
2. Gắn `customer: {name, email, contact}` lấy từ `UserDTO` (`fullName`, `email`, `phone`).
3. Nhúng nội bộ vào field `notes`: `{paymentId, bookingId, userId}` — đây là cách nhận diện lại
   payment khi Razorpay callback/khi mình fetch lại sau này (không phải "node field" — đúng
   tên field Razorpay là `notes`).
4. Bật `notify: {sms: true, email: true}`.
5. Set `callback_url` (success) và tận dụng đúng field Razorpay hỗ trợ (`callback_method: "get"`);
   Razorpay Payment Links **không có** field cancel-callback riêng như transcript mô tả — chỉ
   một `callback_url` chung, phân biệt kết quả bằng query param Razorpay tự đính kèm
   (`razorpay_payment_id`, `razorpay_payment_link_status`). Ghi rõ điểm lệch này khi implement.
6. Set `expire_by` (unix timestamp, khuyến nghị = `booking.ticketTimeLimit`) để đồng bộ hạn
   thanh toán với hạn giữ chỗ bên `booking-service`.
7. Trả về `short_url` (`payment_link`) cho client mở ra thanh toán.

### 5.2. `fetchPaymentDetails(String razorpayPaymentId)`

`GET https://api.razorpay.com/v1/payments/{id}`. Parse JSON, đọc field `status`:
`status.equals("captured")` ⇒ thành công. Các giá trị khác Razorpay trả về:
`created`, `authorized`, `failed`, `refunded` — map `failed` → `FAILED` với `error_description`,
các case còn lại (`created`/`authorized`) nghĩa là **chưa xong**, service gọi verify không nên
coi đó là `FAILED` (xem §10 — polling quá sớm).

## 6. Luồng nghiệp vụ

```
1) INITIATE  (client bấm "thanh toán")
   POST /api/payments/initiate  { bookingId }
   ├─ 1. existsByBookingIdAndStatus(bookingId, SUCCESS) → nếu true, ném BusinessException
   ├─ 2. Lấy amount đáng tin cậy: GET booking-service /api/bookings/{bookingId}
   │      (KHÔNG nhận amount từ client — xem §10)
   ├─ 3. Lưu Payment (status = PENDING)             ── @Transactional
   ├─ 4. Fetch user: GET user-service /api/users/{userId}
   ├─ 5. RazorpayService.createPaymentLink(payment, user)
   └─ 6. Trả PaymentLinkResponse { id, paymentUrl, status, expiresAt } cho client

2) VERIFY    (client quay lại qua callback_url, hoặc client tự gọi sau khi thanh toán xong)
   POST /api/payments/{id}/verify
   ├─ 1. RazorpayService.fetchPaymentDetails(payment.providerPaymentId)
   ├─ 2. status == "captured"  → Payment.status = SUCCESS, paidAt = now, transactionId = ...
   │      status == "failed"   → Payment.status = FAILED, failureReason = ...
   ├─ 3. Save Payment                                ── @Transactional
   └─ 4. Publish kết quả (bước 3 dưới đây)

3) PUBLISH   (báo cho booking-service)
   SUCCESS → PATCH booking-service /api/bookings/{bookingId}/confirm-payment
   FAILED  → PATCH booking-service /api/bookings/{bookingId}/cancel-payment
             (endpoint này CHƯA tồn tại trong booking-service — xem §10, việc cần làm ở cả 2 service)
             │
             ▼
   booking-service tự xử lý:  Booking PENDING → CONFIRMED / CANCELLED
                               Ticket  PENDING → ISSUED (nếu confirm)
                               → tự thông báo tiếp cho seat-service / notification-service
```

Payment-service **không biết gì** về ghế, vé hay notification — nó chỉ gọi đúng 1 endpoint của
booking-service rồi kết thúc trách nhiệm, đúng tinh thần transcript ("payment service itself
never needs to know what happens next").

## 7. Vòng đời trạng thái

`PENDING` ──(Razorpay báo `captured`)──▶ `SUCCESS`
`PENDING` ──(Razorpay báo `failed` / hết `expire_by`)──▶ `FAILED`
`PENDING` ──(user huỷ giữa chừng, nếu implement)──▶ `CANCELLED`

`REFUNDED` và `PROCESSING` (đã có sẵn trong `PaymentStatus`) **không đạt được** ở bản đầu vì
chưa có luồng refund (xem §10) — giữ nguyên trong enum cho tương lai, không xoá vì service khác
(`common-lib`) đã tham chiếu.

## 8. Giao tiếp cross-service

| Service | Payment cần gì | Cơ chế |
|---|---|---|
| `user-service` | Tên/email/phone để gắn vào Razorpay `customer` | `GET /api/users/{id}` (REST thủ công, xem ghi chú dưới) |
| `booking-service` | Amount đáng tin cậy lúc initiate; báo kết quả lúc verify xong | `GET /api/bookings/{id}` (đọc amount) + `PATCH /api/bookings/{id}/confirm-payment` \| `cancel-payment` (ghi kết quả) |
| `booking-service` (chiều ngược lại) | Đọc trạng thái payment khi liệt kê nhiều booking | `POST /api/payments/batch-by-ids` → `Map<UUID, PaymentDTO>` — **bắt buộc** theo `CLAUDE.md` §6/§7 để tránh N+1 khi `booking-service` enrich danh sách booking |

> **Lưu ý trạng thái repo hiện tại (đã xác nhận với bạn):** repo **chưa có Kafka**, chưa có
> service registry hay Feign (`CLAUDE.md` §2, `docs/REFACTOR-BACKLOG.md` mục 17). Transcript gốc
> mô tả bước 3 (Publish) bằng Kafka event (`payment.completed`/`payment.failed`); **bản plan này
> dùng REST callback đồng bộ thay thế tạm thời**, theo đúng lựa chọn bạn đã chốt. Đánh đổi so
> với Kafka:
> - Payment-service tạm thời **phụ thuộc uptime của booking-service** tại đúng lúc verify —
>   nếu call PATCH thất bại (network blip), Payment đã lưu `SUCCESS` nhưng booking vẫn `PENDING`
>   → **lệch trạng thái**. Đây chính là lý do ROADMAP §17 (Kafka) tồn tại; ghi lại như nợ kỹ
>   thuật đã biết, không chặn việc implement bản REST trước.
> - Giảm thiểu rủi ro này ở mức tối thiểu (không cần Kafka): sau khi Payment lưu `SUCCESS`/`FAILED`
>   thành công, mới gọi callback; nếu callback lỗi, log `error` đầy đủ và **không rollback**
>   Payment (tiền đã thu thật) — để lại một job/endpoint đối soát thủ công sau này thay vì chặn.
> - `RestClient`/`WebClient` gọi `http://localhost:5009` hardcode (booking-service port), y hệt
>   pattern đang dùng cho mọi cross-service call khác trong repo hiện nay.

## 9. Tuân thủ convention repo (`CLAUDE.md`)

| Quy tắc | Áp dụng cho payment-service |
|---|---|
| Không annotation quan hệ JPA | `Payment.userId`/`bookingId` là `UUID` thô |
| `@Transactional` cho method ghi | `initiate()` và `verify()` đều ghi bảng `payments` — `@Transactional` method-level, class-level `@Transactional(readOnly = true)` |
| `existsBy*` thay vì `findBy*` rồi check | `existsByBookingIdAndStatus(bookingId, SUCCESS)` ở bước initiate |
| Batch, không gọi HTTP/repo trong loop | Chỉ 1 payment/1 booking mỗi lần initiate — không có vòng lặp N ở đây, nhưng `batch-by-ids` vẫn bắt buộc cho chiều đọc (booking-service gọi vào) |
| `ApiResponse<T>` bọc mọi response; `@Valid` cho `@RequestBody` | `PaymentRequest { bookingId }` cần `@NotNull` |
| `GlobalExceptionHandler` riêng + `ResourceNotFoundException`/`BusinessException` | Booking không tồn tại → `ResourceNotFoundException`; đã có payment SUCCESS → `BusinessException` |
| Index cho cột hay lọc | `(booking_id, status)`, `user_id` — xem §4 |
| Inject qua interface, không `*Impl` | `PaymentController` inject `PaymentService` (interface), không `PaymentServiceImpl` |
| Không log dữ liệu nhạy cảm | **Không** log `key_secret` Razorpay, **không** log toàn bộ `UserDTO` (leak field `password`, xem §10), **không** log payload request tới Razorpay có chứa `notes` PII đầy đủ nếu không cần thiết |
| Credential không commit | `razorpay.key-id` / `razorpay.key-secret` đọc từ biến môi trường trong `application.yaml` (`${RAZORPAY_KEY_ID}`), không hardcode |

## 10. Điểm thiết kế cần chốt (transcript chưa nói tới)

- **Amount không được đến từ client.** Transcript nói "initiate payment" nhận request từ client
  nhưng không nói rõ amount lấy từ đâu. Đây là **lỗ hổng bảo mật kinh điển** nếu client tự gửi
  `amount` trong `PaymentRequest` — sửa DevTools là đổi được số tiền charge. `PaymentRequest`
  chỉ nên chứa `bookingId`; payment-service **tự** gọi `booking-service` lấy `totalAmount` đã
  chốt phía server.
- **Phụ thuộc chặn: `booking-service` chưa tính giá.** Theo khảo sát thực tế,
  `BookingServiceImpl.createBooking()` hiện có `// todo step 7: calculate price...` — `Booking`
  **chưa có field `totalAmount`** nào được lưu thật (chỉ có trên `BookingResponse` dạng enrich
  rỗng). Nghĩa là **payment-service không thể lấy amount thật cho tới khi booking-service hoàn
  thành bước tính giá** (xem [`booking-service.md`](booking-service.md) §11). Trong lúc dev/test
  payment-service độc lập, phải dùng amount giả lập (stub) và ghi rõ đây là phụ thuộc chưa xong,
  không phải bug của payment-service.
- **Race condition ở bước "check chưa có payment SUCCESS".** `existsByBookingIdAndStatus` rồi
  mới `save()` là TOCTOU (time-of-check-to-time-of-use) — 2 request initiate đồng thời cho cùng
  1 booking có thể cùng pass check trước khi cái nào insert xong. Giảm thiểu bằng unique
  constraint mức DB trên `(booking_id)` **có điều kiện** (MySQL không hỗ trợ partial unique
  index native — cân nhắc unique constraint tổng trên `booking_id` nếu quyết định "1 booking chỉ
  cho phép 1 Payment record tại một thời điểm, tạo mới thì tái dùng record cũ nếu còn PENDING/FAILED"
  thay vì luôn insert row mới).
- **`booking-service` chưa có endpoint nhận kết quả payment.** `BookingController` hiện chỉ có
  `PATCH /api/bookings/{id}/cancel` (yêu cầu header `X-User-Id`, thiết kế cho user tự huỷ, không
  hợp cho một service khác gọi vào). Cần **thêm 2 endpoint mới, không yêu cầu `X-User-Id`**, ở
  `booking-service`: `PATCH /api/bookings/{id}/confirm-payment` và
  `PATCH /api/bookings/{id}/cancel-payment` — đây là việc cần làm ở **cả hai** service, ghi rõ ở
  §11 và cả checklist của `booking-service.md`.
- **`UserDTO` cross-service lộ field `password`.** `common-lib/payload/dto/UserDTO.java` có field
  `password` (hash) — dùng nguyên DTO này cho response `GET /api/users/{id}` nghĩa là
  payment-service (và bất kỳ ai gọi endpoint đó) nhận được password hash dù không cần. Không sửa
  trong phạm vi payment-service (thuộc `user-service`/`common-lib`), nhưng payment-service
  **tuyệt đối không log/lưu lại** field đó — chỉ đọc `fullName`/`email`/`phone` rồi bỏ qua phần
  còn lại của response.
- **Không có xác minh chữ ký webhook.** Thiết kế ở §6 dùng **polling chủ động**
  (`fetchPaymentDetails` do payment-service tự gọi), không nhận webhook Razorpay POST vào —
  nhờ vậy **tránh được** yêu cầu bắt buộc verify `X-Razorpay-Signature` (HMAC) mà một webhook
  receiver thật sự cần. Nếu sau này đổi sang nhận webhook trực tiếp từ Razorpay (nhanh hơn polling,
  ROADMAP §12.5 "Xử lý Payment Webhook — xác thực chữ ký"), **bắt buộc** thêm bước verify HMAC
  trước khi tin bất kỳ payload nào — không làm thì ai cũng giả được webhook thành công.
- **Trạng thái `authorized`/`created` bị polling quá sớm.** Nếu client gọi `/verify` ngay sau khi
  redirect (trước khi Razorpay xử lý xong), `fetchPaymentDetails` có thể trả `created` hoặc
  `authorized`, không phải `captured` lẫn `failed`. Đừng map 2 trạng thái này thành `FAILED` —
  giữ nguyên `PENDING`, để client retry verify sau vài giây (hoặc thêm `@Scheduled` job quét
  `PENDING` quá X phút → tự fetch lại, tương tự nợ kỹ thuật `ticketTimeLimit` scheduler bên
  `booking-service.md` §10).
- **Payment thành công sau khi booking đã hết hạn/bị huỷ (saga compensation).** Nếu
  `booking-service` đã tự động cancel booking (hết `ticketTimeLimit`) trước khi Razorpay báo
  `captured`, `confirm-payment` gọi vào một booking không còn `PENDING` nữa. `booking-service`
  phải **từ chối** confirm trong trường hợp này (trả lỗi rõ ràng, không âm thầm no-op), và
  payment-service nhận lỗi đó phải **tự chuyển sang luồng refund** (chưa implement ở bản đầu —
  ghi vào backlog, không chặn payment-service ban đầu).
- **Refund chưa có luồng thật.** `refundId` và `PaymentStatus.REFUNDED` đã có chỗ trong entity/enum
  nhưng `RazorpayService` ở bản đầu **không** có method gọi `POST /v1/payments/{id}/refund`.
  Đây là công việc riêng, không nằm trong scope "initiate + verify + publish" ban đầu — liệt kê
  ở §11 như việc kế tiếp, khớp `ROADMAP.md` §12.7 ("Testing Payment flows... refund").

## 11. Việc cần làm (bám ROADMAP Section 12)

**Ở `payment-service` (module mới):**

- [x] Khởi tạo module `services/payment-service`, thêm `<module>payment-service</module>` vào
      [`services/pom.xml`](../../services/pom.xml); port **5010**; database `airline_payment`
- [ ] Entity `Payment extends BaseEntity` theo bảng field ở §4, index `(booking_id, status)`
- [ ] Enum mới `PaymentMethod` trong `common-lib/enums/`; dùng lại `PaymentStatus` có sẵn
- [ ] DTO mới `PaymentRequest { bookingId }` trong `common-lib/payload/request/`; populate
      `PaymentLinkResponse`/`PaymentDTO` có sẵn thay vì tạo response mới
- [ ] `RazorpayService` (gateway layer) — `createPaymentLink()`, `fetchPaymentDetails()`;
      credential qua biến môi trường, không hardcode
- [ ] `PaymentService`/`PaymentServiceImpl` — `initiate()`, `verify()`, `findByIds()` (cho
      `batch-by-ids`)
- [ ] `PaymentController` — `POST /api/payments/initiate`, `POST /api/payments/{id}/verify`,
      `POST /api/payments/batch-by-ids`, `GET /api/payments/{id}`
- [ ] `GlobalExceptionHandler` riêng cho service; `@Transactional` cho `initiate`/`verify`
- [ ] `RestClient`/`WebClient` bean gọi `user-service` (5001) và `booking-service` (5009) qua
      `http://localhost:<port>` (thủ công, thay bằng Feign khi `cloud/` xong — ROADMAP §13)
- [ ] Test: initiate thành công, initiate khi đã có payment SUCCESS (phải reject), verify
      `captured`/`failed`/`authorized` (chưa xong), callback booking-service lỗi (log, không mất
      tiền), amount không lấy từ client (test cố tình gửi amount sai bị bỏ qua)

**Ở `booking-service` (thay đổi cần thiết để nhận kết quả):**

- [ ] Thêm `PATCH /api/bookings/{id}/confirm-payment` và `PATCH /api/bookings/{id}/cancel-payment`
      — endpoint hệ thống gọi vào, không yêu cầu header `X-User-Id`
- [ ] Hoàn thành `// todo step 7: calculate price...` trước — payment-service không có amount
      thật để dùng nếu bước này chưa xong (xem §10)
- [ ] `confirm-payment` phải kiểm tra `booking.status == PENDING` trước khi chuyển `CONFIRMED`,
      từ chối rõ ràng nếu không (saga compensation, xem §10)

**Nợ kỹ thuật ghi nhận, chưa làm ở bản đầu:**

- [ ] Refund flow (`RazorpayService.refund()`, `PaymentStatus.REFUNDED`) — ROADMAP §12.7
- [ ] Thay REST callback đồng bộ bằng Kafka publish/consume thật — ROADMAP §17, khi hạ tầng sẵn sàng
- [ ] `@Scheduled` job quét `Payment.status == PENDING` quá hạn `expire_by` → tự fetch lại hoặc
      đánh dấu `FAILED`
