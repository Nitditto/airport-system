# Dữ liệu mẫu (`docker/mysql/seed/`)

`001-sample-data.sql` nạp dữ liệu mẫu chuẩn cho **mọi service** để chạy được luồng Tìm chuyến → Chọn ghế → Đặt vé → Thanh toán
mà không phải tạo tay. Giống `migrations/`: chạy tay, **không** được mount vào `docker-entrypoint-initdb.d` (schema do Hibernate
`ddl-auto: update` tạo lúc service khởi động nên seed phải chạy **sau** đó).

## Chạy

Điều kiện: mọi service đã khởi động ít nhất một lần (để có bảng) và DB **trống** (compose mới hoặc sau `docker compose down -v`).

```powershell
# Docker compose đầy đủ (container airline-mysql)
Get-Content docker/mysql/seed/001-sample-data.sql -Raw -Encoding UTF8 |
  docker exec -i airline-mysql mysql -uroot -p123456 --default-character-set=utf8mb4 -t

# Hạ tầng dev (docker-compose.dev.yml, container airline-mysql-dev)
Get-Content docker/mysql/seed/001-sample-data.sql -Raw -Encoding UTF8 |
  docker exec -i airline-mysql-dev mysql -uroot -p123456 --default-character-set=utf8mb4 -t
```

```bash
docker exec -i airline-mysql mysql -uroot -p123456 --default-character-set=utf8mb4 -t < docker/mysql/seed/001-sample-data.sql
```

Mất vài giây. Cuối script in số dòng đã nạp (`flight_instances`, `seats`, `seat_instances`, `fares`).
Mật khẩu root theo `MYSQL_ROOT_PASSWORD` (mặc định `123456`).

## Nội dung

| Service / DB | Dữ liệu |
|---|---|
| `user-service` (`airline_user`) | 7 tài khoản, mật khẩu chung **`Password@123`** (chỉ dev): `admin@airline.local` (SYSTEM_ADMIN), `owner.vn\|vj\|qh\|vu@airline.local` (AIRLINE_OWNER, mỗi người sở hữu một hãng), `customer1\|2@airline.local` (USER) |
| `location-service` (`airline_location`) | 12 thành phố, 13 sân bay (HAN, SGN, DAD, PQC, CXR, DLI, HUI, HPH, BKK, SIN, NRT, HND, ICN) kèm múi giờ |
| `airline-core-service` (`airline`) | 4 hãng (VN, VJ, QH, VU; VN thuộc SkyTeam), 8 máy bay A321 (VN/QH có thương gia 12 + phổ thông 168 ghế, VJ/VU phổ thông 186 ghế) |
| `seat-service` (`airline_seat`) | hạng ghế + sơ đồ ghế + 1.464 ghế (thương gia 2-2, phổ thông 3-3, hàng 12-13 là lối thoát hiểm, phụ thu chọn chỗ theo loại ghế), cabin + ~115.000 seat instance cho từng chuyến |
| `flight-ops-service` (`airline_flight`) | 23 chuyến bay (HAN/SGN/DAD/PQC/DLI/BKK) và lịch bay; **630 chuyến cụ thể trong 30 ngày tới** (ngày mai trở đi, theo `operating_days`) |
| `pricing-service` (`airline_pricing`) | 57 fare (Tiết kiệm / Linh hoạt / Thương gia) kèm quy tắc hoàn-đổi và chính sách hành lý |
| `ancillary-service` (`airline_ancillary`) | 20 suất ăn, 14 dịch vụ thêm (hành lý 10/20/30kg theo hãng, 2 gói bảo hiểm) + 10 quyền lợi bảo hiểm, suất ăn và dịch vụ theo từng chuyến/hạng ghế |

Giá VND, số nguyên. `total = base_fare + taxed_and_fees + airline_fees`. Ví dụ HAN → SGN: VJ Tiết kiệm 1.369.000, QH 1.642.000,
VN 1.729.000; thương gia QH 5.294.000, VN 5.579.000.

Quy tắc hoàn vé (khớp `RefundPolicy`): **Tiết kiệm** không hoàn; **Linh hoạt** hoàn trừ phí 300.000, huỷ trước 3 ngày; **Thương gia** hoàn đủ,
huỷ trước 1 ngày.

## Idempotent và an toàn

- Id cố định có tiền tố `5eed` (`5eed` + mã loại + số thứ tự) cho dữ liệu gốc; id dẫn xuất bằng `MD5` cho dữ liệu sinh hàng loạt
  (ghế, chuyến, cabin, seat instance...). Câu `INSERT ... VALUES` dùng `ON DUPLICATE KEY UPDATE id = id`, câu `INSERT ... SELECT`
  dùng `INSERT IGNORE` (cú pháp trước bị MySQL báo cột `id` mơ hồ). **Chạy lại không nhân đôi dữ liệu**; chạy lại ở ngày khác sinh
  thêm chuyến cho các ngày mới trong cửa sổ 30 ngày.
- `INSERT IGNORE` nuốt cả lỗi dữ liệu khác: sau khi sửa script, đối chiếu số dòng in ở cuối với kỳ vọng.
- **Không chạy trên DB có dữ liệu thật**: hãng/sân bay trùng mã IATA bị bỏ qua nên chuyến sẽ trỏ tới id không tồn tại.
- Seed bỏ qua Kafka: cabin và seat instance được sinh trực tiếp bằng SQL theo cùng quy tắc `FlightInstanceCabinServiceImpl`
  (thay cho sự kiện `flight.instance.created`). Đổi quy tắc ở service thì phải sửa seed cho khớp.
- Gắn với tên cột hiện tại: đổi entity (đổi tên/kiểu cột) thì sửa seed. Đổi kiểu cột đã có cần migration thủ công (xem `migrations/`).

## Mở rộng

- Thêm hãng/chuyến: thêm dòng vào các `INSERT ... VALUES` tương ứng, giữ mã loại id (`5eed0001` thành phố, `0002` sân bay, `0003` user,
  `0004` hãng, `0005` máy bay, `0006` hạng ghế, `0007` sơ đồ ghế, `0008` chuyến bay, `0009` lịch bay, `000a` fare, `000b` fare rules,
  `000c` hành lý, `000d` dịch vụ thêm, `000e` bảo hiểm, `000f` suất ăn). Các câu `INSERT ... SELECT` lọc theo tiền tố này.
- Chuyến bay quốc tế chỉ có SGN ↔ BKK (cùng múi giờ UTC+7): thời lượng hiển thị tính trực tiếp từ giờ đi/đến nên điểm đến khác
  múi giờ sẽ hiện sai thời lượng.
- Muốn dọn dữ liệu mẫu: `docker compose down -v` (xoá volume MySQL) rồi chạy lại.

## Kiểm tra nhanh

```sql
-- mọi số trong kết quả này phải bằng 0
SELECT COUNT(*) AS instances_without_cabin FROM airline_flight.flight_instances fi
 WHERE NOT EXISTS (SELECT 1 FROM airline_seat.flight_instance_cabins x WHERE x.flight_instance_id = fi.id);
SELECT COUNT(*) AS fares_without_rules FROM airline_pricing.fares f
 WHERE NOT EXISTS (SELECT 1 FROM airline_pricing.fare_rules r WHERE r.fare_id = f.id);
```

Qua gateway (không cần JWT): `GET /api/flights/search?departureAirportId=<HAN>&arrivalAirportId=<SGN>&departureDate=<ngày mai>&passengers=2&cabinClass=ECONOMY`.
ID sân bay mẫu: HAN `5eed0002-0000-0000-0000-000000000001`, SGN `5eed0002-0000-0000-0000-000000000002`.
