# 03 — Entity & Persistence

> Hiện thực: `CLAUDE.md` mục "NO JPA Relationship Annotations" và "Entity Design Rules".

## 3.1 — Mọi entity phải `extends BaseEntity`

### ❌ SAI — `seat-service/model/CabinClass.java` hiện tại trong repo

```java
package com.nitditto.model;

import com.nitditto.enums.CabinClassType;
import jakarta.persistence.*;

public class CabinClass {                        // (1) thiếu @Entity, @Table

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)   // (2) tự khai id + strategy bị cấm
    private Long id;                                  // (3) Long, phải là UUID

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CabinClassType name;

    @Column(nullable = false)
    private Long aircraftId;                     // (4) FK phải là UUID + binary(16)

    @Column(nullable = false)
    private Integer displayOrder = 0;
    // (5) không có @Getter/@Setter/@Builder — Lombok đâu?
    // (6) aircraftId là cột sẽ lọc thường xuyên nhưng không có @Index
}
```

Sáu lỗi trong 30 dòng. Đáng chú ý nhất: thiếu `@Entity` nghĩa là class này **không phải
entity** — Hibernate bỏ qua hoàn toàn, và repository trỏ vào nó sẽ chết lúc khởi động
chứ không phải lúc compile.

### ✅ ĐÚNG

```java
package com.nitditto.model;

import com.nitditto.entity.BaseEntity;
import com.nitditto.enums.CabinClassType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "cabin_classes",
        indexes = @Index(name = "idx_cabin_class_aircraft", columnList = "aircraft_id")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabinClass extends BaseEntity {     // id/createdAt/updatedAt kế thừa từ đây

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, length = 30)
    private CabinClassType name;

    @Column(name = "code", nullable = false, length = 10)
    private String code;

    @Column(name = "description")
    private String description;

    @Column(name = "aircraft_id", columnDefinition = "binary(16)", nullable = false)
    private UUID aircraftId;                     // trỏ sang airline-core-service

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "is_bookable", nullable = false)
    private Boolean isBookable = true;
}
```

Điểm khác biệt bắt buộc:

- `extends BaseEntity` — đây là nơi **duy nhất** khai `id`/`createdAt`/`updatedAt`.
- Tên bảng **số nhiều, snake_case**; tên cột khai `name` **tường minh**, không dựa vào naming strategy mặc định.
- FK là `UUID` kèm `columnDefinition = "binary(16)"` để khớp kiểu với PK.
- `@Index` cho cột sẽ dùng để lọc.
- Import tường minh, không `jakarta.persistence.*`.

## 3.2 — UUIDv7, không phải v4

`BaseEntity` trong `common-lib` đã lo sẵn:

```java
@Id
@UuidGenerator(algorithm = UuidVersion7Strategy.class)
@JdbcTypeCode(SqlTypes.BINARY)
@Column(name = "id", columnDefinition = "binary(16)", updatable = false, nullable = false)
private UUID id;
```

```java
// ❌ SAI — trông rất hợp lý nhưng sinh UUID v4 NGẪU NHIÊN
@Id
@GeneratedValue(strategy = GenerationType.UUID)
private UUID id;
```

**Vì sao nguy hiểm:** UUIDv4 ngẫu nhiên khiến mỗi lần insert rơi vào một vị trí bất kỳ
trong B-tree của index, gây phân mảnh và page split liên tục. UUIDv7 có tiền tố timestamp
nên tăng dần, insert luôn ở cuối index. Lỗi này **không báo gì cả** — chỉ lộ khi bảng đã
lớn, và lúc đó sửa thì phải drop database.

**Lợi ích kèm theo:** vì ID sinh ở tầng ứng dụng (biết trước khi insert), Hibernate mới gom
được batch insert — đó là lý do `jdbc.batch_size` ở [trang 02](02-new-service-checklist.md)
mới thực sự có tác dụng.

## 3.3 — Không relationship, kể cả trong cùng service

```java
// ❌ SAI — kể cả khi Seat và CabinClass nằm cùng service, cùng database
@Entity
public class Seat extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cabin_class_id")
    private CabinClass cabinClass;               // CẤM

    @OneToMany(mappedBy = "seat", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SeatInstance> instances;        // CẤM
}
```

```java
// ✅ ĐÚNG — con giữ ID của cha; cha KHÔNG giữ tham chiếu tới con
@Entity
@Table(
        name = "seats",
        indexes = @Index(name = "idx_seat_cabin_class", columnList = "cabin_class_id")
)
public class Seat extends BaseEntity {

    @Column(name = "cabin_class_id", columnDefinition = "binary(16)", nullable = false)
    private UUID cabinClassId;
}
```

**Hệ quả phải tự xử lý:** không còn `cascade`/`orphanRemoval`, nên xoá cha phải tự chặn
hoặc tự xoá con — xem [05-service-layer.md](05-service-layer.md) mục 5.4.

## 3.4 — `@Embedded` được phép, đừng gỡ nhầm

`@Embedded` **không phải** relationship: nó nhúng field vào chính bảng đó, không sinh join,
không sinh query phụ, không gây N+1.

```java
// ✅ ĐÚNG — cùng bảng, không join
@Embedded
private SeatBenefits seatBenefits = new SeatBenefits();
```

Các value object trong `common-lib/embeddable/` (`Address`, `GeoCode`, `SeatBenefits`,
`BoardingBenefits`, `InFlightBenefits`, `FlexibilityBenefits`, `PremiumServiceBenefits`)
đều hợp lệ — **giữ nguyên khi refactor**.

## 3.5 — Thay `@ElementCollection` bằng `AttributeConverter`

`@ElementCollection` bị cấm vì sinh bảng phụ và lazy-load y hệt `@OneToMany`.

```java
// ❌ SAI
@ElementCollection
private List<DayOfWeek> operatingDays;
```

```java
// ✅ ĐÚNG — 1 cột varchar, không bảng phụ, không query phụ
@Convert(converter = DayOfWeekSetConverter.class)
@Column(name = "operating_days", length = 32)
private Set<DayOfWeek> operatingDays = new HashSet<>();
```

Converter mẫu có sẵn trong repo:
[`DayOfWeekSetConverter.java`](../../services/flight-ops-service/src/main/java/com/nitditto/converter/DayOfWeekSetConverter.java).
Đặt trong `converter/` của service sở hữu entity, hoặc `common-lib/converter/` nếu từ 2 service trở lên dùng.

## 3.6 — Index: quy tắc thực hành

`ddl-auto: update` **chỉ** tự tạo index cho khoá chính và unique constraint. Cột dùng để
lọc thì không có gì cả, nghĩa là đang full table scan.

**Quy tắc:** thêm repository method lọc theo cột nào thì kiểm tra cột đó đã có index chưa.

```java
@Table(
    name = "seat_instances",
    indexes = {
        // Index tổ hợp: cột lọc bằng "=" đặt TRƯỚC cột lọc theo khoảng/sắp xếp
        @Index(name = "idx_seat_inst_flight_status", columnList = "flight_instance_id, status"),
        @Index(name = "idx_seat_inst_seat", columnList = "seat_id")
    }
)
```

> Index tổ hợp `(a, b)` phục vụ được cả truy vấn chỉ lọc theo `a`, nhưng **không** phục vụ
> truy vấn chỉ lọc theo `b`. Đặt thứ tự cột đúng theo cách repository thực sự query.

## 3.7 — Không tự override `equals()`/`hashCode()`

Và **không bao giờ** dùng `@Data` hay `@EqualsAndHashCode` của Lombok trên entity — chúng
sinh `equals` trên toàn bộ field, gây lỗi rất khó lần. Chỉ dùng đúng bộ 5 annotation:
`@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`.

## Checklist entity

- [ ] `extends BaseEntity`, không tự khai `id`/`createdAt`/`updatedAt`
- [ ] Có `@Entity` **và** `@Table(name = "<số nhiều snake_case>")`
- [ ] Không có annotation nào trong bảng cấm ([01-golden-rules.md](01-golden-rules.md))
- [ ] Mọi FK là `UUID` + `columnDefinition = "binary(16)"`, đặt tên `<entity>Id`
- [ ] Mọi cột khai `name` tường minh
- [ ] Cột dùng để lọc đã có `@Index`
- [ ] Đủ 5 annotation Lombok, **không** `@Data`/`@EqualsAndHashCode`
- [ ] Import tường minh, không wildcard `.*`
