# Tìm chuyến bay (`GET /api/flights/search`)

**Code:** `flight-ops-service` — `FlightSearchController`, `FlightSearchServiceImpl`, `FlightInstanceSpecs`, `FlightInstanceAssembler`.
Công khai (gateway `PUBLIC_READS`, không cần JWT). Endpoint cũ `GET /api/flight-instances/search` còn giữ nhưng FE không dùng.

## Tham số

| Tham số | Bắt buộc | Ghi chú |
|---|---|---|
| `departureAirportId`, `arrivalAirportId` | ✅ | khác nhau (400 nếu trùng); sân bay đi không tồn tại → 404 |
| `departureDate` | ✅ | `yyyy-MM-dd`, chuyến khởi hành trong ngày này (giờ địa phương sân bay đi) và sau thời điểm hiện tại ở múi giờ đó |
| `passengers` | ✅ | 1–9, lọc `availableSeats >= passengers` |
| `cabinClass` | ✅ | `ECONOMY`, `PREMIUM_ECONOMY`, `BUSINESS`, `FIRST` |
| `airlines` | | danh sách UUID hãng |
| `minPrice`, `maxPrice` | | so với `fare.totalPrice`; `min > max` → 400 |
| `departureTimeRange`, `arrivalTimeRange` | | `any`, `morning` (6–11h), `afternoon` (12–17h), `evening` (18–20h), `night` (21–5h) |
| `maxDuration` | | phút |
| `alliance` | | không phân biệt hoa thường, so với `AirlineResponse.alliance` |
| `sortBy`, `sortOrder` | | `price`, `duration`, `departure` (mặc định), `arrival`; `asc` (mặc định) / `desc`; giá trị lạ → 400 |
| `page`, `size` | | `size` tối đa 100 |

Mỗi phần tử trả về là `FlightInstanceResponse` đã có `fare` (fare rẻ nhất của hạng ghế đã chọn).

## Luồng xử lý

1. `locationClient.getAirportById` → múi giờ sân bay đi → `now` địa phương.
2. DB (`Specification`): chuyến `SCHEDULED`, `isActive`, đúng cặp sân bay, trong ngày và sau `now`, đủ ghế, hãng, khung giờ. Lấy tối đa **200** chuyến ứng viên (sắp theo giờ khởi hành).
3. `flightRepository.findAllById` → `Flight` để biết `aircraftId`.
4. seat-service: **một** call `POST /api/cabin-classes/batch-by-aircraft-ids?cabinClass=` → hạng còn bán của từng máy bay.
5. pricing-service: nhóm chuyến theo `cabinClassId`, **mỗi hạng ghế một** call `POST /api/fares/search?cabinClassId=` → fare rẻ nhất. Chuyến thiếu hạng ghế hoặc fare bị loại.
6. Trong bộ nhớ: lọc giá, thời lượng, alliance (chỉ nạp hãng khi có tham số alliance, một call batch).
7. Sắp xếp (tiêu chí phụ: giờ khởi hành, để thứ tự ổn định giữa các trang) và phân trang → `totalElements` đúng.
8. Chỉ trang trả về mới được dựng response: `FlightInstanceAssembler` nạp hãng/máy bay/sân bay bằng batch (`ReferenceDataLoader`).

Số lời gọi sang service khác là hằng số theo số loại dữ liệu / số hạng ghế, không theo số chuyến (CLAUDE.md §7, §8). Service này không dùng `@Transactional` để không giữ connection DB trong lúc gọi Feign.

## Lỗi

| Tình huống | Kết quả |
|---|---|
| Thiếu/sai kiểu tham số, validate, `sortBy` lạ, `min > max`, hai sân bay trùng | 400 (lỗi ép kiểu chỉ trả `invalid value`, không lộ tên class) |
| Sân bay đi không tồn tại | 404 |
| seat/pricing/location không kết nối được | 503; lỗi khác từ upstream 502 (xử lý chung ở `GlobalExceptionHandler`) |

## Giới hạn

- Tối đa 200 chuyến ứng viên mỗi ngày mỗi tuyến; vượt thì cắt theo giờ khởi hành và ghi `warn`.
- Giá/sort/phân trang trong bộ nhớ, nên chi phí tăng theo số ứng viên, không theo `size`.
- Lọc ghế theo `availableSeats` tổng của chuyến, chưa theo từng hạng ghế.
- Chưa có cache. Hướng mở rộng (cần hỏi trước, CLAUDE.md §2): CQRS + search engine (Elasticsearch/Redis) với read model đồng bộ qua Kafka (`flight.instance.created`, đổi giá vé), search thẳng vào index — bỏ giới hạn 200 và phân trang trong bộ nhớ.

## Kiểm thử

- `FlightSearchServiceImplTest` (số call cố định, lọc, sort, phân trang, validate), `GatewayJwtE2ETest` (route công khai, POST vẫn 401).
- Đã chạy stack thật qua gateway với 20 ca (không JWT): lọc giá/hãng/alliance/khung giờ/thời lượng, sắp xếp, phân trang, 400/404, endpoint cũ vẫn 200.
