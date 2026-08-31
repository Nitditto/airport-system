# 06 — API Layer

> Hiện thực: `CLAUDE.md` mục "API Response Wrapper", "Validation", "Cross-Service Communication".

## 6.1 — Controller chuẩn (bản mẫu đầy đủ)

Mẫu thật để copy: [`FareController.java`](../../services/pricing-service/src/main/java/com/nitditto/controller/FareController.java).

```java
// ✅ ĐÚNG
@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;                 // interface, không phải Impl

    @PostMapping
    public ResponseEntity<ApiResponse<SeatResponse>> createSeat(
            @Valid @RequestBody final SeatRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(this.seatService.createSeat(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<SeatResponse>>> getSeats(final Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(this.seatService.getSeats(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SeatResponse>> getSeatById(
            @PathVariable("id") final UUID id) {
        return ResponseEntity.ok(ApiResponse.success(this.seatService.getSeatById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SeatResponse>> updateSeat(
            @PathVariable("id") final UUID id,
            @Valid @RequestBody final SeatRequest request) {          // @Valid cả ở update!
        return ResponseEntity.ok(ApiResponse.success(this.seatService.updateSeat(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSeat(@PathVariable("id") final UUID id) {
        this.seatService.deleteSeat(id);
        return ResponseEntity.noContent().build();          // 204, KHÔNG bọc ApiResponse
    }

    @PostMapping("/batch-by-ids")
    public ResponseEntity<ApiResponse<Map<UUID, SeatResponse>>> getSeatsByIds(
            @RequestBody @Size(max = 200) final List<UUID> seatIds) {
        return ResponseEntity.ok(ApiResponse.success(this.seatService.getSeatsByIds(seatIds)));
    }
}
```

## 6.2 — `ApiResponse<T>` cho MỌI endpoint trả dữ liệu

```java
// ❌ SAI — trả thẳng DTO, client phải xử lý 2 format khác nhau tuỳ endpoint
@GetMapping("/{id}")
public SeatResponse getSeatById(@PathVariable UUID id) {
    return seatService.getSeatById(id);
}
```

```java
// ✅ ĐÚNG
@GetMapping("/{id}")
public ResponseEntity<ApiResponse<SeatResponse>> getSeatById(@PathVariable("id") final UUID id) {
    return ResponseEntity.ok(ApiResponse.success(this.seatService.getSeatById(id)));
}
```

Format thống nhất client nhận được:

```json
{ "success": true,  "message": null,              "data": { ... } }
{ "success": false, "message": "Seat not found with id: ...", "data": null }
```

**Ngoại lệ duy nhất: `DELETE` trả `204 No Content` không body** — theo spec HTTP, 204
không nên có body, nên không bọc `ApiResponse`.

Đây là điểm dễ làm nửa vời nhất: một số endpoint bọc, số khác trả thẳng DTO. **Không chấp
nhận được** — client sẽ phải viết 2 nhánh parse.

## 6.3 — `@Valid` là bắt buộc, đặc biệt ở `update`

```java
// ❌ SAI — thiếu @Valid, dữ liệu rác vào thẳng DB
@PutMapping("/{id}")
public ResponseEntity<ApiResponse<SeatResponse>> updateSeat(
        @PathVariable final UUID id, @RequestBody final SeatRequest request) { ... }
```

```java
// ✅ ĐÚNG
@PutMapping("/{id}")
public ResponseEntity<ApiResponse<SeatResponse>> updateSeat(
        @PathVariable("id") final UUID id,
        @Valid @RequestBody final SeatRequest request) { ... }
```

Đây là lỗi đã tìm thấy ở 4 controller khác nhau trong repo (đều ở method `update`, vì
lúc viết `create` thì nhớ mà copy sang `update` thì quên). **Rà cả `create` lẫn `update`.**

Request DTO đặt validation bằng `jakarta.validation.constraints`:

```java
public class SeatRequest {

    @NotBlank(message = "Seat number is required")
    private String seatNumber;

    @NotNull(message = "Cabin class ID is required")
    private UUID cabinClassId;

    @Positive(message = "Row number must be positive")
    private Integer rowNumber;

    private Double surcharge;      // nullable có chủ đích → mapper PHẢI null-safe (xem 05)
}
```

> **Liên đới quan trọng:** field **không** có `@NotNull` nghĩa là bạn tự cam kết mọi chỗ
> dùng nó đều xử lý được `null`. Đây chính là gốc của 2 bug NPE thật trong repo.

## 6.4 — HTTP status đúng ngữ nghĩa

| Tình huống | Status | Cách trả |
|---|---|---|
| Tạo mới thành công | `201 Created` | `ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(...))` |
| Đọc/sửa thành công | `200 OK` | `ResponseEntity.ok(ApiResponse.success(...))` |
| Xoá thành công | `204 No Content` | `ResponseEntity.noContent().build()` |
| Không tìm thấy | `404` | ném `ResourceNotFoundException` |
| Sai nghiệp vụ / validation | `400` | ném `BusinessException` / `@Valid` tự lo |
| Chưa đăng nhập / không đủ quyền | `401` / `403` | Spring Security tự lo |
| Lỗi không lường trước | `500` | catch-all trong `GlobalExceptionHandler` |

Controller **không tự bắt exception** để đổi status — cứ ném, `GlobalExceptionHandler` lo.

## 6.5 — Naming & REST convention

```
POST   /api/seats                    tạo mới
GET    /api/seats?page=0&size=20     danh sách (phân trang)
GET    /api/seats/{id}               chi tiết
PUT    /api/seats/{id}               cập nhật
DELETE /api/seats/{id}               xoá
POST   /api/seats/batch-by-ids       batch fetch cho service khác
GET    /api/seats/cabin-class/{cabinClassId}    lọc theo quan hệ
```

- Base path: `/api/<danh-từ-số-nhiều-kebab-case>`.
- DTO đặt tên `*Request` (dùng chung cho cả create và update, **không** tách
  `CreateXRequest`/`UpdateXRequest`) và `*Response`.
- DTO nào **từ 2 service trở lên** dùng thì đặt trong `common-lib/payload/`; chỉ 1 service
  dùng thì giữ trong service đó.

## 6.6 — Cross-service: batch endpoint là bắt buộc

Chưa có Feign/service discovery, nên giao tiếp giữa service là **tham chiếu UUID + endpoint
batch-fetch**.

```java
// ❌ SAI — N HTTP call. Tệ hơn N+1 qua DB nhiều lần: mỗi lần là một round-trip TCP
//    + serialize JSON, nhân lên theo độ trễ mạng
for (final Seat seat : seats) {
    final CabinClassResponse cabin = cabinClient.getById(seat.getCabinClassId());
    responses.add(SeatMapper.toResponse(seat, cabin));
}
```

```java
// ✅ ĐÚNG — 1 HTTP call batch
final List<UUID> cabinIds = seats.stream()
        .map(Seat::getCabinClassId).distinct().toList();          // nhớ .distinct()
final Map<UUID, CabinClassResponse> cabinById = this.cabinClient.getByIds(cabinIds);

for (final Seat seat : seats) {
    responses.add(SeatMapper.toResponse(seat, cabinById.get(seat.getCabinClassId())));
}
```

**Rule:** mọi service expose dữ liệu cho service khác **bắt buộc** có endpoint
`POST /batch-by-ids` nhận `List<UUID>`, trả `Map<UUID, XxxResponse>`.

**Không bao giờ** map sang entity của service khác (khác database, vỡ ngay khi chạy) — chỉ
giữ `UUID` đặt tên `<entity>Id`.

> **Nợ kỹ thuật đã biết:** cho tới khi module `cloud/` (Eureka + Gateway) được dựng, các
> lời gọi HTTP còn phải hardcode `localhost:<port>`. Khi có Gateway, thay bằng OpenFeign
> client đặt trong `common-lib` hoặc package `client/` của service gọi — đừng rải
> `localhost:5006` khắp code, gom vào một chỗ để sau này đổi một lần.

## Checklist API layer

- [ ] Controller inject **interface**, không phải `*ServiceImpl`
- [ ] Mọi `@RequestBody` có `@Valid` — kiểm tra cả `create` **và** `update`
- [ ] Mọi endpoint trả dữ liệu bọc `ApiResponse<T>`; `DELETE` trả `204` không body
- [ ] Endpoint trả list nhận `Pageable`
- [ ] Có `POST /batch-by-ids` nếu service khác cần đọc dữ liệu này
- [ ] `batch-by-ids` đã chặn kích thước đầu vào (`@Size(max = 200)`)
- [ ] Controller không khai `throws Exception`, không tự catch để đổi status
- [ ] Status code đúng ngữ nghĩa (`201` cho create, không phải `200`)
