# CLAUDE.md

File này cung cấp hướng dẫn cho Claude Code khi làm việc với code trong repository này.

## Project Overview

**Airline Microservices** (`com.nitditto:airline-microservice`, repo `Nitditto/airport-system`) — hệ thống quản lý hàng không dạng multi-module Maven, tách theo domain thành các microservice độc lập (airline core, flight operations, location, pricing, user/auth).

## Tech Stack

- Java 17 + Spring Boot 4.0.7 + Spring Cloud 2025.1.0
- MySQL + Spring Data JPA (Hibernate `ddl-auto: update`)
- Lombok
- Maven multi-module reactor build
- Spring Security + JWT tự viết (`JwtProvider`, `JwtConstant`) — chỉ có ở `user-service`

**Chưa có** (lưu ý để không giả định nhầm khi code): service discovery (Eureka), config server, API gateway, Feign client, message broker (Kafka/RabbitMQ), Redis cache, Flyway/Liquibase, PostgreSQL, MapStruct, OpenAPI/Swagger, SLF4J logging, exception handling framework, CI/CD, Docker.

## Repository & Module Structure

Reactor 3 cấp: `root pom` → (`common-lib`, `cloud`, `services`) → `services/pom.xml` → 5 service module.

```
microservices/
├── pom.xml                          # Parent POM — quản lý version tập trung
│                                     # (java.version, spring.boot.version, spring.cloud.version)
├── common-lib/                      # Shared jar — mọi service phụ thuộc vào đây
│   └── src/main/java/com/nitditto/
│       ├── embeddable/              # @Embeddable value objects (Address, GeoCode, SeatBenefits...)
│       ├── enums/                   # AircraftStatus, AirlineStatus, CabinClassType, FlightStatus, UserRole
│       └── payload/
│           ├── dto/                 # UserDTO
│           ├── request/             # *Request DTO cho MỌI service (tập trung tại đây)
│           └── response/            # *Response DTO + ApiResponse, AuthResponse
├── cloud/                           # Aggregator RỖNG — service-registry/config-server/api-gateway
│                                     # mới chỉ là comment, CHƯA implement (xem "Known Gaps")
└── services/
    ├── pom.xml                      # Aggregator của 5 service, là parent thật của từng service
    ├── user-service/        (port 5001)  # Auth, JWT, Spring Security
    ├── airline-core-service/(port 5002)  # Aircraft, Airline
    ├── location-service/    (port 5004)  # Airport, City
    ├── flight-ops-service/  (port 5005)  # Flight, FlightInstance, FlightSchedule
    └── pricing-service/     (port 5005)  # Fare, FareRules, BaggagePolicy — TRÙNG PORT với flight-ops-service, cần sửa
```

Mỗi service có package layout giống nhau dưới `com.nitditto`:
```
controller/    # @RestController
service/       # interface — hợp đồng nghiệp vụ
service/impl/  # *ServiceImpl — cài đặt, @Service @RequiredArgsConstructor
repository/    # Spring Data JPA interface
model/         # @Entity
mapper/        # static method mapper Entity <-> DTO (viết tay, không dùng MapStruct)
config/        # (chỉ user-service hiện có: SecurityConfig, JwtProvider, JwtConstant)
```

## Build Commands

**Không có Maven Wrapper ở thư mục gốc** — dùng `mvn` cài trên máy. Mỗi service con có `mvnw`/`mvnw.cmd`/`.mvn/` riêng (rác còn lại từ lúc mỗi service được khởi tạo độc lập bằng Spring Initializr trước khi gộp vào reactor) — **đừng dùng** các wrapper lẻ đó, luôn chạy `mvn` từ thư mục gốc để giữ đúng version do parent POM quản lý.

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

**Lưu ý khi thêm dependency/version:** chỉ khai báo `<version>` ở root `pom.xml` (`dependencyManagement`) hoặc dùng version do `spring-boot-dependencies`/`spring-cloud-dependencies` BOM quản lý. Service con KHÔNG tự ghim version riêng — tránh lệch version giữa các service. Vi phạm hiện có cần dọn: `user-service/pom.xml` đang tự ghim `<version>0.13.0</version>` cho 3 dependency `jjwt-*` thay vì khai ở `dependencyManagement` gốc.

## Architecture

### Data Flow (trong một service)

```
Request → Controller → Service (interface) → ServiceImpl → Repository → MySQL
                                                    ↓
                                              Mapper (Entity <-> DTO)
```

## NO JPA Relationship Annotations (BẮT BUỘC — áp dụng CẢ trong cùng một service)

Đây là rule nền tảng của project, chi phối cách viết entity, repository, service và mapper. **Mọi khoá ngoại đều là cột `UUID` thô**, không có exception — kể cả khi hai entity nằm cùng một service và cùng một database.

### Danh sách annotation bị CẤM

`@ManyToOne` · `@OneToMany` · `@OneToOne` · `@ManyToMany` · `@JoinColumn` · `@JoinTable` · `@ElementCollection` · `@MapsId` · mọi `CascadeType` và `orphanRemoval`

```java
// ❌ SAI — không được xuất hiện trong bất kỳ entity nào
@Entity
public class Fare extends BaseEntity {

    @OneToOne(mappedBy = "fare", cascade = CascadeType.ALL, orphanRemoval = true)
    private BaggagePolicy baggagePolicy;          // KHÔNG ĐƯỢC

    @OneToOne(mappedBy = "fare", cascade = CascadeType.ALL, orphanRemoval = true)
    private FareRules fareRules;                  // KHÔNG ĐƯỢC

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

    @Column(name = "flight_id", nullable = false)
    private UUID flightId;                        // trỏ sang flight-ops-service

    @Column(name = "cabin_class_id", nullable = false)
    private UUID cabinClassId;                    // trỏ sang airline-core-service
}

// Chiều ngược lại cũng vậy — con giữ ID của cha, cha KHÔNG giữ tham chiếu tới con
@Entity
@Table(name = "baggage_policies")
public class BaggagePolicy extends BaseEntity {

    @Column(name = "fare_id", nullable = false, unique = true)
    private UUID fareId;
}
```

**Lý do:** loại bỏ hoàn toàn N+1 do lazy/eager loading; kiểm soát tường minh thời điểm load dữ liệu liên quan; entity serialize/deserialize đơn giản, không cần `@JsonIgnore` để chống vòng lặp vô hạn; và quan trọng nhất — ranh giới service không bị phá vỡ khi một entity sau này bị tách sang service khác (đổi chỗ chỉ là chuyện của tầng service, entity không đổi).

### Ngoại lệ được phép: `@Embedded` / `@Embeddable`

`@Embedded` **KHÔNG phải** relationship — nó nhúng field vào chính bảng đó, không sinh join, không sinh query phụ, không gây N+1. Các benefit block của `Fare` (`SeatBenefits`, `BoardingBenefits`, `InFlightBenefits`, `FlexibilityBenefits`, `PremiumServiceBenefits`) và `Address`, `GeoCode` trong `common-lib` là hợp lệ — **giữ nguyên, đừng gỡ khi refactor**.

```java
// ✅ ĐÚNG — cùng bảng, không join
@Embedded
private SeatBenefits seatBenefits = new SeatBenefits();
```

### Thay thế `@ElementCollection`

`@ElementCollection` bị cấm vì nó sinh bảng phụ và lazy-load y hệt `@OneToMany`. Trường hợp thật trong repo: `FlightSchedule.operatingDays` kiểu `List<DayOfWeek>`. Thay bằng `AttributeConverter` lưu vào một cột duy nhất:

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

Converter đặt trong package `converter/` của service sở hữu entity (hoặc `common-lib/converter/` nếu ≥2 service dùng).

### Hệ quả 1 — Mất `cascade`/`orphanRemoval`, phải tự xử lý ở tầng service

Trước đây `fareRepository.delete(fare)` tự xoá luôn `BaggagePolicy` và `FareRules`. Giờ **không còn**, phải xoá thủ công trong cùng một transaction:

```java
// ✅ ĐÚNG — xoá con trước, cha sau, trong 1 transaction
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

**Bắt buộc:** mọi method xoá một entity có bản ghi con phải có `@Transactional` và xoá con tường minh. Thiếu `@Transactional` ở đây sẽ để lại dữ liệu mồ côi khi có lỗi giữa chừng.

### Hệ quả 2 — Mapper nhận dữ liệu liên quan qua tham số

Mapper không còn tự truy ngược quan hệ được (`fare.getFareRules()` không tồn tại nữa), nên dữ liệu liên quan phải được service load sẵn và truyền vào:

```java
// ❌ SAI — mapper tự đi lấy quan hệ (không còn khả thi), hoặc tệ hơn là inject repository vào mapper
public static FareResponse toResponse(final Fare fare) {
    return FareResponse.builder()
            .fareRules(FareRuleMapper.toResponse(fare.getFareRules()))   // KHÔNG CÒN
            .build();
}

// ✅ ĐÚNG — service load rồi truyền vào; mapper vẫn là hàm static thuần, không phụ thuộc DB
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

Giữ mapper là **hàm static thuần, không được inject repository/service vào mapper** — nếu mapper cần đi query thì đó là dấu hiệu logic đặt sai chỗ, phải đẩy lên service.

Nếu số tham số vượt 3, gói lại thành một record ngữ cảnh:

```java
public record FareContext(Fare fare, FareRules fareRules, BaggagePolicy baggagePolicy) {}

public static FareResponse toResponse(final FareContext context) { ... }
```

### Hệ quả 3 — Load dữ liệu liên quan bằng batch query ở tầng service

Đây là phần quan trọng nhất và cũng là chỗ dễ đẻ ra N+1 nhất — xem đầy đủ ở mục "N+1 Query Prevention" bên dưới. Nguyên tắc: **một query cho cha, một query `IN` cho mỗi loại con, rồi ghép trong bộ nhớ bằng `Map`.**

### Các vi phạm hiện có phải gỡ bỏ khi refactor

| Service | Entity | Annotation hiện tại | Thay bằng |
|---|---|---|---|
| pricing | `Fare.baggagePolicy` | `@OneToOne(mappedBy)` | bỏ hẳn; `BaggagePolicy.fareId` là `UUID` |
| pricing | `Fare.fareRules` | `@OneToOne(mappedBy)` | bỏ hẳn; `FareRules.fareId` là `UUID` |
| pricing | `FareRules.fare`, `BaggagePolicy.fare` | `@OneToOne` | `private UUID fareId;` |
| airline-core | `Aircraft.airline` | `@ManyToOne` | `private UUID airlineId;` |
| flight-ops | `FlightSchedule.flight` | `@ManyToOne` | `private UUID flightId;` |
| flight-ops | `FlightInstance.flight` | `@ManyToOne` | `private UUID flightId;` |
| flight-ops | `FlightSchedule.operatingDays` | `@ElementCollection` | `@Convert` + `DayOfWeekSetConverter` |
| location | `Airport.city` | `@ManyToOne` | `private UUID cityId;` |

Lưu ý khi gỡ: `FlightInstanceRepository` đang có `@Query` dùng `fi.flight.id = :flightId` — phải đổi thành `fi.flightId = :flightId`. Rà toàn bộ `@Query` sau khi gỡ quan hệ, vì JPQL sẽ fail lúc khởi động chứ không phải lúc compile.

### Cross-Service Communication

Chưa có Feign/service discovery, nên giao tiếp giữa các service là **tham chiếu bằng UUID + endpoint batch-fetch**:

```java
// Consumer tự batch-fetch khi cần data đầy đủ, ví dụ pricing-service expose:
POST /api/fares/batch-by-ids   // nhận List<UUID>, trả về Map<UUID, FareResponse>
```

**Quy tắc bắt buộc khi thêm liên kết mới giữa 2 service:**
- Field tham chiếu luôn là `UUID`, đặt tên `<entity>Id`. Không bao giờ map sang entity của service khác (khác database, vỡ ngay khi chạy).
- Nếu cần load nhiều bản ghi liên quan, cung cấp endpoint `POST /batch-by-ids` nhận `List<UUID>`, tránh gọi API service khác trong loop — xem "N+1 Query Prevention / Loại 2".
- Khi module `cloud/` (Eureka + Gateway) được dựng lên, thay các lời gọi HTTP thủ công bằng OpenFeign client đặt trong `common-lib` hoặc package `client/` riêng của service gọi — không hardcode `localhost:port`.

## N+1 Query Prevention (QUAN TRỌNG)

Vì đã cấm hoàn toàn relationship annotation, **N+1 do lazy/eager loading không còn tồn tại** — Hibernate không có gì để tự đi load ngầm nữa. Đổi lại, việc ghép dữ liệu chuyển sang tay bạn ở tầng service, và đó chính là chỗ N+1 sẽ quay lại nếu viết ẩu. Còn **2 loại** phải phòng.

### Loại 1 — Gọi repository trong vòng lặp

Đây là dạng phổ biến nhất sau khi bỏ relationship, vì mọi phép "join" giờ đều do bạn tự viết.

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

**Công thức chuẩn — thuộc lòng 4 bước:**
1. Query danh sách cha (có phân trang).
2. Trích `List<UUID>` các ID cần tra cứu, nhớ `.distinct()`.
3. Một query `...In(ids)` cho **mỗi loại** dữ liệu con → gom về `Map<UUID, T>`.
4. Lặp qua cha, tra `Map` bằng `get()`, không đụng vào repository nữa.

Số query luôn là **hằng số** theo số loại quan hệ, không phụ thuộc số dòng.

Với dữ liệu tổng hợp (đếm/sum/min), dùng aggregate query một lần thay vì tính từng dòng:

```java
// ❌ SAI
for (final Flight flight : flights) {
    final long count = fareRepository.countByFlightId(flight.getId());   // N query!
}

// ✅ ĐÚNG — 1 query gom nhóm
@Query("""
        select f.flightId, count(f) from Fare f
        where f.flightId in :flightIds
        group by f.flightId
        """)
List<Object[]> countByFlightIds(@Param("flightIds") List<UUID> flightIds);
```

Khi kết quả gồm nhiều cột, ưu tiên **DTO projection** thay vì `Object[]` để khỏi ép kiểu thủ công:

```java
public record FareCountByFlight(UUID flightId, Long total) {}

@Query("""
        select new com.nitditto.payload.projection.FareCountByFlight(f.flightId, count(f))
        from Fare f where f.flightId in :flightIds group by f.flightId
        """)
List<FareCountByFlight> countByFlightIds(@Param("flightIds") List<UUID> flightIds);
```

### Loại 2 — Gọi HTTP sang service khác trong vòng lặp (nguy hiểm nhất)

N+1 qua network tệ hơn N+1 qua DB nhiều lần (mỗi lần là một round-trip TCP + serialize JSON, và nhân lên theo độ trễ mạng). Vì các service ở đây tham chiếu nhau bằng ID thô, đây là cái bẫy dễ mắc nhất khi bắt đầu nối service với nhau:

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

**Rule:** mọi service expose dữ liệu cho service khác **bắt buộc** có endpoint batch nhận `List<UUID>` và trả `Map<UUID, XxxResponse>` (pattern `getFareByIds` của pricing-service đang làm đúng — nhân rộng ra các service còn lại). Nhớ `.distinct()` danh sách ID trước khi gọi.

### Cấu hình bắt buộc trong `application.yaml`

```yaml
spring:
  jpa:
    open-in-view: false     # giải phóng DB connection ngay khi service trả về,
                            # không giữ tới lúc render response
    show-sql: true          # DEV ONLY — để đếm query thực tế, tắt ở production
    properties:
      hibernate:
        format_sql: true
logging:
  level:
    org.hibernate.SQL: DEBUG          # DEV ONLY
```

Không thể phòng N+1 nếu không nhìn thấy nó — bật SQL log rồi gọi thử endpoint trả list và **đếm số dòng SQL**. Nếu số query tăng theo số bản ghi thì đang có N+1, không cần đoán.

### Checklist khi review code (bắt buộc tự soát trước khi kết thúc một task)

- [ ] Entity mới có lọt annotation quan hệ nào không (`@ManyToOne`, `@OneToMany`, `@OneToOne`, `@ManyToMany`, `@JoinColumn`, `@ElementCollection`)?
- [ ] Có lời gọi `repository.*` hay HTTP client nào nằm trong `for` / `stream().map()` / `forEach` không?
- [ ] Mỗi loại dữ liệu con có đúng **một** query `...In(ids)` không, và đã `.distinct()` danh sách ID chưa?
- [ ] Endpoint trả list đã phân trang chưa?
- [ ] Đã bật SQL log và đếm số query thực tế cho endpoint trả list chưa?

## Repository & Query Rules

### Bắt buộc phân trang cho MỌI endpoint trả về list

`location-service`, `airline-core-service`, `flight-ops-service` đã làm đúng (`Page<T>` + `Pageable`). Riêng `pricing-service` còn `getFares()` gọi `findAll()` không giới hạn — đây là quả bom hẹn giờ, bảng `fares` lớn dần là sập.

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

- Không có ngoại lệ cho endpoint "lấy tất cả" — nếu client thật sự cần toàn bộ, bắt nó phân trang và lặp.
- Endpoint `batch-by-ids` là ngoại lệ hợp lệ (client đã tự giới hạn bằng danh sách ID), nhưng nên chặn kích thước đầu vào (`@Size(max = 200)` trên `List<UUID>`).

### Repository chỉ chứa truy vấn, không chứa business logic

Repository là Spring Data JPA thuần: method name query, `@Query` khi cần, `Specification` cho filter động. **Không** đặt logic nghiệp vụ, không đặt mapping Entity↔DTO, không gọi service khác từ repository.

### `Specification` cho filter động — thay cho `@Query` với chuỗi `(:param is null or ...)`

`FlightRepository` và `FlightInstanceRepository` hiện dùng pattern `(:depId is null or f.departureAirportId = :depId)`. Cách này chạy được nhưng có 2 nhược: DB không tối ưu được execution plan (điều kiện `is null` chặn việc dùng index), và thêm một filter là phải sửa cả câu JPQL. Khi số filter ≥ 3, chuyển sang `Specification`:

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
// Ghép ở tầng service — trả null nghĩa là "bỏ qua điều kiện này",
// Spring Data tự loại khỏi WHERE nên SQL sinh ra chỉ chứa filter thực sự có giá trị
final Specification<Flight> spec = Specification
        .where(FlightSpecs.airlineId(airlineId))
        .and(FlightSpecs.departureAirportId(departureAirportId));

return this.flightRepository.findAll(spec, pageable).map(FlightMapper::toResponse);
```

Đặt các lớp `*Specs` trong package `repository/spec/` của service, là `final class` với constructor private (chỉ chứa static factory method, không phải bean).

### Kiểm tra tồn tại: dùng `existsBy*`, không `findBy*` rồi check null

Pattern này repo đang làm **đúng** ở cả 5 service (`existsByFlightIdAndCabinClassIdAndName`, `existsByCityCode`, `existsByFlightNumber`...) — giữ nguyên, đừng thay bằng `findBy`:

```java
// ❌ SAI — kéo cả entity về chỉ để biết có tồn tại hay không
if (fareRepository.findByName(name).isPresent()) { ... }
if (fareRepository.countByName(name) > 0) { ... }

// ✅ ĐÚNG — SQL chỉ trả về boolean
if (this.fareRepository.existsByName(name)) { ... }
```

Quy ước cho luồng update: dùng biến thể `*AndIdNot` để loại chính bản ghi đang sửa ra khỏi phép kiểm tra trùng (`existsByCityCodeAndIdNot`) — repo đang làm đúng, nhân rộng cho mọi unique check.

### Ghi hàng loạt: `saveAll()`, không `save()` trong vòng lặp

```java
// ❌ SAI — N lần round-trip tới DB
for (final Fare fare : fares) {
    fareRepository.save(fare);
}

// ✅ ĐÚNG — 1 lần, Hibernate gom thành batch insert
this.fareRepository.saveAll(fares);
```

Để `saveAll()` thật sự batch dưới JDBC, cần bật trong `application.yaml` (hiện chưa service nào bật):

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc.batch_size: 50
        order_inserts: true
        order_updates: true
```

Batch insert chỉ hoạt động khi ID **không** do DB sinh. Vì project dùng UUIDv7 sinh ở tầng ứng dụng (xem "Entity Design Rules"), Hibernate biết ID trước khi insert nên gom batch được — đây là một lợi ích trực tiếp của việc bỏ `IDENTITY`.

Kèm theo: `saveAll()` một danh sách rất lớn vẫn giữ toàn bộ entity trong persistence context. Với >5.000 bản ghi, chia lô và `flush()`/`clear()` sau mỗi lô để tránh phình heap.

### Chỉ đọc vài cột thì dùng projection, đừng load entity đầy đủ

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

Đặc biệt hữu ích với `Fare` vì entity này có 5 `@Embedded` benefit block — load đầy đủ để chỉ đọc giá là rất lãng phí.

### Index cho cột được query thường xuyên (repo hiện KHÔNG có cái nào)

Toàn repo không khai một `@Index` nào. Với `ddl-auto: update`, Hibernate chỉ tự tạo index cho khoá chính và unique constraint — **cột dùng để lọc thì không có index**, nghĩa là mọi truy vấn đang full table scan.

```java
// ✅ ĐÚNG — khai index cho đúng tổ hợp cột mà repository thực sự query
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

Các cột đang cần index ngay: `fares(flight_id, cabin_class_id)`, `flights(airline_id)`, `flight_instances(airline_id, departure_date_time)`, `airports(city_id)`, `aircrafts(airline_id)`. Quy tắc: **thêm repository method lọc theo cột nào thì kiểm tra cột đó đã có index chưa**, và index tổ hợp phải đặt cột lọc bằng `=` lên trước cột lọc theo khoảng.

### `@Transactional` — bắt buộc và nhất quán

Hiện chỉ `flight-ops-service` có, 4 service còn lại không có dòng nào.

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

- `readOnly = true` cho method đọc: Hibernate bỏ qua dirty checking → nhanh hơn và tránh vô tình ghi.
- **Bắt buộc `@Transactional`** cho mọi method ghi có đụng tới ≥2 bảng — đặc biệt là các luồng xoá cha-con thủ công, vì đã không còn `cascade` để dựa vào (xem "NO JPA Relationship Annotations / Hệ quả 1").
- `@Transactional` chỉ có tác dụng khi được gọi từ **bên ngoài** bean. Gọi `this.someTransactionalMethod()` trong cùng class sẽ bỏ qua proxy và **không** mở transaction — tách sang bean khác nếu cần.

## common-lib Usage Rules (BẮT BUỘC)

- **DTO dùng chung nhiều service** (Request/Response đại diện cho một entity mà service khác cần đọc) → đặt trong `common-lib/payload/request|response`.
- **Enum nghiệp vụ dùng chung** (status, role, type) → `common-lib/enums`.
- **`@Embeddable` value object dùng chung** (Address, GeoCode...) → `common-lib/embeddable`.
- **KHÔNG** đặt Entity (`@Entity`) trong `common-lib` — entity luôn thuộc về đúng 1 service, giữ database boundary rõ ràng.
- **KHÔNG** đặt business logic hay Service/Repository trong `common-lib` — chỉ chứa model dữ liệu thuần (DTO/enum/embeddable) + validation annotation.
- Thêm class mới vào `common-lib` nghĩa là ít nhất 2 service sẽ dùng nó — nếu chỉ 1 service dùng, giữ trong package `payload` cục bộ của service đó (nếu có) hoặc trong service đó luôn.

## Entity Design Rules

### BaseEntity + UUIDv7 (BẮT BUỘC)

Toàn bộ khoá chính dùng **UUID version 7**, sinh ở tầng ứng dụng. Hiện mỗi entity tự khai `id`/`createdAt`/`updatedAt` riêng lẻ với `Long` + generation strategy không đồng nhất (`AUTO` ở location/pricing/user, `IDENTITY` ở airline-core/flight-ops) — chuẩn hoá hết về `BaseEntity` đặt trong `common-lib`:

```java
// common-lib/entity/BaseEntity.java
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(algorithm = UuidVersion7Strategy.class)
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", columnDefinition = "binary(16)", updatable = false, nullable = false)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
```

`UuidVersion7Strategy` là `org.hibernate.id.uuid.UuidVersion7Strategy` — có sẵn trong Hibernate 7 mà Spring Boot 4.0.7 kéo về, **không cần thư viện ngoài**.

**Quy tắc bắt buộc:**

- **CẤM `@GeneratedValue(strategy = GenerationType.UUID)`** — annotation này trông rất hợp lý nhưng sinh UUID **v4 ngẫu nhiên**, làm phân mảnh index và mất sạch lợi ích của v7. Đây là lỗi âm thầm, không báo gì cả, chỉ lộ ra khi bảng đã lớn. Chỉ dùng đúng cặp `@GeneratedValue` + `@UuidGenerator(algorithm = UuidVersion7Strategy.class)` như trên.
- **`BaseEntity` là nơi DUY NHẤT khai báo ID.** Entity con không được tự khai lại `id`, `createdAt`, `updatedAt`.
- **Lưu dạng `binary(16)`**, không phải `char(36)` — tiết kiệm 20 byte mỗi khoá và mỗi index. Đánh đổi: query tay trong MySQL Workbench phải bọc `BIN_TO_UUID(id)` / `UUID_TO_BIN('...')`.
- **Mọi FK cross-service và trong cùng service đều là `UUID`**, khai kèm `columnDefinition = "binary(16)"` để khớp kiểu với PK:

```java
@Column(name = "flight_id", columnDefinition = "binary(16)", nullable = false)
private UUID flightId;
```

- **`ddl-auto: update` KHÔNG đổi được kiểu cột đã tồn tại** — Hibernate chỉ thêm bảng/cột mới. Việc chuyển từ `Long` sang `UUID` bắt buộc phải **drop và tạo lại database dev**, không có đường migrate tự động.

**Không tự override `equals()`/`hashCode()` theo `id`.** Với UUID sinh ở tầng app, ID đã có sẵn trước khi persist nên so sánh theo `id` là an toàn, nhưng chỉ làm khi thực sự cần bỏ entity vào `Set`/`Map`. Mặc định cứ để nguyên và **không bao giờ dùng `@Data`/`@EqualsAndHashCode` của Lombok trên entity** (nó sinh `equals` trên toàn bộ field, gây lỗi khó lần).

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

## Coding Standards

### SOLID Principles (Bắt buộc)

**S - Single Responsibility** — Controller chỉ xử lý HTTP, Service chỉ chứa business logic của một domain, tách service nếu class vượt 200 dòng.

**O - Open/Closed** — mở rộng qua interface/Strategy, không sửa method hiện có để nhét thêm nhánh `if` cho case mới.

**L - Liskov Substitution** — `*ServiceImpl` phải thay thế được interface mà không đổi hành vi; không override để throw `UnsupportedOperationException`.

**I - Interface Segregation** — interface nhỏ, gọn theo domain (`FareService`, không gộp `FareService` + `BaggagePolicyService` vào 1 interface).

**D - Dependency Inversion** — **BẮT BUỘC** controller và service khác inject qua interface, không inject thẳng `*ServiceImpl`.

### Dependency Injection Pattern (đã áp dụng đúng trong pricing-service — giữ nguyên)

```java
// ✅ ĐÚNG
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

### Java Naming Conventions

- **Package**: lowercase, không underscore — `com.nitditto.pricing.service` (nếu tách sub-package theo domain).
- **Class/Interface**: PascalCase danh từ, interface không prefix `I`, implementation suffix `Impl` (`FareService` / `FareServiceImpl`).
- **Method**: camelCase, bắt đầu bằng động từ — `findById()`, `createFare()`, `isActive()`. Boolean: `is*`, `has*`, `can*`.
- **Variable**: camelCase có nghĩa (`flightId`, không viết tắt `fltId`); collection dùng số nhiều (`fares`, `flights`).
- **Constant**: SCREAMING_SNAKE_CASE.
- **DTO naming (giữ nguyên convention hiện có)**:
  ```
  *Request   — dùng chung cho cả create và update (không tách CreateXRequest/UpdateXRequest)
  *Response  — response trả về client
  ```

### Clean Code Rules

- Method tối đa ~20 dòng, tối đa 3 parameter (nhiều hơn thì gói vào Request object).
- Một method làm một việc.
- Không comment giải thích WHAT, chỉ comment khi giải thích WHY (constraint ẩn, workaround).
- Không catch `Exception` chung chung ở tầng service để nuốt lỗi; ném exception cụ thể (xem mục Exception Handling).
- Không return `null` cho các phương thức tìm theo id — dùng `Optional` ở repository, ném exception cụ thể ở service.

### `final` & `this` keyword

- Method parameter và local variable không đổi giá trị: dùng `final`.
- Luôn dùng `this.` khi truy cập field/method của instance trong service/mapper để phân biệt với parameter cùng tên.

### Loop vs Stream

- Ưu tiên `for` loop ở hot path (mapping list lớn, batch xử lý); Stream OK cho logic đơn giản không phải hot path.

## Exception Handling (CẦN BỔ SUNG — hiện repo CHƯA có, đây là rule bắt buộc áp dụng khi refactor)

Hiện tại toàn bộ service ném `throw new Exception("...")` thô và controller khai `throws Exception` — không có custom exception, không có `@RestControllerAdvice`. Khi refactor, áp dụng:

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
}
```

- `ResourceNotFoundException`/`BusinessException` đặt trong `common-lib` để mọi service dùng chung; `GlobalExceptionHandler` đặt riêng trong từng service (vì `@RestControllerAdvice` scope theo application context).
- Service ném `ResourceNotFoundException`/`BusinessException` thay vì `throw new Exception(...)`; controller bỏ `throws Exception`.

## API Response Wrapper (CẦN NÂNG CẤP)

`ApiResponse` hiện tại trong `common-lib` chỉ có 1 field `message`, và chỉ được dùng ở `HomeController` — các endpoint thật trả thẳng DTO, không bọc response. Khi refactor, chuẩn hoá thành generic wrapper và áp dụng cho MỌI endpoint:

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

Đây là thay đổi breaking cho mọi client hiện có gọi các service — cần đồng bộ refactor tất cả controller cùng lúc, không làm nửa vời (một số endpoint bọc `ApiResponse`, số khác trả thẳng DTO).

## Validation

- Request DTO dùng `jakarta.validation.constraints` (`@NotBlank`, `@NotNull`, `@Positive`, `@Size`).
- **BẮT BUỘC** mọi `@RequestBody` ở controller phải có `@Valid` đi kèm — hiện tại bị thiếu ở một số method (vd. update), cần rà soát và bổ sung khi refactor, không chỉ sửa chỗ đang làm.

## Logging (CẦN BỔ SUNG — hiện chưa có SLF4J trong repo)

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

- Thêm `@Slf4j` cho class Service khi refactor; log ở mức `info` cho hành động ghi (create/update/delete), `warn`/`error` khi bắt exception nghiệp vụ.
- Không log dữ liệu nhạy cảm (password, token JWT).

## Database

- Mỗi service **một database riêng** (database-per-service) — đúng tinh thần này ở airline-core (`airline`) và location-service (`airline_location`), nhưng **user-service hiện đang trỏ nhầm vào `airline_location`** (copy-paste từ location-service) — phải tách riêng DB cho user-service khi refactor, ví dụ `airline_user`.
- `ddl-auto: update` chỉ chấp nhận ở môi trường dev hiện tại; không dùng cho production — nếu dự án tiến tới production, cân nhắc Flyway/Liquibase để quản lý schema migration thay vì để Hibernate tự sinh.

## Known Gaps / Refactor Backlog (đọc trước khi bắt đầu refactor)

Đây là các vấn đề cụ thể phát hiện được trong codebase hiện tại — không phải style nit, mà là bug/thiếu sót cần xử lý.

### Nhóm A — Refactor nền tảng (phải làm cùng một đợt, drop DB dev)

Ba việc dưới đây đụng vào cùng những file entity/mapper/service, và đều đòi drop database vì `ddl-auto: update` không alter được kiểu cột. Làm riêng lẻ sẽ phải sửa đi sửa lại — gom một đợt:

1. **Chuyển toàn bộ ID sang UUIDv7** — hiện là `Long` với strategy không đồng nhất (`AUTO` ở location/pricing/user, `IDENTITY` ở airline-core/flight-ops). Chuẩn hoá về `BaseEntity` trong `common-lib`. Kéo theo: mọi FK cross-service, mọi `*Request`/`*Response` trong `common-lib`, mọi `@PathVariable`, mọi signature service/repository. Xem "Entity Design Rules".
2. **Gỡ toàn bộ relationship annotation** — 8 chỗ vi phạm (bảng chi tiết ở mục "NO JPA Relationship Annotations"). Kéo theo: mất `cascade`/`orphanRemoval` nên phải viết tay luồng xoá cha-con, và mapper phải đổi signature để nhận dữ liệu liên quan qua tham số.
3. **Sửa `@Query` sau khi gỡ quan hệ** — `FlightInstanceRepository` đang dùng `fi.flight.id = :flightId`, phải đổi thành `fi.flightId = :flightId`. JPQL sai chỉ fail lúc khởi động chứ không phải lúc compile, nên phải rà hết.

### Nhóm B — Bug cấu hình (sửa độc lập, nhanh) — ĐÃ XỬ LÝ XONG

4. ~~**Port collision**~~ — **đã tự fix trước đó**: `flight-ops-service` đã đổi sang `server.port: 5003` (commit "chore: change port to 5003", PR #6). Port thật hiện tại: `user=5001, airline-core=5002, flight-ops=5003, location=5004, pricing=5005` — không còn trùng. Bảng ở mục "Repository & Module Structure" phía trên phản ánh số port cũ đã lỗi thời, cần soát lại nếu thấy sai lệch với `application.yaml` thực tế của từng service.
5. ~~**`user-service` sai datasource`**~~ — **đã fix**: đổi từ `jdbc:mysql://localhost:3306/airline_location` (copy nhầm từ location-service) sang `jdbc:mysql://localhost:3306/airline_user`. Cần tự tạo database mới trước khi chạy: `CREATE DATABASE airline_user;` (Hibernate `ddl-auto: update` sẽ tự tạo bảng).
6. ~~**`SecurityConfig` của user-service đang `permitAll()` toàn bộ request`~~ — **đã fix**: thêm [`JwtAuthenticationFilter`](services/user-service/src/main/java/com/nitditto/config/JwtAuthenticationFilter.java) (`OncePerRequestFilter`) đọc header `Authorization: Bearer <token>`, validate bằng `JwtProvider.parseClaims()`, dựng `Authentication` từ claim `email`/`authorities` rồi nạp vào `SecurityContextHolder`. `SecurityConfig` giờ chỉ `permitAll()` cho `/` và `/auth/**`, còn lại bắt buộc `authenticated()`. Filter gắn trước `UsernamePasswordAuthenticationFilter` bằng `addFilterBefore`.
   - **Nợ kỹ thuật phát sinh, chưa xử lý** (ngoài phạm vi fix này, cân nhắc làm riêng): `UserController.getUserProfile()`/`getAllUsers()` đang tin tưởng header `X-User-Email` do client tự gửi để xác định danh tính — giờ endpoint đã yêu cầu đăng nhập nhưng **chưa** kiểm tra danh tính đó khớp với người dùng trong JWT, nên một user bất kỳ đã đăng nhập vẫn có thể đọc profile của người khác bằng cách tự đổi header. Cần sửa để lấy email từ `SecurityContextHolder` thay vì tin header client.
   - **Nợ kỹ thuật phát sinh khác**: `AuthServiceImpl.signup()` tạo `Authentication` bằng constructor 2 tham số (`new UsernamePasswordAuthenticationToken(email, password)`), constructor này luôn trả `getAuthorities()` rỗng — nghĩa là JWT sinh ra lúc đăng ký mới **không có claim `authorities`**, user vừa signup chưa có role nào cho tới lần login kế tiếp (`login()` dùng path tạo `Authentication` khác, có authorities đúng). Cần dùng cùng cách tạo `Authentication` như `login()`.

### Nhóm C — Chất lượng tầng dữ liệu

7. **Không có một `@Index` nào trong toàn repo** — mọi truy vấn lọc theo `flightId`, `cabinClassId`, `airlineId`, `cityId`... đang full table scan, vì `ddl-auto: update` chỉ tạo index cho PK và unique constraint.
8. **`@Transactional` dùng không nhất quán** — chỉ `flight-ops-service` có (`FlightServiceImpl`, `FlightScheduleServiceImpl`), 4 service còn lại không có dòng nào. Đặc biệt cấp thiết sau khi bỏ `cascade`, vì luồng xoá cha-con thủ công không có transaction sẽ để lại dữ liệu mồ côi.
9. **`pricing-service` thiếu phân trang** — `getFares()` dùng `findAll()` không giới hạn, trong khi 3 service khác đã dùng `Page<T>` + `Pageable` đúng chuẩn.
10. **`FareServiceImpl.getFares()` trả `List<Fare>` (entity thô)** thay vì `List<FareResponse>` — rò rỉ entity ra ngoài tầng service.
11. **Chưa bật JDBC batching** — không service nào cấu hình `hibernate.jdbc.batch_size`. Sau khi chuyển UUIDv7 thì batch insert mới hoạt động được, nên bật cùng lúc.
12. **`open-in-view` vẫn bật mặc định** — giữ DB connection tới tận lúc render response.

### Nhóm D — Chuẩn hoá tầng API

13. **Không có exception handling**: toàn repo dùng `throw new Exception(...)` + `throws Exception` ở controller — xem mục "Exception Handling".
14. **`ApiResponse` chưa phải generic wrapper thật** — chỉ dùng cho `HomeController`, các endpoint thật trả DTO trần.
15. **Thiếu `@Valid`** ở một số `@RequestBody` (vd. `FareController.updateFare`).
16. **Không có SLF4J logging** ở bất kỳ service nào.

### Nhóm E — Ngoài phạm vi doc này

17. **`cloud/` module rỗng** — service registry / config server / API gateway mới là comment trong `pom.xml`. Cho tới khi dựng xong, service gọi nhau phải hardcode `localhost:<port>`.
18. **Không có test, không có CI/CD, không có Docker/docker-compose** — cần lưu ý khi ước lượng công sức refactor.

## Git Convention (BẮT BUỘC)

Phần này chuẩn hoá lại đúng cách repo `Nitditto/airport-system` đang làm, và vá các chỗ đang lệch.

### Branch Model

Repo dùng mô hình **develop-centric**: `develop` là base branch mặc định (`origin/HEAD -> origin/develop`). **Chưa có `main`/`master`** — khi nào có release thật mới tạo `main`, còn hiện tại mọi PR đều nhắm vào `develop`.

```
develop                 ← base branch, luôn ở trạng thái build được
   ├── feature/xxx      ← nhánh làm việc, tách ra từ develop
   ├── fix/xxx
   └── docs/xxx
```

**Quy tắc:**
- **KHÔNG commit trực tiếp lên `develop`.** Mọi thay đổi đi qua nhánh riêng + Pull Request, kể cả sửa một dòng config (`chore: change port to 5003` đã làm đúng — đi qua nhánh `fix/flight-ops` và PR #6).
- Luôn tách nhánh mới từ `develop` đã cập nhật, không tách từ nhánh feature khác (trừ khi thật sự phụ thuộc, và phải ghi rõ trong PR).
- Một nhánh = một mục đích. Không gộp "thêm pricing-service" và "sửa port flight-ops" vào cùng nhánh.

### Branch Naming

```
<type>/<scope-hoặc-mô-tả-ngắn>
```

| Prefix | Dùng khi | Ví dụ thật trong repo |
|---|---|---|
| `feature/` | Thêm tính năng / module mới | `feature/pricing-service`, `feature/user-service` |
| `fix/` | Sửa bug, sửa cấu hình sai | `fix/flight-ops` |
| `docs/` | Chỉ đụng tài liệu | `docs/build-base` |
| `refactor/` | Đổi cấu trúc, không đổi hành vi | `refactor/uuid-migration` |
| `chore/` | Build, dependency, config vặt | `chore/bump-spring-boot` |
| `test/` | Chỉ thêm/sửa test | `test/fare-service` |

**Quy tắc đặt tên:**
- Toàn bộ **lowercase, kebab-case**. Không dấu tiếng Việt, không space, không camelCase.
- Ngắn gọn nhưng đủ nghĩa: `feature/pricing-service` tốt hơn `feature/new-stuff`.
- Nếu có issue tracker, thêm số vào cuối: `feature/pricing-service-42`.

> ⚠️ **Lệch đang tồn tại cần thống nhất:** repo có 5 nhánh dùng `feature/` nhưng nhánh `feat/seat-service` lại dùng `feat/`. **Chốt dùng `feature/`** cho branch (còn `feat` chỉ dùng làm *type của commit*, xem mục dưới) — hai khái niệm khác nhau, đừng trộn. Nhánh `feat/seat-service` hiện **chưa merge** vào develop, nên đổi tên trước khi mở PR:
> ```bash
> git branch -m feat/seat-service feature/seat-service
> git push origin :feat/seat-service
> git push -u origin feature/seat-service
> ```

### Commit Message — Conventional Commits

Repo đang theo Conventional Commits (conventionalcommits.org) — giữ nguyên và áp dụng nhất quán.

```
<type>(<scope>): <subject>

<body — tuỳ chọn, giải thích WHY>

<footer — tuỳ chọn: BREAKING CHANGE, Refs #issue>
```

**Bảng `type`:**

| Type | Ý nghĩa |
|---|---|
| `feat` | Thêm tính năng mới cho người dùng/API |
| `fix` | Sửa bug |
| `refactor` | Đổi code nhưng **không** đổi hành vi bên ngoài |
| `perf` | Tối ưu hiệu năng (vd. thêm index, gỡ N+1) |
| `docs` | Chỉ tài liệu (`CLAUDE.md`, `docs/ROADMAP.md`) |
| `test` | Thêm/sửa test |
| `chore` | Build, dependency, cấu hình, việc vặt không đụng logic |
| `style` | Format, khoảng trắng, import — không đổi logic |
| `build` | Maven POM, version, reactor structure |

**Bảng `scope`** — bám theo module trong reactor, dùng tên rút gọn (bỏ đuôi `-service`):

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
- Đụng **nhiều** module cùng lúc (vd. refactor UUID toàn repo) → **bỏ scope**: `refactor: migrate all entity ids to uuidv7`
- Không bịa scope ngoài bảng trên.

**Quy tắc viết `subject`:**
- **Tiếng Anh**, thể mệnh lệnh (imperative): `add`, `implement`, `fix`, `remove` — không phải `added`/`adding`/`adds`.
- Chữ đầu **thường**, **không** có dấu chấm cuối câu.
- Tối đa **72 ký tự**.
- Mô tả *cái gì thay đổi*, không phải *đã làm bao nhiêu file*.

```
✅ ĐÚNG (lấy từ chính repo)
feat(pricing): implement baggage policy management feature
feat(user): add user-service with authentication and jwt configuration
feat(common): add shared models and request/response payloads for pricing
chore: change port to 5003
docs: add project roadmap and CLAUDE documentation files

❌ SAI
Feature/pricing service              # tên nhánh, không phải commit message
feat(pricing): Implemented Fares.    # hoa đầu + quá khứ + dấu chấm cuối
update code                          # thiếu type, không rõ nghĩa
fix bug                              # không biết bug gì, ở đâu
feat(pricing): add fare + fix port + update docs   # gộp 3 việc vào 1 commit
```

> ⚠️ Repo hiện có vài commit thiếu scope dù chỉ đụng đúng một module — `feat: add location-service with airport and city controllers` lẽ ra là `feat(location): ...`. Từ giờ áp dụng đủ scope.

**Body — khi nào cần:**

Bỏ qua được với commit hiển nhiên. **Bắt buộc có** khi thay đổi cần giải thích lý do hoặc có đánh đổi:

```
perf(pricing): add composite index on fares(flight_id, cabin_class_id)

FareRepository lọc theo đúng cặp cột này ở 3 method, trước đó full table
scan vì ddl-auto: update chỉ tự tạo index cho PK và unique constraint.

Refs #12
```

- Body cách subject đúng **một dòng trống**.
- Wrap ở **72 ký tự**.
- Viết **WHY**, không viết WHAT — WHAT đã nằm trong diff rồi.

**Footer — breaking change:**

Bắt buộc khai báo khi thay đổi phá vỡ client đang gọi API hoặc phá schema DB:

```
refactor: migrate all entity ids from Long to UUIDv7

BREAKING CHANGE: mọi @PathVariable, *Request/*Response trong common-lib
và mọi FK cross-service đổi từ Long sang UUID. ddl-auto: update không
alter được kiểu cột — bắt buộc drop và tạo lại database dev.
```

Các thay đổi trong "Known Gaps / Nhóm A" và mục "API Response Wrapper" đều là breaking change — phải ghi footer này.

**Kích thước commit:**
- Một commit = một thay đổi logic hoàn chỉnh, build được. Không commit code không compile.
- Nhánh `feature/pricing-service` đang là mẫu tốt: tách thành 5 commit theo từng bước (`initialize module` → `add shared payloads` → `Fare` → `Fare Rules` → `Baggage Policy`) thay vì một commit khổng lồ.
- Không commit file build (`target/`), file IDE (`.idea/`, `.vscode/`) — `.gitignore` đã chặn sẵn, đừng dùng `git add -f` để lách.

### Pull Request Convention

**Title:** viết đúng như một commit message conventional — **không** để tên nhánh tự sinh.

```
✅ feat(pricing): add fare, fare rules and baggage policy management
❌ Feature/pricing service        # PR #5 đang bị thế này, GitHub tự sinh ra
```

**Description — template tối thiểu:**

```markdown
## Mục đích
<Giải quyết vấn đề gì, tại sao cần>

## Thay đổi chính
- <thay đổi 1>
- <thay đổi 2>

## Cách kiểm thử
./mvnw -pl services/pricing-service -am clean install
./mvnw -pl services/pricing-service spring-boot:run
<endpoint đã gọi thử, kết quả mong đợi>

## Breaking change
<Có/Không — nếu có, ghi rõ client nào phải sửa, có phải drop DB không>

## Checklist
- [ ] Không có annotation quan hệ mới (`@ManyToOne`/`@OneToMany`/`@OneToOne`/`@ManyToMany`/`@JoinColumn`/`@ElementCollection`)
- [ ] Không có `repository.*` hay HTTP client nằm trong `for`/`stream().map()`/`forEach`
- [ ] Endpoint trả list đã phân trang (`Page<T>` + `Pageable`)
- [ ] Method ghi đụng ≥2 bảng đã có `@Transactional`
- [ ] `@RequestBody` đã có `@Valid`
- [ ] Cột mới dùng để lọc đã có `@Index`
- [ ] Controller inject qua interface, không phải `*ServiceImpl`
- [ ] Đã bật SQL log và đếm query cho endpoint trả list
```

**Quy tắc:**
- PR luôn nhắm vào `develop`.
- PR nhỏ, tập trung. Trên khoảng 400 dòng thay đổi thì cân nhắc tách — trừ các đợt refactor nền tảng (Nhóm A) vốn buộc phải làm một lần.
- Tự review diff của mình trước khi mở PR: `git diff develop...HEAD`.
- Không merge PR đang đỏ build.

### Merge Strategy

Repo đang dùng **merge commit** (`Merge pull request #7 from Nitditto/docs/build-base`) — giữ nguyên, vì nó bảo toàn từng commit nhỏ trong nhánh feature (đúng với cách nhánh `feature/pricing-service` chia 5 commit theo bước).

- **Merge commit** — mặc định cho `feature/*`: giữ lại lịch sử từng bước.
- **Squash** — dùng cho nhánh lặt vặt nhiều commit rác kiểu `fix typo`, `fix again`, gộp thành một commit sạch.
- **KHÔNG force-push lên `develop`.** Force-push chỉ được phép trên nhánh feature cá nhân **chưa có ai khác cùng làm**, và dùng `--force-with-lease` chứ không phải `--force`.

**Cập nhật nhánh feature khi develop đã chạy trước:**

```bash
# Ưu tiên rebase khi nhánh CHƯA push hoặc chỉ mình bạn làm — giữ lịch sử thẳng
git fetch origin
git rebase origin/develop

# Nếu nhánh đã push và có người khác cùng làm thì merge, đừng rebase
git merge origin/develop
```

### Dọn nhánh sau khi merge

**Xoá nhánh ngay sau khi PR được merge**, cả local lẫn remote:

```bash
git branch -d feature/pricing-service
git push origin --delete feature/pricing-service
```

> ⚠️ **Đang tồn đọng:** 7 nhánh đã merge vào `develop` nhưng vẫn còn cả local lẫn `origin` — `docs/build-base`, `fix/flight-ops`, và 5 nhánh `feature/*-service`. Cần dọn:
> ```bash
> git branch --merged develop | grep -v develop | xargs -r git branch -d
> git remote prune origin
> ```
> Giữ lại `feat/seat-service` — nhánh này **chưa merge**, còn đang làm dở (và cần đổi tên theo mục Branch Naming ở trên).

### Lệnh Git hay dùng cho repo này

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

### Những điều TUYỆT ĐỐI không làm

- Commit trực tiếp lên `develop`.
- Force-push lên `develop`.
- Commit credential, mật khẩu DB thật, JWT secret. `application.yaml` hiện đang hardcode user/password MySQL — khi lên môi trường thật phải chuyển sang biến môi trường hoặc config server, và **không bao giờ** commit giá trị thật.
- Commit `target/`, `.idea/`, `.vscode/`, `*.iml` (đã có trong `.gitignore` — đừng `git add -f`).
- Commit code không compile được, hoặc "commit tạm để mai sửa" lên nhánh chung.
- Gộp refactor lớn chung với feature mới trong một PR — reviewer không tách được đâu là thay đổi hành vi.

## Reference

- Không có `docs/`, `README.md`, hay tài liệu thiết kế nào khác trong repo hiện tại — file này (`CLAUDE.md`) là nguồn quy tắc duy nhất cho tới khi có thêm tài liệu.
