# Hoàn tiền khi huỷ booking

> Phạm vi: khách huỷ một booking **đã thanh toán** (`CONFIRMED`). Hiện thực: `booking-service` (quyết định số tiền) +
> `payment-service` (ghi nhận và thực hiện với cổng thanh toán). Chỉ hỗ trợ **VNPay**.

## 1. Luồng

```
Khách ──PATCH /api/bookings/{id}/cancel──► booking-service (KHÔNG chạy trong transaction DB)
   1. Chỉ chủ booking được huỷ.
   2. Booking CONFIRMED: lấy payment (payment-service), FareRules (pricing-service), giờ bay (flight-ops-service)
      → RefundPolicy quyết định số tiền hoàn.
   3. Số tiền > 0: POST /api/payments/refund (payment-service, Feign nội bộ)
        payment-service: ghi Refund PENDING (commit) → gọi API hoàn tiền VNPay → ghi SUCCESS/FAILED (commit)
   4. Đổi booking CANCELLED + huỷ vé (transaction riêng) → nhả ghế → trả response kèm refundAmount/refundStatus/refundNote.
```

Thứ tự **hoàn tiền trước, huỷ sau** là chủ ý. Nếu payment-service không ghi nhận được yêu cầu, booking giữ nguyên và khách
thử lại; yêu cầu hoàn là idempotent theo payment nên thử lại sau khi lỡ commit huỷ vẫn an toàn. Đảo thứ tự thì crash giữa
hai bước làm mất yêu cầu hoàn tiền mà không còn cách nào kích hoạt lại (booking đã `CANCELLED`).

## 2. Chính sách (`RefundPolicy`, lấy từ `FareRules`)

Cùng các thông số frontend đang hiển thị cho khách (`isRefundable`, `cancellationFee`, `refundDeadlineDays`).

| Tình huống | Kết quả |
|---|---|
| Fare không có rules, hoặc `isRefundable` ≠ true | **Cho huỷ, không hoàn** |
| Chuyến đã khởi hành | Cho huỷ, không hoàn |
| Quá hạn (`refundDeadlineDays` ngày trước giờ bay; biên **được** hoàn) | Cho huỷ, không hoàn |
| Không có hạn (`null`/`0`) | Hoàn đến lúc khởi hành |
| Còn lại | Hoàn `đã thanh toán − cancellationFee`, làm tròn số nguyên; phí ≥ số đã trả thì không hoàn |

- `cancellationFee` được hiểu là **số tiền cố định** cùng đơn vị với fare (không phải %), `null` = 0.
- Giờ bay tính theo **múi giờ sân bay đi** (mặc định `Asia/Ho_Chi_Minh` nếu thiếu).
- Booking chưa thanh toán (`PENDING`) khi huỷ: không có gì để hoàn, chỉ nhả ghế.
- Lỗi khi tra pricing/flight/payment **không** bị coi là "không có rules": huỷ thất bại để thử lại, không đoán chính sách.

## 3. Thiết kế an toàn cho tiền

- **Một payment chỉ có tối đa một refund** (`refunds.payment_id` unique). Gọi lặp trả lại yêu cầu cũ; hai request song song
  chỉ gọi cổng đúng một lần (đã có test chạy trên MySQL thật). Số tiền khác với refund đã có bị từ chối.
- **Không bao giờ giữ transaction DB trong lúc gọi cổng**: ghi nhận (commit) → gọi cổng → ghi kết quả (commit).
- Kết quả cổng được phân loại:
  - `SUCCESS` (mã `00`): refund `SUCCESS`, payment `REFUNDED`.
  - `REJECTED` (mã khác `00`, hoặc VNPay chưa cấu hình): **chắc chắn chưa hoàn**, refund `FAILED`, retry tự do.
  - `UNKNOWN` (timeout, lỗi mạng, phản hồi sai chữ ký/không đọc được): **tiền có thể đã được hoàn**. Refund `FAILED` với
    `outcome_unknown = true`, lý do bắt đầu bằng `UNKNOWN OUTCOME`.
- **Không có retry tự động.** Hệ thống chưa kiểm chứng được cách VNPay xử lý yêu cầu trùng, nên không tự gọi lại khi kết quả
  không rõ. Refund `PENDING` bị bỏ dở (tiến trình chết giữa lúc gọi cổng) cũng không tự chạy lại.
- Huỷ booking **vẫn hoàn tất** khi cổng lỗi: khách nhận `refundStatus = FAILED` kèm ghi chú "xử lý thủ công".

## 4. Vận hành: xử lý refund thất bại

Các endpoint dưới `/api/payments/` có chứa `refund` **chỉ ADMIN** gọi được qua gateway (mọi method); booking-service gọi
nội bộ qua Feign.

1. Liệt kê: `GET /api/payments/refunds?status=FAILED`.
2. Xem `failureReason`. Nếu bắt đầu bằng `UNKNOWN OUTCOME`: **vào cổng merchant của VNPay kiểm tra giao dịch** đã được hoàn chưa.
3. Retry: `POST /api/payments/refunds/{id}/retry`. Với `UNKNOWN OUTCOME` (và refund `PENDING` bị bỏ dở) phải thêm
   `?confirmNotRefunded=true` sau khi đã xác nhận ở cổng là **chưa hoàn**. Thiếu tham số này bị từ chối và không gọi cổng.
4. Nếu đã hoàn ở cổng nhưng hệ thống ghi `FAILED`: cần sửa tay trạng thái refund/payment (chưa có endpoint).

## 5. Cấu hình (`payment-service`)

| Biến môi trường | Mặc định | Ý nghĩa |
|---|---|---|
| `VNPAY_REFUND_URL` | `https://sandbox.vnpayment.vn/merchant_webapi/api/transaction` | Endpoint hoàn tiền |
| `VNPAY_REFUND_VERIFY_RESPONSE` | `true` | Kiểm tra chữ ký phản hồi; chỉ tắt khi định dạng thực tế khác tài liệu và đã đối chiếu thủ công |
| `vnpay.refund-ip-addr` | `127.0.0.1` | `vnp_IpAddr` gửi lên |
| `vnpay.http.connect-timeout-ms` / `read-timeout-ms` | `3000` / `15000` | Hết giờ = kết quả `UNKNOWN` |

DB: bảng `refunds` mới và cột `payments.provider_create_date` (do `ddl-auto: update` tự tạo ở dev). VNPay đòi lại đúng
`vnp_CreateDate` của giao dịch gốc nên giá trị này được lưu lúc tạo link thanh toán. Payment tạo **trước** khi có cột này suy
ra từ `created_at` (có thể lệch vài giây và bị VNPay từ chối, khi đó refund `FAILED` để xử lý tay).

## 6. Giới hạn đã biết

- **Chưa kiểm chứng với VNPay thật.** Thứ tự trường để ký, endpoint, `vnp_TransactionType` (`02` toàn phần / `03` một phần),
  và cách xử lý yêu cầu trùng đều lấy từ tài liệu/thư viện công khai. Sandbox của VNPay **hạn chế hoàn tiền** và cần VNPay mở
  cho merchant. Test hiện có (và E2E với cổng VNPay giả) chứng minh code nhất quán với tài liệu, không chứng minh VNPay thật chấp nhận.
- Hỗ trợ **VNPay** và **Razorpay** (xem phần "Hoàn tiền Razorpay" cuối tài liệu). Payment Razorpay tạo trước khi có số quy đổi tiền tệ: refund ghi nhận rồi `FAILED`, hoàn tay.
- Chỉ hoàn khi **khách huỷ**. Chưa tự hoàn khi: hãng huỷ chuyến, thanh toán thành công **sau khi** booking đã `CANCELLED`/`EXPIRED`
  (hiện ghi log để xử lý tay), hoặc đổi vé.
- Chưa gửi thông báo cho khách khi hoàn tiền; chưa có audit log riêng ngoài bảng `refunds`.
- Số tiền dùng `Double` (VND nguyên) theo quyết định hiện tại của dự án.

## 8. Hoàn tiền Razorpay

Payment Razorpay thu INR (số đã quy đổi từ VND lúc tạo link, xem `docs/features/payment-service.md`), nên hoàn **theo tỷ lệ trên số INR đã thu** chứ không quy đổi lại theo tỷ giá hôm nay: hoàn toàn bộ trả đúng số INR đã thu, hoàn một phần trả `đã thu × (VND hoàn / VND đã trả)`. Phân loại kết quả như VNPay: HTTP 4xx (kể cả `BAD_REQUEST_ERROR`) là `REJECTED` (chưa hoàn, retry tự do); 5xx/timeout/lỗi mạng là `UNKNOWN` (tiền có thể đã hoàn: admin xác nhận ở cổng rồi retry với `confirmNotRefunded=true`). Đã thử với Razorpay test mode: payment không tồn tại trả 404 và được xếp `REJECTED`; chưa thử hoàn một payment đã capture thật.
