# 04 — Repository & Queries

> Hiện thực: `CLAUDE.md` mục "Repository & Query Rules" và "N+1 Query Prevention".

Vì dự án đã cấm hoàn toàn relationship annotation, **N+1 do lazy/eager loading không còn
tồn tại** — Hibernate không có gì để tự đi load ngầm nữa. Đổi lại, việc ghép dữ liệu
chuyển sang tay bạn ở tầng service, và đó chính là chỗ N+1 quay lại nếu viết ẩu.

## 4.1 — Phân trang cho MỌI endpoint trả list

```java
// ❌ SAI — không giới hạn số dòng, và trả entity thô ra ngoài service
List<Seat> getSeats() {
    return this.seatRepository.findAll();
}
```

```java
// ✅ ĐÚNG
Page<SeatResponse> getSeats(final Pageable pageable) {
    return this.seatRepository.findAll(pageable).map(SeatMapper::toResponse);
}
```

**Không có ngoại lệ cho endpoint "lấy tất cả".** Nếu client thật sự cần toàn bộ, bắt nó
phân trang và lặp. Đây từng là quả bom hẹn giờ thật trong `pricing-service` (đã fix ở Nhóm C).

Ngoại lệ hợp lệ duy nhất là `batch-by-ids` (client đã tự giới hạn bằng danh sách ID), nhưng
vẫn nên chặn kích thước đầu vào:

```java
@PostMapping("/batch-by-ids")
public ResponseEntity<ApiResponse<Map<UUID, SeatResponse>>> getSeatsByIds(
        @RequestBody @Size(max = 200) final List<UUID> seatIds) {
    return ResponseEntity.ok(ApiResponse.success(this.seatService.getSeatsByIds(seatIds)));
}
```

## 4.2 — N+1 loại 1: gọi repository trong vòng lặp

Đây là dạng phổ biến nhất sau khi bỏ relationship, vì mọi phép "join" giờ đều do bạn tự viết.

```java
// ❌ SAI — 1 + 2N query
final List<SeatResponse> responses = new ArrayList<>();
for (final Seat seat : seats) {
    final CabinClass cabin = cabinClassRepository.findById(seat.getCabinClassId()).orElse(null);  // N query!
    final SeatInstance inst = seatInstanceRepository.findBySeatId(seat.getId());                  // N query nữa!
    responses.add(SeatMapper.toResponse(seat, cabin, inst));
}
```

```java
// ✅ ĐÚNG — 1 + 2 query, ghép bằng Map trong bộ nhớ
final List<UUID> seatIds = seats.stream().map(Seat::getId).distinct().toList();
final List<UUID> cabinIds = seats.stream().map(Seat::getCabinClassId).distinct().toList();

final Map<UUID, CabinClass> cabinById = this.cabinClassRepository.findAllById(cabinIds)
        .stream().collect(Collectors.toMap(CabinClass::getId, Function.identity()));
final Map<UUID, SeatInstance> instBySeatId = this.seatInstanceRepository.findBySeatIdIn(seatIds)
        .stream().collect(Collectors.toMap(SeatInstance::getSeatId, Function.identity()));

final List<SeatResponse> responses = new ArrayList<>(seats.size());
for (final Seat seat : seats) {
    responses.add(SeatMapper.toResponse(
            seat, cabinById.get(seat.getCabinClassId()), instBySeatId.get(seat.getId())));
}
```

### Công thức chuẩn — thuộc lòng 4 bước

1. Query danh sách cha (có phân trang).
2. Trích `List<UUID>` các ID cần tra cứu, nhớ `.distinct()`.
3. Một query `...In(ids)` cho **mỗi loại** dữ liệu con, gom về `Map<UUID, T>`.
4. Lặp qua cha, tra `Map` bằng `get()`, **không đụng repository nữa**.

Số query luôn là **hằng số** theo số loại quan hệ, không phụ thuộc số dòng.

Bản mẫu chạy thật trong repo: [`FareServiceImpl.toResponses()`](../../services/pricing-service/src/main/java/com/nitditto/service/impl/FareServiceImpl.java).

## 4.3 — Dữ liệu tổng hợp: aggregate query, đừng tính từng dòng

```java
// ❌ SAI
for (final Flight flight : flights) {
    final long count = seatRepository.countByFlightId(flight.getId());   // N query!
}
```

```java
// ✅ ĐÚNG — 1 query gom nhóm, dùng DTO projection cho khỏi ép kiểu Object[]
public record SeatCountByFlight(UUID flightId, Long total) {}

@Query("""
        select new com.nitditto.payload.projection.SeatCountByFlight(s.flightId, count(s))
        from Seat s where s.flightId in :flightIds group by s.flightId
        """)
List<SeatCountByFlight> countByFlightIds(@Param("flightIds") List<UUID> flightIds);
```

## 4.4 — Kiểm tra tồn tại: `existsBy*`, không `findBy*` rồi check

```java
// ❌ SAI — kéo cả entity (hoặc cả List entity) về chỉ để biết có tồn tại hay không
if (seatRepository.findByCode(code).isPresent()) { ... }
if (seatRepository.countByCode(code) > 0) { ... }
if (!aircraftRepository.findByAirlineId(airlineId).isEmpty()) { ... }
```

```java
// ✅ ĐÚNG — SQL chỉ trả về boolean
if (this.seatRepository.existsByCode(code)) { ... }
if (this.aircraftRepository.existsByAirlineId(airlineId)) { ... }
```

> **Vi phạm còn tồn trong repo:** `AirlineServiceImpl.deleteAirline()` đang dùng
> `aircraftRepository.findByAirlineId(...).isEmpty()` để kiểm tra tồn tại — load toàn bộ
> `Aircraft` của hãng về chỉ để hỏi "có cái nào không". `AircraftRepository` hiện chưa có
> `existsByAirlineId`. Nhỏ, nhưng đúng dạng cần sửa; đừng nhân bản pattern này sang service mới.

**Luồng update:** dùng biến thể `*AndIdNot` để loại chính bản ghi đang sửa ra khỏi phép
kiểm tra trùng — repo đang làm đúng ở cả 5 service, nhân rộng cho mọi unique check:

```java
if (this.seatRepository.existsByCodeAndIdNot(code, seat.getId())) {
    throw new BusinessException("Seat code already exists");
}
```

## 4.5 — Ghi hàng loạt: `saveAll()`, không `save()` trong vòng lặp

```java
// ❌ SAI — N lần round-trip tới DB
for (final Seat seat : seats) {
    seatRepository.save(seat);
}
```

```java
// ✅ ĐÚNG — 1 lần, Hibernate gom thành batch insert
this.seatRepository.saveAll(seats);
```

Chỉ thật sự batch khi `application.yaml` đã bật `jdbc.batch_size` + `order_inserts` +
`order_updates` (xem [trang 02](02-new-service-checklist.md)).

Với **>5.000 bản ghi**, chia lô và `flush()`/`clear()` sau mỗi lô để tránh phình heap —
`saveAll()` một danh sách rất lớn vẫn giữ toàn bộ entity trong persistence context.

Đây là kịch bản rất thật với `seat-service`: sinh sơ đồ ghế cho một tàu bay 300 chỗ,
hoặc sinh `SeatInstance` cho mọi chuyến bay trong tháng.

## 4.6 — Chỉ đọc vài cột thì dùng projection

```java
// ❌ SAI — load cả entity chỉ để lấy 2 field
final List<Seat> seats = seatRepository.findByCabinClassId(cabinClassId);
```

```java
// ✅ ĐÚNG — interface projection, SQL chỉ select đúng 2 cột
public interface SeatSummary {
    String getSeatNumber();
    Boolean getIsWindow();
}

List<SeatSummary> findByCabinClassId(UUID cabinClassId);
```

Đặc biệt đáng giá với entity có nhiều khối `@Embedded` (như `Fare` có 5 khối benefit) —
load đầy đủ chỉ để đọc giá là rất lãng phí.

## 4.7 — Filter động: `Specification` thay cho chuỗi `(:param is null or ...)`

```java
// ❌ TRÁNH khi số filter ≥ 3 — DB không dùng được index vì điều kiện "is null" chặn,
//    và thêm một filter là phải sửa cả câu JPQL
@Query("""
        select s from Seat s
        where (:cabinClassId is null or s.cabinClassId = :cabinClassId)
          and (:isWindow is null or s.isWindow = :isWindow)
          and (:isAvailable is null or s.isAvailable = :isAvailable)
        """)
Page<Seat> search(...);
```

```java
// ✅ ĐÚNG
public interface SeatRepository extends JpaRepository<Seat, UUID>, JpaSpecificationExecutor<Seat> { }

public final class SeatSpecs {

    private SeatSpecs() { }

    public static Specification<Seat> cabinClassId(final UUID cabinClassId) {
        return (root, query, cb) ->
                cabinClassId == null ? null : cb.equal(root.get("cabinClassId"), cabinClassId);
    }

    public static Specification<Seat> isWindow(final Boolean isWindow) {
        return (root, query, cb) ->
                isWindow == null ? null : cb.equal(root.get("isWindow"), isWindow);
    }
}
```

```java
// Ghép ở tầng service — trả null nghĩa là "bỏ qua điều kiện này", Spring Data tự loại
// khỏi WHERE nên SQL sinh ra chỉ chứa filter thực sự có giá trị
final Specification<Seat> spec = Specification
        .where(SeatSpecs.cabinClassId(cabinClassId))
        .and(SeatSpecs.isWindow(isWindow));

return this.seatRepository.findAll(spec, pageable).map(SeatMapper::toResponse);
```

Đặt `*Specs` trong `repository/spec/`, là `final class` với constructor private (chỉ chứa
static factory method, **không phải bean**).

> Hiện repo **chưa có** `*Specs` nào — `FlightRepository`/`FlightInstanceRepository` vẫn
> dùng pattern `(:param is null or ...)`. Đây là mẫu chuẩn cho service mới, không phải mô
> tả code đang có.

## 4.8 — Repository chỉ chứa truy vấn

Repository là Spring Data JPA thuần: derived query theo tên method, `@Query` khi cần,
`Specification` cho filter động. **Không** đặt business logic, **không** mapping Entity↔DTO,
**không** gọi service khác từ repository.

## Checklist repository

- [ ] Endpoint trả list đã `Page<T>` + `Pageable`
- [ ] Không có `repository.*` nào nằm trong `for` / `stream().map()` / `forEach`
- [ ] Mỗi loại dữ liệu con đúng **một** query `...In(ids)`, danh sách ID đã `.distinct()`
- [ ] Kiểm tra tồn tại dùng `existsBy*`, không `findBy*`/`countBy*`
- [ ] Ghi hàng loạt dùng `saveAll()`
- [ ] Cột trong mọi derived query mới đã có `@Index` tương ứng
- [ ] Đã bật SQL log và **đếm số dòng SQL thực tế** cho endpoint trả list
