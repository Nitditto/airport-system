# 05 — Service Layer

> Hiện thực: `CLAUDE.md` mục "Coding Standards", "Exception Handling", "Logging",
> "NO JPA Relationship Annotations / Hệ quả 1 và 2".

## 5.1 — Interface + Impl, inject qua interface

```java
// ❌ SAI — inject thẳng implementation
@RestController
@RequiredArgsConstructor
public class SeatController {
    private final SeatServiceImpl seatService;   // vi phạm Dependency Inversion
}
```

```java
// ✅ ĐÚNG
public interface SeatService {
    SeatResponse getSeatById(UUID id);
    Map<UUID, SeatResponse> getSeatsByIds(List<UUID> ids);
    Page<SeatResponse> getSeats(Pageable pageable);
    SeatResponse createSeat(SeatRequest request);
    SeatResponse updateSeat(UUID id, SeatRequest request);
    void deleteSeat(UUID id);
}

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatServiceImpl implements SeatService {

    private final SeatRepository seatRepository;
    private final CabinClassRepository cabinClassRepository;
}

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seats")
public class SeatController {
    private final SeatService seatService;       // interface, không phải Impl
}
```

Interface nhỏ, gọn theo domain — `SeatService` riêng, `CabinClassService` riêng, đừng gộp
hai domain vào một interface.

## 5.2 — `@Transactional` dùng nhất quán

```java
// ✅ ĐÚNG — readOnly mặc định cho cả class, ghi đè cho method ghi
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatServiceImpl implements SeatService {

    @Override
    @Transactional                      // ghi đè cho method ghi
    public SeatResponse createSeat(final SeatRequest request) { ... }

    @Override
    public SeatResponse getSeatById(final UUID id) { ... }   // dùng readOnly của class
}
```

- `readOnly = true` cho method đọc: Hibernate bỏ qua dirty checking, nhanh hơn và tránh vô tình ghi.
- **Bắt buộc** `@Transactional` cho mọi method ghi đụng từ 2 bảng trở lên — đặc biệt các
  luồng xoá cha-con thủ công, vì không còn `cascade` để dựa vào.

### Cái bẫy self-invocation

```java
// ❌ SAI — gọi nội bộ trong cùng class BỎ QUA proxy, KHÔNG mở transaction
@Service
public class SeatServiceImpl implements SeatService {

    public void importSeats(final List<SeatRequest> requests) {
        this.saveBatch(requests);        // @Transactional bên dưới VÔ HIỆU
    }

    @Transactional
    public void saveBatch(final List<SeatRequest> requests) { ... }
}
```

`@Transactional` chỉ có tác dụng khi được gọi từ **bên ngoài** bean. Nếu cần, tách sang bean khác.

## 5.3 — Exception: ném cụ thể, không `throw new Exception`

```java
// ❌ SAI — pattern cũ toàn repo trước Nhóm D
public Seat getSeatById(Long id) throws Exception {
    return seatRepository.findById(id)
            .orElseThrow(() -> new Exception("Seat not found"));
}
```

```java
// ✅ ĐÚNG
@Override
public SeatResponse getSeatById(final UUID id) {
    final Seat seat = this.seatRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Seat", "id", id));
    return SeatMapper.toResponse(seat);
}

@Override
@Transactional
public SeatResponse createSeat(final SeatRequest request) {
    if (this.seatRepository.existsByCabinClassIdAndSeatNumber(
            request.getCabinClassId(), request.getSeatNumber())) {
        throw new BusinessException("Seat number already exists in this cabin class");
    }
    ...
}
```

Hai exception này nằm trong `common-lib/exception/`, dùng chung cho mọi service:

| Exception | HTTP status | Dùng khi |
|---|---|---|
| `ResourceNotFoundException(resource, field, value)` | 404 | Tìm theo id/code mà không có |
| `BusinessException(message)` | 400 | Vi phạm quy tắc nghiệp vụ (trùng, xung đột trạng thái, còn con) |

- **Không catch `Exception` chung chung** ở tầng service để nuốt lỗi.
- **Không return `null`** cho method tìm theo id — dùng `Optional` ở repository, ném exception ở service.
- Controller **không khai `throws Exception`** — `GlobalExceptionHandler` lo hết.

## 5.4 — Xoá cha-con: tự chặn hoặc tự xoá (không còn `cascade`)

Gỡ relationship đồng nghĩa mất luôn FK constraint thật ở MySQL từng chặn việc này.
Có **hai** cách xử lý, chọn theo ngữ nghĩa nghiệp vụ:

### Cách A — CHẶN, khi con là dữ liệu độc lập có giá trị riêng

```java
// ✅ ĐÚNG — mẫu thật từ AirlineServiceImpl
@Override
@Transactional
public void deleteCabinClass(final UUID id) {
    final CabinClass cabinClass = this.cabinClassRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("CabinClass", "id", id));

    // Gỡ @OneToMany khỏi CabinClass đồng nghĩa mất luôn FK constraint thật ở MySQL từng
    // chặn việc này — phải tự kiểm tra để tránh Seat mồ côi trỏ vào cabinClassId không còn.
    if (this.seatRepository.existsByCabinClassId(id)) {
        throw new BusinessException("Cannot delete cabin class that still has seats");
    }

    this.cabinClassRepository.delete(cabinClass);
    log.info("Deleted cabin class id={}", id);
}
```

### Cách B — XOÁ THEO, khi con vô nghĩa nếu không có cha

```java
// ✅ ĐÚNG — mẫu thật từ FareServiceImpl; xoá con trước, cha sau, trong 1 transaction
@Override
@Transactional
public void deleteSeat(final UUID id) {
    if (!this.seatRepository.existsById(id)) {
        throw new ResourceNotFoundException("Seat", "id", id);
    }
    this.seatInstanceRepository.deleteBySeatId(id);   // con trước
    this.seatRepository.deleteById(id);               // cha sau
    log.info("Deleted seat id={}", id);
}
```

**Thiếu `@Transactional` ở đây sẽ để lại dữ liệu mồ côi khi có lỗi giữa chừng.**

## 5.5 — Mapper: hàm static thuần, nhận dữ liệu liên quan qua tham số

```java
// ❌ SAI — mapper tự đi lấy quan hệ (không còn khả thi), hoặc tệ hơn là inject repository
public static SeatResponse toResponse(final Seat seat) {
    return SeatResponse.builder()
            .cabinClass(CabinClassMapper.toResponse(seat.getCabinClass()))   // KHÔNG CÒN
            .build();
}
```

```java
// ✅ ĐÚNG — service load sẵn rồi truyền vào; mapper không phụ thuộc DB
public final class SeatMapper {

    private SeatMapper() { }

    public static SeatResponse toResponse(final Seat seat, final CabinClass cabinClass) {
        if (seat == null) {
            return null;
        }
        return SeatResponse.builder()
                .id(seat.getId())
                .seatNumber(seat.getSeatNumber())
                .cabinClassId(seat.getCabinClassId())
                .cabinClassName(cabinClass != null ? cabinClass.getName() : null)
                .build();
    }
}
```

- **Không inject repository/service vào mapper.** Nếu mapper cần đi query thì đó là dấu
  hiệu logic đặt sai chỗ, phải đẩy lên service.
- Luôn `if (x == null) return null;` ở đầu — mapper thường xuyên nhận dữ liệu con vắng mặt.
- Nếu số tham số vượt 3, gói lại thành một record ngữ cảnh:

```java
public record SeatContext(Seat seat, CabinClass cabinClass, SeatInstance instance) {}

public static SeatResponse toResponse(final SeatContext context) { ... }
```

### Cạm bẫy `@Builder` + kiểu bọc (đã gây 2 bug thật trong repo)

```java
// ❌ SAI — NPE khi request thiếu field
public static Seat toEntity(final SeatRequest request) {
    return Seat.builder()
            .totalPrice(request.getBasePrice() + request.getSurcharge())   // NPE nếu surcharge null
            .build();
}
```

```java
// ✅ ĐÚNG — mọi phép tính trên Double/Integer đến từ request đều phải null-safe
public static Seat toEntity(final SeatRequest request) {
    final double surcharge = request.getSurcharge() != null ? request.getSurcharge() : 0.0;
    return Seat.builder()
            .totalPrice(request.getBasePrice() + surcharge)
            .build();
}
```

**Vì sao đây không phải lo xa:** field khai `private Integer displayOrder = 0;` trong entity
có vẻ đã có default, nhưng default kiểu đó **chỉ áp dụng khi gọi `new Seat()`**. Khi dựng
bằng Lombok `@Builder` (đúng cách mapper đang làm), field không set sẽ là `null`, không phải `0`.
Chi tiết 2 bug thật: [09-bug-co-that-trong-repo.md](09-bug-co-that-trong-repo.md).

## 5.6 — Logging

```java
// ✅ ĐÚNG
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatServiceImpl implements SeatService {

    @Override
    @Transactional
    public SeatResponse createSeat(final SeatRequest request) {
        ...
        final Seat saved = this.seatRepository.save(seat);
        log.info("Created seat id={} cabinClassId={} seatNumber={}",
                saved.getId(), saved.getCabinClassId(), saved.getSeatNumber());
        return SeatMapper.toResponse(saved, null);
    }
}
```

- `@Slf4j` cho mọi `*ServiceImpl` có method ghi; log `info` sau khi **create/update/delete thành công**.
- Dùng placeholder `{}`, **không** nối chuỗi (`"id=" + id`) — tránh tốn chi phí khi log level tắt.
- **Không log dữ liệu nhạy cảm**: password, JWT, token, số thẻ.

## 5.7 — Clean code

- Method tối đa ~20 dòng, tối đa 3 parameter (nhiều hơn thì gói vào Request object).
- Một method làm một việc; tách service nếu class vượt ~200 dòng.
- Comment chỉ giải thích **WHY** (ràng buộc ẩn, workaround), không giải thích WHAT.
- `final` cho parameter và local variable không đổi giá trị.
- Luôn `this.` khi truy cập field/method của instance.
- Ưu tiên `for` loop ở hot path (mapping list lớn); Stream OK cho logic đơn giản.

## Checklist service layer

- [ ] Có interface riêng, controller inject interface
- [ ] `@Transactional(readOnly = true)` ở class, `@Transactional` ở method ghi
- [ ] Method ghi đụng ≥2 bảng chắc chắn có `@Transactional`
- [ ] Ném `ResourceNotFoundException`/`BusinessException`, không `throw new Exception`
- [ ] Luồng xoá cha đã chặn hoặc xoá con tường minh
- [ ] Mapper là static thuần, không inject repository, có null-guard
- [ ] Mọi phép tính trên `Double`/`Integer` từ request đều null-safe
- [ ] `@Slf4j` + `log.info` cho create/update/delete
