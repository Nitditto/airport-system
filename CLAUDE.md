# CLAUDE.md

File này chứa **rule và convention** cho Claude Code khi làm việc với repository này. Chỉ chứa quy tắc mang tính lâu dài — không ghi trạng thái codebase, không ghi "chỗ nào đang sai / cần sửa". Việc còn tồn đọng nằm ở [`docs/REFACTOR-BACKLOG.md`](docs/REFACTOR-BACKLOG.md).

---

## 1. Project Overview

**Airline Microservices** (`com.nitditto:airline-microservice`, repo `Nitditto/airport-system`) — hệ thống quản lý hàng không dạng multi-module Maven, tách theo domain thành các microservice độc lập (airline core, flight operations, location, pricing, user/auth).

## 2. Tech Stack

- Java 17 + Spring Boot 4.0.7 + Spring Cloud 2025.1.0
- MySQL + Spring Data JPA (Hibernate `ddl-auto: update` — chỉ dùng ở môi trường dev)
- Lombok
- Maven multi-module reactor build
- Spring Security + JWT tự viết (`JwtProvider`, `JwtConstant`) — chỉ có ở `user-service`

### Ngoài phạm vi kiến trúc hiện tại

Không giả định các thành phần sau tồn tại khi viết code: service discovery (Eureka), config server, API gateway, Feign client, message broker (Kafka/RabbitMQ), Redis cache, Flyway/Liquibase, PostgreSQL, MapStruct, OpenAPI/Swagger, CI/CD, Docker. Khi cần một trong số này, hỏi trước — đừng tự thêm.

## 3. Repository & Module Structure

Reactor 3 cấp: `root pom` → (`common-lib`, `cloud`, `services`) → `services/pom.xml` → 5 service module.

```
microservices/
├── pom.xml                          # Parent POM — quản lý version tập trung
│                                     # (java.version, spring.boot.version, spring.cloud.version)
├── common-lib/                      # Shared jar — mọi service phụ thuộc vào đây
│   └── src/main/java/com/nitditto/
│       ├── entity/                  # BaseEntity (id UUIDv7 + createdAt/updatedAt)
│       ├── embeddable/              # @Embeddable value objects (Address, GeoCode, SeatBenefits...)
│       ├── enums/                   # AircraftStatus, AirlineStatus, CabinClassType, FlightStatus, UserRole
│       ├── exception/               # ResourceNotFoundException, BusinessException (dùng chung)
│       └── payload/
│           ├── dto/                 # UserDTO
│           ├── request/             # *Request DTO cho MỌI service (tập trung tại đây)
│           └── response/            # *Response DTO + ApiResponse<T>, AuthResponse
├── cloud/                           # Aggregator cho service-registry/config-server/api-gateway
└── services/
    ├── pom.xml                      # Aggregator của 5 service, là parent thật của từng service
    ├── user-service/         port 5001   # Auth, JWT, Spring Security
    ├── airline-core-service/ port 5002   # Aircraft, Airline
    ├── flight-ops-service/   port 5003   # Flight, FlightInstance, FlightSchedule
    ├── location-service/     port 5004   # Airport, City
    └── pricing-service/      port 5005   # Fare, FareRules, BaggagePolicy
```

Mỗi service có package layout giống nhau dưới `com.nitditto`:

```
controller/    # @RestController
service/       # interface — hợp đồng nghiệp vụ
service/impl/  # *ServiceImpl — @Service @RequiredArgsConstructor
repository/    # Spring Data JPA interface
repository/spec/ # *Specs — Specification factory (khi filter động ≥ 3 điều kiện)
model/         # @Entity
mapper/        # static method mapper Entity <-> DTO (viết tay, không MapStruct)
converter/     # AttributeConverter (thay cho @ElementCollection)
exception/     # GlobalExceptionHandler của service (@RestControllerAdvice)
config/        # SecurityConfig, JwtProvider, JwtConstant, JwtAuthenticationFilter (hiện chỉ user-service)
```

## 4. Build Commands

**Không có Maven Wrapper ở thư mục gốc** — dùng `mvn` cài trên máy. Mỗi service con còn `mvnw`/`mvnw.cmd`/`.mvn/` lẻ (rác từ lúc khởi tạo độc lập bằng Spring Initializr) — **đừng dùng**, luôn chạy `mvn` từ thư mục gốc để giữ đúng version do parent POM quản lý.

```bash
# Build toàn bộ reactor (common-lib -> cloud -> services)
mvn clean install

# Build/run một service cụ thể (chạy từ thư mục gốc, dùng -pl với path module)
mvn -pl services/pricing-service -am clean install
mvn -pl services/pricing-service spring-boot:run

# Run tests toàn repo / một service / một class / một method
mvn test
mvn -pl services/pricing-service test
mvn -pl services/pricing-service test -Dtest=ClassName
mvn -pl services/pricing-service test -Dtest=ClassName#methodName

# Package không chạy test
mvn clean package -DskipTests
```

**Quản lý version dependency:** chỉ khai `<version>` ở root `pom.xml` (`dependencyManagement`), hoặc dùng version do `spring-boot-dependencies`/`spring-cloud-dependencies` BOM quản lý. Service con **KHÔNG** tự ghim `<version>` riêng — tránh lệch version giữa các service.

## 5. Architecture — Data Flow (trong một service)

```
Request → Controller → Service (interface) → ServiceImpl → Repository → MySQL
                                                    ↓
                                              Mapper (Entity <-> DTO)
```

- Controller chỉ xử lý HTTP (nhận request, gọi service, bọc `ApiResponse`, trả `ResponseEntity`).
- Business logic chỉ nằm ở `*ServiceImpl`.
- Mapper là hàm static thuần — không inject gì.
- Repository chỉ chứa truy vấn.

---

## 6. NO JPA Relationship Annotations (BẮT BUỘC — áp dụng CẢ trong cùng một service)

Rule nền tảng của project, chi phối cách viết entity, repository, service và mapper. **Mọi khoá ngoại đều là cột `UUID` thô**, không exception — kể cả khi hai entity cùng một service, cùng một database.

### Danh sách annotation bị CẤM

`@ManyToOne` · `@OneToMany` · `@OneToOne` · `@ManyToMany` · `@JoinColumn` · `@JoinTable` · `@ElementCollection` · `@MapsId` · mọi `CascadeType` và `orphanRemoval`

```java
// ❌ SAI — không được xuất hiện trong bất kỳ entity nào
@Entity
public class Fare extends BaseEntity {

    @OneToOne(mappedBy = "fare", cascade = CascadeType.ALL, orphanRemoval = true)
    private BaggagePolicy baggagePolicy;          // KHÔNG ĐƯỢC

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cabin_class_id")
    private CabinClass cabinClass;                // KHÔNG ĐƯỢC
}
```

```java
// ✅ ĐÚNG — chỉ giữ UUID thô, đặt tên <entity>Id
@Entity
@Table(name = "fares")
public class Fare extends BaseEntity {

    @Column(name = "flight_id", columnDefinition = "binary(16)", nullable = false)
    private UUID flightId;                        // trỏ sang flight-ops-service

    @Column(name = "cabin_class_id", columnDefinition = "binary(16)", nullable = false)
    private UUID cabinClassId;                    // trỏ sang airline-core-service
}

// Chiều ngược lại cũng vậy — con giữ ID của cha, cha KHÔNG giữ tham chiếu tới con
@Entity
@Table(name = "baggage_policies")
public class BaggagePolicy extends BaseEntity {

    @Column(name = "fare_id", columnDefinition = "binary(16)", nullable = false, unique = true)
    private UUID fareId;
}
```

**Lý do:** loại bỏ hoàn toàn N+1 do lazy/eager loading; kiểm soát tường minh thời điểm load dữ liệu liên quan; entity serialize/deserialize đơn giản, không cần `@JsonIgnore` chống vòng lặp; và quan trọng nhất — ranh giới service không vỡ khi một entity sau này bị tách sang service khác (đổi chỗ chỉ là chuyện của tầng service, entity không đổi).

### Ngoại lệ được phép: `@Embedded` / `@Embeddable`

`@Embedded` **KHÔNG phải** relationship — nó nhúng field vào chính bảng đó, không sinh join, không sinh query phụ, không gây N+1. Các benefit block của `Fare` (`SeatBenefits`, `BoardingBenefits`, `InFlightBenefits`, `FlexibilityBenefits`, `PremiumServiceBenefits`) và `Address`, `GeoCode` trong `common-lib` là hợp lệ — **giữ nguyên khi refactor**.

```java
// ✅ ĐÚNG — cùng bảng, không join
@Embedded
private SeatBenefits seatBenefits = new SeatBenefits();
```

### Thay thế `@ElementCollection`

`@ElementCollection` bị cấm vì sinh bảng phụ và lazy-load y hệt `@OneToMany`. Với collection kiểu giá trị (vd. `Set<DayOfWeek> operatingDays`), dùng `AttributeConverter` lưu vào một cột duy nhất:

```java
// ✅ ĐÚNG — 1 cột varchar, không bảng phụ, không query phụ
@Convert(converter = DayOfWeekSetConverter.class)
@Column(name = "operating_days", length = 32)
private Set<DayOfWeek> operatingDays = new HashSet<>();

@Converter
public class DayOfWeekSetConverter implements AttributeConverter<Set<DayOfWeek>, String> {

    @Override
    public String convertToDatabaseColumn(final Set<DayOfWeek> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "";
        }
        return attribute.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public Set<DayOfWeek> convertToEntityAttribute(final String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new HashSet<>();
        }
        return Arrays.stream(dbData.split(",")).map(DayOfWeek::valueOf)
                .collect(Collectors.toCollection(HashSet::new));
    }
}
```

Converter đặt trong package `converter/` của service sở hữu entity (hoặc `common-lib/converter/` nếu ≥ 2 service dùng).

### Hệ quả 1 — Không còn `cascade`/`orphanRemoval`, phải tự xử lý ở tầng service

Xoá cha không tự xoá con nữa. Phải xoá thủ công trong cùng một transaction, con trước cha sau:

```java
// ✅ ĐÚNG
@Override
@Transactional
public void deleteFareById(final UUID id) {
    if (!this.fareRepository.existsById(id)) {
        throw new ResourceNotFoundException("Fare", "id", id);
    }
    this.baggagePolicyRepository.deleteByFareId(id);
    this.fareRuleRepository.deleteByFareId(id);
    this.fareRepository.deleteById(id);
}
```

**Bắt buộc:** mọi method xoá một entity có bản ghi con phải có `@Transactional` và xoá con tường minh. Với các cặp cha-con từng dựa vào FK constraint thật của MySQL, thêm kiểm tra tường minh (ném `BusinessException`) ở tầng service trước khi xoá cha hoặc thêm con.

### Hệ quả 2 — Mapper nhận dữ liệu liên quan qua tham số

Mapper không truy ngược quan hệ được nữa (`fare.getFareRules()` không tồn tại), nên service load sẵn và truyền vào:

```java
// ❌ SAI — mapper tự đi query, hoặc inject repository vào mapper
public static FareResponse toResponse(final Fare fare) {
    return FareResponse.builder()
            .fareRules(FareRuleMapper.toResponse(fare.getFareRules()))   // KHÔNG CÒN
            .build();
}

// ✅ ĐÚNG — service load rồi truyền vào; mapper vẫn là hàm static thuần
public static FareResponse toResponse(final Fare fare,
                                      final FareRules fareRules,
                                      final BaggagePolicy baggagePolicy) {
    return FareResponse.builder()
            .id(fare.getId())
            .fareRules(FareRuleMapper.toResponse(fareRules))
            .baggagePolicy(BaggagePolicyMapper.toResponse(baggagePolicy))
            .build();
}
```

**Không inject repository/service vào mapper.** Nếu mapper cần query, logic đang đặt sai chỗ — đẩy lên service.

Nếu số tham số vượt 3, gói lại thành một record ngữ cảnh:

```java
public record FareContext(Fare fare, FareRules fareRules, BaggagePolicy baggagePolicy) {}

public static FareResponse toResponse(final FareContext context) { ... }
```

### Hệ quả 3 — Load dữ liệu liên quan bằng batch query ở tầng service

Nguyên tắc: **một query cho cha, một query `IN` cho mỗi loại con, rồi ghép trong bộ nhớ bằng `Map`.** Đầy đủ ở [§7 N+1 Query Prevention](#7-n1-query-prevention-quan-trọng).

### Cross-Service Communication

Chưa có Feign/service discovery, nên giao tiếp giữa các service là **tham chiếu bằng UUID + endpoint batch-fetch**:

```java
// Consumer tự batch-fetch khi cần data đầy đủ, ví dụ pricing-service expose:
POST /api/fares/batch-by-ids   // nhận List<UUID>, trả về Map<UUID, FareResponse>
```

**Quy tắc bắt buộc khi thêm liên kết mới giữa 2 service:**

- Field tham chiếu luôn là `UUID`, đặt tên `<entity>Id`. Không bao giờ map sang entity của service khác (khác database, vỡ khi chạy).
- Cần load nhiều bản ghi liên quan → cung cấp endpoint `POST /batch-by-ids` nhận `List<UUID>` trả `Map<UUID, XxxResponse>`, không gọi API service khác trong loop (xem [§7 Loại 2](#loại-2--gọi-http-sang-service-khác-trong-vòng-lặp-nguy-hiểm-nhất)).
- Khi module `cloud/` (Eureka + Gateway) dựng xong, thay lời gọi HTTP thủ công bằng OpenFeign client đặt trong `common-lib` hoặc package `client/` của service gọi — không hardcode `localhost:port`.

---

## 7. N+1 Query Prevention (QUAN TRỌNG)

Vì đã cấm hoàn toàn relationship annotation, **N+1 do lazy/eager loading không còn tồn tại**. Đổi lại, việc ghép dữ liệu chuyển sang tay bạn ở tầng service — và đó là chỗ N+1 quay lại nếu viết ẩu. Có **2 loại** phải phòng.

### Loại 1 — Gọi repository trong vòng lặp

```java
// ❌ SAI — 1 + N query
final List<FareResponse> responses = new ArrayList<>();
for (final Fare fare : fares) {
    final BaggagePolicy policy = baggagePolicyRepository.findByFareId(fare.getId());   // N query!
    final FareRules rules = fareRuleRepository.findByFareId(fare.getId());             // N query nữa!
    responses.add(FareMapper.toResponse(fare, rules, policy));
}

// ✅ ĐÚNG — 1 + 2 query, ghép bằng Map trong bộ nhớ
final List<UUID> fareIds = fares.stream().map(Fare::getId).toList();

final Map<UUID, BaggagePolicy> policyByFareId = this.baggagePolicyRepository.findByFareIdIn(fareIds)
        .stream().collect(Collectors.toMap(BaggagePolicy::getFareId, Function.identity()));
final Map<UUID, FareRules> rulesByFareId = this.fareRuleRepository.findByFareIdIn(fareIds)
        .stream().collect(Collectors.toMap(FareRules::getFareId, Function.identity()));

final List<FareResponse> responses = new ArrayList<>(fares.size());
for (final Fare fare : fares) {
    responses.add(FareMapper.toResponse(
            fare, rulesByFareId.get(fare.getId()), policyByFareId.get(fare.getId())));
}
```

**Công thức chuẩn — 4 bước:**

1. Query danh sách cha (có phân trang).
2. Trích `List<UUID>` các ID cần tra cứu, nhớ `.distinct()`.
3. Một query `...In(ids)` cho **mỗi loại** dữ liệu con → gom về `Map<UUID, T>`.
4. Lặp qua cha, tra `Map` bằng `get()`, không đụng repository nữa.

Số query luôn là **hằng số** theo số loại quan hệ, không phụ thuộc số dòng.

Với dữ liệu tổng hợp (đếm/sum/min), dùng aggregate query một lần thay vì tính từng dòng:

```java
// ✅ ĐÚNG — 1 query gom nhóm, ưu tiên DTO projection thay vì Object[]
public record FareCountByFlight(UUID flightId, Long total) {}

@Query("""
        select new com.nitditto.payload.projection.FareCountByFlight(f.flightId, count(f))
        from Fare f where f.flightId in :flightIds group by f.flightId
        """)
List<FareCountByFlight> countByFlightIds(@Param("flightIds") List<UUID> flightIds);
```

### Loại 2 — Gọi HTTP sang service khác trong vòng lặp (nguy hiểm nhất)

N+1 qua network tệ hơn N+1 qua DB nhiều lần (mỗi lần là round-trip TCP + serialize JSON, nhân theo độ trễ mạng):

```java
// ❌ SAI — N HTTP call
for (final Fare fare : fares) {
    final FlightResponse flight = flightClient.getById(fare.getFlightId());
    responses.add(FareMapper.toResponse(fare, flight));
}

// ✅ ĐÚNG — 1 HTTP call batch
final List<UUID> flightIds = fares.stream().map(Fare::getFlightId).distinct().toList();
final Map<UUID, FlightResponse> flightById = this.flightClient.getByIds(flightIds);

for (final Fare fare : fares) {
    responses.add(FareMapper.toResponse(fare, flightById.get(fare.getFlightId())));
}
```

**Rule:** mọi service expose dữ liệu cho service khác **bắt buộc** có endpoint batch nhận `List<UUID>` trả `Map<UUID, XxxResponse>`. Luôn `.distinct()` danh sách ID trước khi gọi.

### Cấu hình bắt buộc trong `application.yaml`

```yaml
spring:
  jpa:
    open-in-view: false     # giải phóng DB connection ngay khi service trả về
    show-sql: true          # DEV ONLY — để đếm query thực tế, tắt ở production
    properties:
      hibernate:
        format_sql: true
logging:
  level:
    org.hibernate.SQL: DEBUG          # DEV ONLY
```

Không thể phòng N+1 nếu không nhìn thấy nó — bật SQL log, gọi thử endpoint trả list và **đếm số dòng SQL**. Số query tăng theo số bản ghi = đang có N+1.

### Checklist N+1 (tự soát trước khi kết thúc task)

- [ ] Entity mới có lọt annotation quan hệ nào không?
- [ ] Có lời gọi `repository.*` hay HTTP client nào nằm trong `for` / `stream().map()` / `forEach` không?
- [ ] Mỗi loại dữ liệu con có đúng **một** query `...In(ids)` không, và đã `.distinct()` danh sách ID chưa?
- [ ] Endpoint trả list đã phân trang chưa?
- [ ] Đã bật SQL log và đếm số query thực tế cho endpoint trả list chưa?

---

## 8. Repository & Query Rules

### Phân trang cho MỌI endpoint trả về list

```java
// ❌ SAI — không giới hạn số dòng, và trả entity thô ra ngoài service
List<Fare> getFares() {
    return fareRepository.findAll();
}

// ✅ ĐÚNG
Page<FareResponse> getFares(final Pageable pageable) {
    return this.fareRepository.findAll(pageable).map(FareMapper::toResponse);
}
```

- Không có ngoại lệ cho endpoint "lấy tất cả" — nếu client cần toàn bộ, bắt nó phân trang và lặp.
- Endpoint `batch-by-ids` là ngoại lệ hợp lệ (client tự giới hạn bằng danh sách ID), nhưng chặn kích thước đầu vào (`@Size(max = 200)` trên `List<UUID>`).

### Repository chỉ chứa truy vấn

Spring Data JPA thuần: method name query, `@Query` khi cần, `Specification` cho filter động. **Không** đặt business logic, không mapping Entity↔DTO, không gọi service khác từ repository.

### `Specification` cho filter động (khi số filter ≥ 3)

Thay pattern `(:param is null or f.col = :param)` — pattern đó chặn DB dùng index và phải sửa JPQL mỗi lần thêm filter.

```java
public interface FlightRepository
        extends JpaRepository<Flight, UUID>, JpaSpecificationExecutor<Flight> { }

public final class FlightSpecs {

    private FlightSpecs() { }

    public static Specification<Flight> airlineId(final UUID airlineId) {
        return (root, query, cb) ->
                airlineId == null ? null : cb.equal(root.get("airlineId"), airlineId);
    }

    public static Specification<Flight> departureAirportId(final UUID airportId) {
        return (root, query, cb) ->
                airportId == null ? null : cb.equal(root.get("departureAirportId"), airportId);
    }
}
```

```java
// Ghép ở tầng service — trả null nghĩa là "bỏ qua điều kiện này"
final Specification<Flight> spec = Specification
        .where(FlightSpecs.airlineId(airlineId))
        .and(FlightSpecs.departureAirportId(departureAirportId));

return this.flightRepository.findAll(spec, pageable).map(FlightMapper::toResponse);
```

Đặt các lớp `*Specs` trong package `repository/spec/`, là `final class` với constructor private (chỉ chứa static factory method, không phải bean).

### Kiểm tra tồn tại: `existsBy*`, không `findBy*` rồi check null

```java
// ❌ SAI — kéo cả entity về chỉ để biết có tồn tại hay không
if (fareRepository.findByName(name).isPresent()) { ... }
if (fareRepository.countByName(name) > 0) { ... }

// ✅ ĐÚNG — SQL chỉ trả về boolean
if (this.fareRepository.existsByName(name)) { ... }
```

Luồng update: dùng biến thể `*AndIdNot` để loại chính bản ghi đang sửa khỏi phép kiểm tra trùng (`existsByCityCodeAndIdNot`). Áp dụng cho mọi unique check.

### Ghi hàng loạt: `saveAll()`, không `save()` trong vòng lặp

```java
// ❌ SAI — N lần round-trip tới DB
for (final Fare fare : fares) {
    fareRepository.save(fare);
}

// ✅ ĐÚNG — 1 lần, Hibernate gom thành batch insert
this.fareRepository.saveAll(fares);
```

Để `saveAll()` thật sự batch dưới JDBC, bật trong `application.yaml`:

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc.batch_size: 50
        order_inserts: true
        order_updates: true
```

Batch insert chỉ hoạt động khi ID **không** do DB sinh. Project dùng UUIDv7 sinh ở tầng ứng dụng (xem [§10](#10-entity-design-rules)) nên Hibernate biết ID trước khi insert → gom batch được.

`saveAll()` một danh sách rất lớn vẫn giữ toàn bộ entity trong persistence context. Với > 5.000 bản ghi, chia lô và `flush()`/`clear()` sau mỗi lô để tránh phình heap.

### Chỉ đọc vài cột thì dùng projection

```java
// ❌ SAI — load cả Fare (kèm 5 khối @Embedded) chỉ để lấy tên và giá
final List<Fare> fares = fareRepository.findByFlightId(flightId);

// ✅ ĐÚNG — interface projection, SQL chỉ select đúng 2 cột
public interface FareSummary {
    String getName();
    Double getCurrentPrice();
}
List<FareSummary> findByFlightId(UUID flightId);
```

### Index cho cột được query thường xuyên

Với `ddl-auto: update`, Hibernate chỉ tự tạo index cho khoá chính và unique constraint — cột dùng để lọc thì không có, nghĩa là full table scan. Khai `@Index` tường minh:

```java
@Entity
@Table(
    name = "fares",
    indexes = {
        @Index(name = "idx_fare_flight_cabin", columnList = "flight_id, cabin_class_id"),
        @Index(name = "idx_fare_flight", columnList = "flight_id")
    }
)
public class Fare extends BaseEntity { ... }
```

**Quy tắc:** thêm repository method lọc theo cột nào thì kiểm tra cột đó đã có index chưa. Index tổ hợp đặt cột lọc bằng `=` lên trước cột lọc theo khoảng.

### `@Transactional` — bắt buộc và nhất quán

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)        // mặc định cho cả class
public class FareServiceImpl implements FareService {

    @Override
    @Transactional                      // ghi đè cho method ghi
    public FareResponse createFare(final FareRequest request) { ... }
}
```

- `readOnly = true` cho method đọc: Hibernate bỏ qua dirty checking → nhanh hơn, tránh vô tình ghi.
- **Bắt buộc `@Transactional`** cho mọi method ghi đụng tới ≥ 2 bảng — đặc biệt luồng xoá cha-con thủ công, vì đã không còn `cascade` (xem [§6 Hệ quả 1](#hệ-quả-1--không-còn-cascadeorphanremoval-phải-tự-xử-lý-ở-tầng-service)).
- `@Transactional` chỉ có tác dụng khi được gọi từ **bên ngoài** bean. Gọi `this.someTransactionalMethod()` trong cùng class bỏ qua proxy → **không** mở transaction. Tách sang bean khác nếu cần.

---

## 9. common-lib Usage Rules (BẮT BUỘC)

- **DTO dùng chung nhiều service** (Request/Response đại diện cho một entity mà service khác cần đọc) → `common-lib/payload/request|response`.
- **Enum nghiệp vụ dùng chung** (status, role, type) → `common-lib/enums`.
- **`@Embeddable` value object dùng chung** (Address, GeoCode...) → `common-lib/embeddable`.
- **Exception dùng chung** (`ResourceNotFoundException`, `BusinessException`) → `common-lib/exception`.
- **KHÔNG** đặt Entity (`@Entity`) trong `common-lib` — entity luôn thuộc đúng 1 service, giữ database boundary rõ ràng. Ngoại lệ duy nhất: `BaseEntity` (`@MappedSuperclass`, không phải `@Entity`).
- **KHÔNG** đặt business logic hay Service/Repository trong `common-lib` — chỉ model dữ liệu thuần (DTO/enum/embeddable) + validation annotation.
- Thêm class mới vào `common-lib` nghĩa là ít nhất 2 service sẽ dùng nó. Nếu chỉ 1 service dùng, giữ trong service đó.

---

## 10. Entity Design Rules

### BaseEntity + UUIDv7 (BẮT BUỘC)

Toàn bộ khoá chính dùng **UUID version 7**, sinh ở tầng ứng dụng. Mọi entity `extends BaseEntity` (đặt trong `common-lib/entity/`):

```java
// common-lib/entity/BaseEntity.java
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @UuidGenerator(algorithm = UuidVersion7Strategy.class)
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", columnDefinition = "binary(16)", updatable = false, nullable = false)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
```

`UuidVersion7Strategy` là `org.hibernate.id.uuid.UuidVersion7Strategy` — có sẵn trong Hibernate 7 (Spring Boot 4.0.7 kéo về), không cần thư viện ngoài.

**Quy tắc bắt buộc:**

- **CẤM `@GeneratedValue(strategy = GenerationType.UUID)`** — sinh UUID **v4 ngẫu nhiên**, làm phân mảnh index và mất hết lợi ích của v7. Lỗi âm thầm, chỉ lộ khi bảng lớn. Chỉ dùng đúng cặp `@UuidGenerator(algorithm = UuidVersion7Strategy.class)` như trên.
- **`BaseEntity` là nơi DUY NHẤT khai báo `id`, `createdAt`, `updatedAt`.** Entity con không khai lại.
- **Lưu dạng `binary(16)`**, không `char(36)` — tiết kiệm 20 byte mỗi khoá và mỗi index. Đánh đổi: query tay trong MySQL Workbench phải bọc `BIN_TO_UUID(id)` / `UUID_TO_BIN('...')`.
- **Mọi FK (cross-service và cùng service) đều là `UUID`**, khai kèm `columnDefinition = "binary(16)"` để khớp kiểu với PK:

```java
@Column(name = "flight_id", columnDefinition = "binary(16)", nullable = false)
private UUID flightId;
```

- **`ddl-auto: update` KHÔNG đổi được kiểu cột đã tồn tại** — chỉ thêm bảng/cột mới. Đổi kiểu cột bắt buộc **drop và tạo lại database dev**.
- **Không tự override `equals()`/`hashCode()` theo `id`** trừ khi thực sự cần bỏ entity vào `Set`/`Map`. **Không bao giờ** dùng `@Data`/`@EqualsAndHashCode` của Lombok trên entity (sinh `equals` trên toàn bộ field, gây lỗi khó lần).

### Naming & Table Convention

```java
@Entity
@Table(
    name = "fares",
    indexes = @Index(name = "idx_fare_flight_cabin", columnList = "flight_id, cabin_class_id")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Fare extends BaseEntity {

    @Column(name = "flight_id", columnDefinition = "binary(16)", nullable = false)
    private UUID flightId;
    // ...
}
```

- Tên bảng: số nhiều, snake_case (`fares`, `flight_instances`, `baggage_policies`).
- Tên cột: snake_case, khai `name` tường minh — không dựa vào naming strategy mặc định.

---

## 11. Coding Standards

### SOLID Principles (Bắt buộc)

- **S — Single Responsibility** — Controller chỉ xử lý HTTP; Service chỉ chứa business logic của một domain; tách service nếu class vượt ~200 dòng.
- **O — Open/Closed** — mở rộng qua interface/Strategy, không sửa method hiện có để nhét thêm nhánh `if` cho case mới.
- **L — Liskov Substitution** — `*ServiceImpl` phải thay thế được interface mà không đổi hành vi; không override để throw `UnsupportedOperationException`.
- **I — Interface Segregation** — interface nhỏ, gọn theo domain (`FareService` riêng, không gộp với `BaggagePolicyService`).
- **D — Dependency Inversion** — **BẮT BUỘC** controller và service khác inject qua interface, không inject thẳng `*ServiceImpl`.

### Dependency Injection Pattern

```java
public interface FareService {
    FareResponse findById(UUID id);
    Map<UUID, FareResponse> findByIds(List<UUID> ids);
    Page<FareResponse> findAll(Pageable pageable);
    FareResponse create(FareRequest request);
    FareResponse update(UUID id, FareRequest request);
    void delete(UUID id);
}

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FareServiceImpl implements FareService {
    private final FareRepository fareRepository;
    private final BaggagePolicyRepository baggagePolicyRepository;
    private final FareRuleRepository fareRuleRepository;
    // ...
}

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/fares")
public class FareController {
    private final FareService fareService;  // inject interface, không phải FareServiceImpl
}
```

Constructor injection qua `@RequiredArgsConstructor` + field `private final`. Không dùng `@Autowired` trên field.

### Java Naming Conventions

- **Package**: lowercase, không underscore — `com.nitditto.pricing.service`.
- **Class/Interface**: PascalCase danh từ; interface không prefix `I`; implementation suffix `Impl` (`FareService` / `FareServiceImpl`).
- **Method**: camelCase, bắt đầu bằng động từ — `findById()`, `createFare()`, `isActive()`. Boolean: `is*`, `has*`, `can*`.
- **Variable**: camelCase có nghĩa (`flightId`, không viết tắt `fltId`); collection dùng số nhiều (`fares`, `flights`).
- **Constant**: SCREAMING_SNAKE_CASE.
- **DTO**: `*Request` (dùng chung cho cả create và update — không tách `CreateXRequest`/`UpdateXRequest`), `*Response` (trả về client).

### Clean Code Rules

- Method tối đa ~20 dòng, tối đa 3 parameter (nhiều hơn thì gói vào Request object).
- Một method làm một việc.
- Không comment giải thích WHAT, chỉ comment WHY (constraint ẩn, workaround).
- Không catch `Exception` chung chung ở tầng service để nuốt lỗi; ném exception cụ thể (xem [§12](#12-exception-handling)).
- Không return `null` cho method tìm theo id — dùng `Optional` ở repository, ném exception cụ thể ở service.

### `final` & `this`

- Method parameter và local variable không đổi giá trị: dùng `final`.
- Luôn dùng `this.` khi truy cập field/method của instance trong service/mapper để phân biệt với parameter cùng tên.

### Loop vs Stream

- Ưu tiên `for` loop ở hot path (mapping list lớn, batch xử lý). Stream OK cho logic đơn giản không phải hot path.

---

## 12. Exception Handling

Custom exception dùng chung trong `common-lib`, mỗi service có `GlobalExceptionHandler` riêng.

```java
// common-lib/exception/ResourceNotFoundException.java
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(final String resource, final String field, final Object value) {
        super("%s not found with %s: %s".formatted(resource, field, value));
    }
}

// common-lib/exception/BusinessException.java
public class BusinessException extends RuntimeException {
    public BusinessException(final String message) {
        super(message);
    }
}
```

```java
// mỗi service: exception/GlobalExceptionHandler.java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(final ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(final MethodArgumentNotValidException ex) {
        // gom field error -> message
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(final BusinessException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(final Exception ex) {
        log.error("Unexpected error", ex);   // log đầy đủ ở server
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal server error"));   // không lộ stack trace ra client
    }
}
```

- `ResourceNotFoundException`/`BusinessException` đặt trong `common-lib` (dùng chung). `GlobalExceptionHandler` đặt riêng từng service (`@RestControllerAdvice` scope theo application context).
- Service ném `ResourceNotFoundException`/`BusinessException` — **không** `throw new Exception(...)`. Controller **không** khai `throws Exception`.
- Catch-all `Exception.class` luôn log đầy đủ ở server và trả message chung chung — không bao giờ trả stack trace ra client.

---

## 13. API Response Wrapper

`ApiResponse<T>` là generic wrapper cho **mọi endpoint có trả dữ liệu**.

```java
// common-lib/payload/response/ApiResponse.java
@Getter
@Builder
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(final T data) {
        return ApiResponse.<T>builder().success(true).data(data).build();
    }

    public static <T> ApiResponse<T> error(final String message) {
        return ApiResponse.<T>builder().success(false).message(message).build();
    }
}
```

```java
@GetMapping("/{id}")
public ResponseEntity<ApiResponse<FareResponse>> findById(@PathVariable final UUID id) {
    return ResponseEntity.ok(ApiResponse.success(this.fareService.findById(id)));
}
```

- Áp dụng đồng bộ cho **tất cả** controller — không nửa vời (một số endpoint bọc, số khác trả thẳng DTO).
- `DELETE` giữ `204 No Content` không body — **không** bọc `ApiResponse` (204 theo spec không nên có body).

---

## 14. Validation

- Request DTO dùng `jakarta.validation.constraints` (`@NotBlank`, `@NotNull`, `@Positive`, `@Size`).
- **BẮT BUỘC** mọi `@RequestBody` ở controller phải có `@Valid` đi kèm — cả create lẫn update.

---

## 15. Logging

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class FareServiceImpl implements FareService {
    @Override
    public FareResponse create(final FareRequest request) {
        log.info("Creating fare for flightId={}", request.getFlightId());
        // ...
    }
}
```

- `@Slf4j` cho class Service. Log mức `info` cho hành động ghi (create/update/delete); `warn`/`error` khi bắt exception nghiệp vụ.
- **Không** log dữ liệu nhạy cảm (password, token JWT).

---

## 16. Database

- **Database-per-service.** Mỗi service một database riêng:

  | Service | Database |
  |---|---|
  | user-service | `airline_user` |
  | airline-core-service | `airline` |
  | flight-ops-service | `airline_flight` |
  | location-service | `airline_location` |
  | pricing-service | `airline_pricing` |

- `ddl-auto: update` chỉ dùng ở dev. Không dùng cho production — nếu tiến tới production, dùng Flyway/Liquibase để quản lý schema migration.
- **Không** commit credential/mật khẩu DB thật/JWT secret. Khi lên môi trường thật, chuyển sang biến môi trường hoặc config server.

---

## 17. Git Convention (BẮT BUỘC)

### Branch Model

Mô hình **develop-centric**: `develop` là base branch mặc định. Chưa có `main`/`master` — mọi PR nhắm vào `develop`.

```
develop                 ← base branch, luôn ở trạng thái build được
   ├── feature/xxx      ← nhánh làm việc, tách ra từ develop
   ├── fix/xxx
   └── docs/xxx
```

- **KHÔNG commit trực tiếp lên `develop`.** Mọi thay đổi đi qua nhánh riêng + Pull Request, kể cả sửa một dòng config.
- Luôn tách nhánh mới từ `develop` đã cập nhật, không tách từ nhánh feature khác (trừ khi thật sự phụ thuộc, và ghi rõ trong PR).
- Một nhánh = một mục đích.

### Branch Naming

```
<type>/<mô-tả-ngắn>
```

| Prefix | Dùng khi | Ví dụ |
|---|---|---|
| `feature/` | Thêm tính năng / module mới | `feature/pricing-service` |
| `fix/` | Sửa bug, sửa cấu hình sai | `fix/flight-ops-port` |
| `docs/` | Chỉ đụng tài liệu | `docs/build-base` |
| `refactor/` | Đổi cấu trúc, không đổi hành vi | `refactor/uuid-migration` |
| `chore/` | Build, dependency, config vặt | `chore/bump-spring-boot` |
| `test/` | Chỉ thêm/sửa test | `test/fare-service` |

- Toàn bộ **lowercase, kebab-case**. Không dấu tiếng Việt, không space, không camelCase.
- Ngắn gọn nhưng đủ nghĩa: `feature/pricing-service` tốt hơn `feature/new-stuff`.
- Có issue tracker thì thêm số vào cuối: `feature/pricing-service-42`.
- Dùng `feature/` cho branch. `feat` chỉ là *type của commit* — hai khái niệm khác nhau, đừng trộn.

### Commit Message — Conventional Commits

```
<type>(<scope>): <subject>

<body — tuỳ chọn, giải thích WHY>

<footer — tuỳ chọn: BREAKING CHANGE, Refs #issue>
```

**`type`:**

| Type | Ý nghĩa |
|---|---|
| `feat` | Thêm tính năng mới cho người dùng/API |
| `fix` | Sửa bug |
| `refactor` | Đổi code nhưng **không** đổi hành vi bên ngoài |
| `perf` | Tối ưu hiệu năng (thêm index, gỡ N+1) |
| `docs` | Chỉ tài liệu |
| `test` | Thêm/sửa test |
| `chore` | Build, dependency, cấu hình, việc vặt không đụng logic |
| `style` | Format, khoảng trắng, import — không đổi logic |
| `build` | Maven POM, version, reactor structure |

**`scope`** — bám theo module trong reactor, dùng tên rút gọn (bỏ đuôi `-service`):

| Scope | Module |
|---|---|
| `common` | `common-lib/` |
| `cloud` | `cloud/` |
| `user` | `services/user-service/` |
| `airline-core` | `services/airline-core-service/` |
| `location` | `services/location-service/` |
| `flight-ops` | `services/flight-ops-service/` |
| `pricing` | `services/pricing-service/` |
| `build` | `pom.xml` gốc, `services/pom.xml`, cấu hình reactor |

- Đụng **một** module → bắt buộc có scope: `feat(pricing): ...`
- Đụng **nhiều** module cùng lúc → **bỏ scope**: `refactor: migrate all entity ids to uuidv7`
- Không bịa scope ngoài bảng trên.

**`subject`:**

- **Tiếng Anh**, thể mệnh lệnh (imperative): `add`, `implement`, `fix`, `remove` — không phải `added`/`adding`/`adds`.
- Chữ đầu **thường**, **không** dấu chấm cuối câu.
- Tối đa **72 ký tự**.
- Mô tả *cái gì thay đổi*, không phải *đã làm bao nhiêu file*.

```
✅ ĐÚNG
feat(pricing): implement baggage policy management feature
feat(user): add user-service with authentication and jwt configuration
feat(common): add shared models and request/response payloads for pricing
chore(flight-ops): change port to 5003

❌ SAI
Feature/pricing service              # tên nhánh, không phải commit message
feat(pricing): Implemented Fares.    # hoa đầu + quá khứ + dấu chấm cuối
update code                          # thiếu type, không rõ nghĩa
fix bug                              # không biết bug gì, ở đâu
feat(pricing): add fare + fix port + update docs   # gộp 3 việc vào 1 commit
```

**Body** — bỏ qua được với commit hiển nhiên; **bắt buộc có** khi thay đổi cần giải thích lý do hoặc có đánh đổi:

```
perf(pricing): add composite index on fares(flight_id, cabin_class_id)

FareRepository lọc theo đúng cặp cột này ở 3 method, trước đó full table
scan vì ddl-auto: update chỉ tự tạo index cho PK và unique constraint.

Refs #12
```

- Body cách subject đúng **một dòng trống**. Wrap ở **72 ký tự**. Viết **WHY**, không viết WHAT.

**Footer — breaking change** — bắt buộc khai báo khi thay đổi phá vỡ client đang gọi API hoặc phá schema DB:

```
refactor: migrate all entity ids from Long to UUIDv7

BREAKING CHANGE: mọi @PathVariable, *Request/*Response trong common-lib
và mọi FK cross-service đổi từ Long sang UUID. ddl-auto: update không
alter được kiểu cột — bắt buộc drop và tạo lại database dev.
```

**Kích thước commit:**

- Một commit = một thay đổi logic hoàn chỉnh, build được. Không commit code không compile.
- Chia commit theo bước (`initialize module` → `add shared payloads` → `add Fare` → `add FareRules` → `add BaggagePolicy`) thay vì một commit khổng lồ.
- Không commit `target/`, `.idea/`, `.vscode/`, `*.iml` (đã có trong `.gitignore` — đừng `git add -f`).

### Pull Request Convention

**Title:** viết đúng như một commit message conventional — **không** để tên nhánh tự sinh (`feat(pricing): add fare, fare rules and baggage policy management`, không phải `Feature/pricing service`).

**Description — template tối thiểu:**

```markdown
## Mục đích
<Giải quyết vấn đề gì, tại sao cần>

## Thay đổi chính
- <thay đổi 1>
- <thay đổi 2>

## Cách kiểm thử
mvn -pl services/pricing-service -am clean install
mvn -pl services/pricing-service spring-boot:run
<endpoint đã gọi thử, kết quả mong đợi>

## Breaking change
<Có/Không — nếu có, ghi rõ client nào phải sửa, có phải drop DB không>

## Checklist
- [ ] Không có annotation quan hệ mới (`@ManyToOne`/`@OneToMany`/`@OneToOne`/`@ManyToMany`/`@JoinColumn`/`@ElementCollection`)
- [ ] Không có `repository.*` hay HTTP client nằm trong `for`/`stream().map()`/`forEach`
- [ ] Endpoint trả list đã phân trang (`Page<T>` + `Pageable`)
- [ ] Method ghi đụng ≥ 2 bảng đã có `@Transactional`
- [ ] `@RequestBody` đã có `@Valid`
- [ ] Cột mới dùng để lọc đã có `@Index`
- [ ] Controller inject qua interface, không phải `*ServiceImpl`
- [ ] Đã bật SQL log và đếm query cho endpoint trả list
```

- PR luôn nhắm vào `develop`.
- PR nhỏ, tập trung. Trên ~400 dòng thay đổi thì cân nhắc tách — trừ đợt refactor nền tảng buộc phải làm một lần.
- Tự review diff trước khi mở PR: `git diff develop...HEAD`.
- Không merge PR đang đỏ build.

### Merge Strategy

- **Merge commit** — mặc định cho `feature/*`: giữ lại lịch sử từng bước.
- **Squash** — cho nhánh lặt vặt nhiều commit rác kiểu `fix typo`, `fix again`.
- **KHÔNG force-push lên `develop`.** Force-push chỉ được phép trên nhánh feature cá nhân chưa ai khác cùng làm, và dùng `--force-with-lease`, không phải `--force`.

**Cập nhật nhánh feature khi develop chạy trước:**

```bash
# Ưu tiên rebase khi nhánh CHƯA push hoặc chỉ mình bạn làm — giữ lịch sử thẳng
git fetch origin
git rebase origin/develop

# Nếu nhánh đã push và có người khác cùng làm thì merge, đừng rebase
git merge origin/develop
```

### Dọn nhánh sau khi merge

Xoá nhánh ngay sau khi PR được merge, cả local lẫn remote:

```bash
git branch -d feature/pricing-service
git push origin --delete feature/pricing-service
```

### Lệnh Git hay dùng

```bash
# Bắt đầu một task mới
git checkout develop && git pull origin develop
git checkout -b feature/seat-service

# Xem mình đã thay đổi gì so với base trước khi mở PR
git diff develop...HEAD --stat
git diff develop...HEAD

# Kiểm tra không lỡ commit file build/IDE
git status --short

# Đẩy nhánh lần đầu
git push -u origin feature/seat-service

# Sửa lại commit message vừa tạo (CHỈ khi chưa push)
git commit --amend

# Xem lịch sử gọn
git log --oneline --graph --decorate -20
```

### Quy tắc cho AI/Claude khi tạo commit

- Tuân thủ Conventional Commits nghiêm ngặt: `<type>(<scope>): <subject>`
  - Types: `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `chore`, `build`, `ci`
  - Subject: thể mệnh lệnh (imperative), chữ thường, không dấu chấm cuối, tối đa 72 ký tự.
  - Scope chỉ lấy từ bảng scope ở trên; đụng nhiều module thì bỏ scope.
- Chỉ xuất **đúng một dòng** commit message.
- **TUYỆT ĐỐI KHÔNG** có co-author (`Co-Authored-By`), không footer, không dòng "Generated with Claude Code", không bọc trong markdown code block. Quy tắc này áp dụng cả cho mô tả Pull Request.
- Một commit = một thay đổi logic; tạo nhánh `<type>/<mô-tả>` trước, không commit lên `develop`; chỉ push khi được yêu cầu.

### Những điều TUYỆT ĐỐI không làm

- Commit trực tiếp lên `develop`.
- Force-push lên `develop`.
- Commit credential, mật khẩu DB thật, JWT secret.
- Commit `target/`, `.idea/`, `.vscode/`, `*.iml`.
- Commit code không compile được, hoặc "commit tạm để mai sửa" lên nhánh chung.
- Gộp refactor lớn chung với feature mới trong một PR.

---

## 18. Reference

- **Việc còn tồn đọng / bug đã biết / nợ kỹ thuật** → [`docs/REFACTOR-BACKLOG.md`](docs/REFACTOR-BACKLOG.md). File `CLAUDE.md` này chỉ chứa rule/convention.
- Chưa có `README.md` hay tài liệu thiết kế khác — `CLAUDE.md` là nguồn quy tắc chính.
