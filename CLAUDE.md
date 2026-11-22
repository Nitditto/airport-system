# CLAUDE.md

File này chứa **rule và convention** cho Claude Code khi làm việc với repository này. Chỉ chứa quy tắc mang tính lâu dài — không ghi trạng thái tạm thời, không ghi "chỗ nào đang sai / cần sửa". Việc còn tồn đọng nằm ở [`docs/REFACTOR-BACKLOG.md`](docs/REFACTOR-BACKLOG.md). Giải thích chi tiết từng chủ đề nằm ở [`docs/`](docs/README.md) (xem [§19](#19-reference)).

---

## 1. Project Overview

**Airline Microservices** (`com.nitditto:airline-microservice`, repo `Nitditto/airport-system`) — hệ thống đặt vé & quản lý hàng không dạng multi-module Maven, gồm 9 business service tách theo domain, đứng sau một API Gateway và đăng ký qua Eureka.

## 2. Tech Stack

| Hạng mục | Công nghệ |
|---|---|
| Ngôn ngữ / framework | Java 17, Spring Boot 4.0.7, Spring Cloud 2025.1.0 |
| Persistence | MySQL 8.4 + Spring Data JPA (Hibernate 7, `ddl-auto: update` — chỉ dev) |
| Service Registry | Spring Cloud Netflix Eureka (`cloud/service-registry`, :8761) |
| API Gateway | Spring Cloud Gateway **Server MVC** (servlet, không phải WebFlux) (`cloud/api-gateway`, :5000) — verify JWT tập trung |
| Gọi đồng bộ giữa service | Spring Cloud OpenFeign (Apache HttpClient 5), resolve qua Eureka |
| Event bất đồng bộ | Apache Kafka 4.1 KRaft (`localhost:9092` từ host / `kafka:29092` trong Docker network) + `spring-kafka` |
| Auth | JWT tự viết — phát hành ở `user-service`, verify ở gateway |
| Container | Docker Compose + Google Jib (`jib-maven-plugin`) |
| Khác | Lombok, Maven multi-module reactor |

### Ngoài phạm vi kiến trúc hiện tại

Không giả định các thành phần sau tồn tại: **Config Server** (module đang comment trong `cloud/pom.xml`), Redis, Resilience4j/circuit breaker, Flyway/Liquibase, PostgreSQL, MapStruct, OpenAPI/Swagger, distributed tracing, CI/CD. Khi cần một trong số này, hỏi trước — đừng tự thêm dependency.

## 3. Repository & Module Structure

Reactor 3 cấp: `root pom` → (`common-lib`, `cloud`, `services`) → module con.

```
microservices/
├── pom.xml                     # Parent POM — version tập trung (java, spring-boot, spring-cloud, jib)
├── docker-compose.yml          # Full stack: MySQL + Kafka + Eureka + Gateway + 9 service (+ FE với --profile full)
├── docker-compose.dev.yml      # Chỉ hạ tầng (MySQL :3306, Kafka :9092) — chạy service từ IDE
├── docker/mysql/init.sql       # Tạo database cho từng service
├── docker/mysql/migrations/    # Script SQL chạy tay cho DB dev đã tồn tại (xem §17) — KHÔNG mount vào initdb
├── docker/mysql/seed/          # Dữ liệu mẫu chuẩn cho mọi service, chạy tay SAU khi service đã tạo schema (xem §17)
├── common-lib/                 # Shared jar — mọi service phụ thuộc
│   └── src/main/java/com/nitditto/
│       ├── entity/             # BaseEntity (@MappedSuperclass)
│       ├── embeddable/         # @Embeddable value objects (Address, GeoCode, SeatBenefits...)
│       ├── enums/              # Enum nghiệp vụ dùng chung (UserRole, FlightStatus, PaymentGateway...)
│       ├── exception/          # ResourceNotFoundException, BusinessException
│       └── payload/
│           ├── request/        # *Request DTO dùng chung
│           ├── response/       # *Response DTO + ApiResponse<T>
│           └── event/          # Integration event (Kafka) dùng bởi ≥ 2 service — xem §9
├── cloud/
│   ├── service-registry/       # Eureka Server
│   └── api-gateway/            # Routing + JWT filter + phân quyền theo role (RouteConfig)
└── services/
```

### Services & Port Mapping

| Module | Port | Domain chính | Database |
|---|---|---|---|
| `cloud/api-gateway` | 5000 | Routing, central JWT filter, role check | — |
| `cloud/service-registry` | 8761 | Eureka Server | — |
| `user-service` | 5001 | Auth (`/auth/**`), phát hành JWT, User | `airline_user` |
| `airline-core-service` | 5002 | Airline, Aircraft | `airline` |
| `flight-ops-service` | 5003 | Flight, FlightInstance, FlightSchedule | `airline_flight` |
| `location-service` | 5004 | Airport, City | `airline_location` |
| `pricing-service` | 5005 | Fare, FareRules, BaggagePolicy | `airline_pricing` |
| `seat-service` | 5006 | CabinClass, SeatMap, SeatInstance, seat hold | `airline_seat` |
| `ancillary-service` | 5008 | Meal, Ancillary, InsuranceCoverage | `airline_ancillary` |
| `booking-service` | 5009 | Booking, Ticket, Reservation | `airline_booking` |
| `payment-service` | 5010 | Payment, cổng VNPay & Razorpay | `airline_payment` |

Thêm service mới: lấy port kế tiếp chưa dùng, khai `spring.application.name` = tên module (đây là tên Eureka/Feign dùng), thêm route ở gateway, database ở `docker/mysql/init.sql`, block ở `docker-compose.yml`, và cập nhật bảng trên.

### Package layout chuẩn của một service (dưới `com.nitditto`)

```
controller/       # @RestController
service/          # interface — hợp đồng nghiệp vụ
service/impl/     # *ServiceImpl — @Service @RequiredArgsConstructor
repository/       # Spring Data JPA interface
repository/spec/  # *Specs — Specification factory (filter động ≥ 3 điều kiện)
model/            # @Entity
mapper/           # static mapper Entity <-> DTO (viết tay)
converter/        # AttributeConverter (thay @ElementCollection)
client/           # @FeignClient gọi service khác — xem §8
event/            # Spring ApplicationEvent nội bộ + Kafka producer/consumer — xem §9
exception/        # GlobalExceptionHandler (@RestControllerAdvice)
config/           # @Configuration (KafkaProducerConfig, ClockConfig...)
```

## 4. Build, Run & Docker

**Không có Maven Wrapper ở thư mục gốc** — dùng `mvn` cài trên máy. `mvnw`/`.mvn/` lẻ trong từng service là rác từ Spring Initializr — **đừng dùng**; luôn chạy từ thư mục gốc.

```bash
mvn clean install                                   # toàn reactor
mvn -pl services/pricing-service -am clean install  # một service (+ common-lib)
mvn -pl services/pricing-service spring-boot:run
mvn -pl services/pricing-service test -Dtest=ClassName#methodName
mvn clean package -DskipTests

# Docker
mvn package jib:dockerBuild -DskipTests    # build image nitditto/<artifactId>:<version> (KHÔNG dùng compile: image thiếu common-lib)
docker compose up -d                                # full stack (cần .env có JWT_SECRET, VNPAY_*)
docker compose -f docker-compose.dev.yml up -d      # chỉ MySQL + Kafka, service chạy từ IDE
```

- **Thứ tự khởi động khi chạy từ IDE:** hạ tầng (dev compose) → `service-registry` → các service → `api-gateway`.
- **Chỉ gateway (5000), Eureka (8761), MySQL, Kafka được publish ra host** trong compose. Client/frontend luôn gọi qua gateway, không gọi thẳng port service.
- Config phụ thuộc môi trường đọc qua biến môi trường có default dev (`${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}`). Secret (`JWT_SECRET`, `VNPAY_*`, `RAZORPAY_*`) **không có default** và nằm trong `.env` (không commit).
- **Quản lý version:** chỉ khai `<version>` ở root `pom.xml` (`dependencyManagement`/properties) hoặc để BOM Spring Boot/Spring Cloud quản lý. Service con **KHÔNG** ghim version riêng.

## 5. Architecture

### Luồng request end-to-end

```
Client ──► api-gateway :5000 ──(verify JWT, role check, set X-User-*)──► lb://<service> (Eureka)
                                                                             │
          Controller → Service (interface) → ServiceImpl → Repository → MySQL (DB riêng)
                                                │
                                                ├─► Mapper (Entity <-> DTO, static)
                                                ├─► FeignClient ──► service khác (đồng bộ, batch)
                                                └─► Kafka producer ──► topic (bất đồng bộ)
```

- Controller chỉ xử lý HTTP (nhận request, đọc header danh tính, gọi service, bọc `ApiResponse`).
- Business logic và check quyền sở hữu chỉ nằm ở `*ServiceImpl`.
- Mapper là hàm static thuần — không inject gì, không query.
- Repository chỉ chứa truy vấn.

---

## 6. NO JPA Relationship Annotations (BẮT BUỘC — kể cả trong cùng một service)

**Mọi khoá ngoại đều là cột `UUID` thô**, đặt tên `<entity>Id`, không exception.

**CẤM:** `@ManyToOne` · `@OneToMany` · `@OneToOne` · `@ManyToMany` · `@JoinColumn` · `@JoinTable` · `@ElementCollection` · `@MapsId` · mọi `CascadeType` và `orphanRemoval`.

```java
// ❌ SAI
@OneToOne(mappedBy = "fare", cascade = CascadeType.ALL, orphanRemoval = true)
private BaggagePolicy baggagePolicy;

// ✅ ĐÚNG — con giữ ID của cha, cha KHÔNG giữ tham chiếu tới con
@Entity
@Table(name = "baggage_policies")
public class BaggagePolicy extends BaseEntity {
    @Column(name = "fare_id", columnDefinition = "binary(16)", nullable = false, unique = true)
    private UUID fareId;
}
```

**Lý do:** không N+1 do lazy/eager loading; load dữ liệu liên quan tường minh; serialize không vòng lặp; ranh giới service không vỡ khi tách entity sang service khác.

### Ngoại lệ được phép: `@Embedded` / `@Embeddable`

`@Embedded` nhúng field vào chính bảng đó — không join, không query phụ. `SeatBenefits`, `BoardingBenefits`, `InFlightBenefits`, `FlexibilityBenefits`, `PremiumServiceBenefits`, `Address`, `GeoCode` là hợp lệ — **giữ nguyên khi refactor**.

### Thay thế `@ElementCollection` bằng `AttributeConverter`

```java
@Convert(converter = DayOfWeekSetConverter.class)
@Column(name = "operating_days", length = 32)
private Set<DayOfWeek> operatingDays = new HashSet<>();

@Converter
public class DayOfWeekSetConverter implements AttributeConverter<Set<DayOfWeek>, String> {
    @Override
    public String convertToDatabaseColumn(final Set<DayOfWeek> attribute) {
        return attribute == null ? "" : attribute.stream().map(Enum::name).collect(Collectors.joining(","));
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

Converter đặt trong `converter/` của service sở hữu entity (hoặc `common-lib/converter/` nếu ≥ 2 service dùng).

### Hệ quả 1 — Xoá cha-con thủ công trong một transaction

```java
@Override
@Transactional
public void deleteFareById(final UUID id) {
    if (!this.fareRepository.existsById(id)) {
        throw new ResourceNotFoundException("Fare", "id", id);
    }
    this.baggagePolicyRepository.deleteByFareId(id);   // con trước
    this.fareRuleRepository.deleteByFareId(id);
    this.fareRepository.deleteById(id);                // cha sau
}
```

Với ràng buộc từng dựa vào FK thật của MySQL, kiểm tra tường minh ở service và ném `BusinessException` trước khi xoá cha hoặc thêm con.

### Hệ quả 2 — Mapper nhận dữ liệu liên quan qua tham số

```java
// ✅ Service load sẵn rồi truyền vào; mapper vẫn static thuần
public static FareResponse toResponse(final Fare fare, final FareRules rules, final BaggagePolicy policy) { ... }

// > 3 tham số → gói thành record ngữ cảnh
public record FareContext(Fare fare, FareRules fareRules, BaggagePolicy baggagePolicy) {}
```

**Không inject repository/service/Feign client vào mapper.** Mapper cần query nghĩa là logic đặt sai chỗ.

Cách load dữ liệu liên quan (batch, không N+1) → [§7](#7-n1-prevention--batch-loading-quan-trọng).

---

## 7. N+1 Prevention & Batch Loading (QUAN TRỌNG)

Đây là nguồn rule **duy nhất** về N+1 trong file này. Có 2 loại:

| Loại | Nguyên nhân | Giải pháp |
|---|---|---|
| DB N+1 | `repository.*` trong vòng lặp | Một query `...In(ids)` cho mỗi loại con, ghép bằng `Map` |
| Network N+1 (tệ hơn nhiều) | Feign call trong vòng lặp | Một call tới endpoint `batch-by-ids` |

```java
// ❌ SAI — 1 + N query / N HTTP call
for (final Fare fare : fares) {
    final BaggagePolicy policy = baggagePolicyRepository.findByFareId(fare.getId());
    final FlightResponse flight = flightClient.getFlightById(fare.getFlightId());
}

// ✅ ĐÚNG — số query/call là hằng số
final List<UUID> fareIds = fares.stream().map(Fare::getId).toList();
final List<UUID> flightIds = fares.stream().map(Fare::getFlightId).distinct().toList();

final Map<UUID, BaggagePolicy> policyByFareId = this.baggagePolicyRepository.findByFareIdIn(fareIds)
        .stream().collect(Collectors.toMap(BaggagePolicy::getFareId, Function.identity()));
final Map<UUID, FlightResponse> flightById = this.flightClient.getFlightsByIds(flightIds).getData();

final List<FareResponse> responses = new ArrayList<>(fares.size());
for (final Fare fare : fares) {
    responses.add(FareMapper.toResponse(fare, policyByFareId.get(fare.getId()), flightById.get(fare.getFlightId())));
}
```

**Công thức 4 bước:** (1) query cha có phân trang → (2) trích `List<UUID>`, `.distinct()` → (3) một query `...In(ids)` / một batch call cho **mỗi loại** dữ liệu → `Map<UUID, T>` → (4) lặp cha, tra `Map.get()`.

**Endpoint batch bắt buộc:** mọi service expose dữ liệu cho service khác phải có

```java
@PostMapping("/api/<resources>/batch-by-ids")
ResponseEntity<ApiResponse<Map<UUID, XxxResponse>>> getByIds(@RequestBody @Valid @Size(max = 200) List<UUID> ids);
```

Gateway coi `POST .../batch-by-ids` là thao tác **đọc** (regex `BATCH_READ_PATH` trong `RouteConfig`) — giữ đúng path pattern này.

Dữ liệu tổng hợp (count/sum/min) → một aggregate query `group by` trả DTO projection (record), không tính từng dòng.

### Cấu hình bắt buộc (`application.yaml`)

```yaml
spring:
  jpa:
    open-in-view: false
    show-sql: true                 # DEV ONLY — để đếm query
    properties:
      hibernate:
        format_sql: true
        jdbc.batch_size: 50        # cho saveAll() batch thật
        order_inserts: true
        order_updates: true
logging:
  level:
    org.hibernate.SQL: DEBUG       # DEV ONLY
```

Bật SQL log, gọi endpoint trả list và **đếm số dòng SQL**: số query tăng theo số bản ghi = đang có N+1.

---

## 8. Inter-service Communication — OpenFeign (BẮT BUỘC)

### Quy tắc đặt & khai báo client

- Client đặt trong package **`com.nitditto.client`** của **service gọi** (không đặt trong `common-lib`). Tên `<TargetDomain>Client` (`FlightClient`, `SeatClient`).
- Khai bằng **tên Eureka**: `@FeignClient(name = "seat-service")`. **Cấm** `url = "http://localhost:..."` hay hardcode host/port.
- Application class có `@EnableFeignClients`.
- Kiểu trả về: `ApiResponse<T>` (khớp wrapper của service đích). Không dùng `ResponseEntity<...>` trong signature Feign; không khai `public` thừa trong interface.
- DTO dùng ở cả hai đầu lấy từ `common-lib/payload` — không copy DTO sang service gọi.
- Luôn đặt `@PathVariable("id")` / `@RequestParam("name")` có tên tường minh.

```java
@FeignClient(name = "flight-ops-service")
public interface FlightClient {

    @PostMapping("/api/flight-instances/batch-by-ids")
    ApiResponse<Map<UUID, FlightInstanceResponse>> getFlightInstancesByIds(@RequestBody List<UUID> ids);

    @GetMapping("/api/flights/{id}")
    ApiResponse<FlightResponse> getFlightById(@PathVariable("id") UUID id);
}
```

### Batching

- **Cấm gọi Feign trong `for` / `stream().map()` / `forEach`.** Đọc nhiều bản ghi → method batch (`List<UUID>` → `Map<UUID, T>`), `.distinct()` trước khi gọi. Chi tiết ở [§7](#7-n1-prevention--batch-loading-quan-trọng).
- Thiếu endpoint batch ở service đích → thêm endpoint batch ở đó, không vòng lặp gọi đơn lẻ.

### Truyền danh tính người dùng

Feign **không tự forward** header của request gốc. Khi service đích cần biết user (check sở hữu, hold ghế...), truyền tường minh: `@RequestHeader("X-User-Id") UUID userId`. Không thêm `RequestInterceptor` copy toàn bộ header.

### Error handling

Feign ném `FeignException` (subclass theo status: `FeignException.BadRequest`, `.NotFound`, ...) khi service đích trả 4xx/5xx. Quy tắc:

1. **Bọc lời gọi Feign ở tầng service**, không để `FeignException` lọt lên controller. Lỗi có ý nghĩa nghiệp vụ → dịch sang exception của mình:

   ```java
   try {
       this.seatClient.hold(userId, request);
   } catch (final FeignException.BadRequest ex) {
       throw new BusinessException("One or more selected seats are no longer available");
   } catch (final FeignException.NotFound ex) {
       throw new ResourceNotFoundException("SeatInstance", "ids", seatInstanceIds);
   }
   ```

2. Lời gọi **bù trừ/không quan trọng** (release ghế khi rollback, notify) → method hậu tố `*Quietly`, bắt `RuntimeException`, `log.warn` rồi bỏ qua — không làm hỏng luồng chính.
3. Mọi service có Feign client khai lưới an toàn `handleFeign(FeignException)` trong `GlobalExceptionHandler` (cùng một bản ở mọi service, mẫu: `flight-ops-service`), log đầy đủ ở server, trả message chung — **không** trả `ex.getMessage()` (lộ URL/body nội bộ):

   | Lỗi từ service đích | Trả cho client |
   |---|---|
   | `FeignException.NotFound` | 404 — id client gửi không tồn tại |
   | `RetryableException`, `status < 0`, 503 (không kết nối được / timeout / chưa có instance trên Eureka) | 503 — client thử lại được |
   | Còn lại (5xx, 401/403 nội bộ...) | 502 |
4. Không dùng `ErrorDecoder` toàn cục, không retry thủ công trong vòng lặp. Cần retry/circuit breaker → hỏi trước (xem §2).
5. Feign call **không nằm trong transaction DB đang ghi** nếu tránh được: gọi service khác trước (validate/lấy dữ liệu), rồi mới mở transaction ghi; hoặc dùng `@TransactionalEventListener(AFTER_COMMIT)` cho việc cần làm sau commit.
6. **Luồng ghi qua nhiều service** (mẫu: `BookingServiceImpl.createBooking`): method điều phối `@Transactional(propagation = NOT_SUPPORTED)` → đọc dữ liệu remote → giữ tài nguyên (hold ghế) → ghi DB trong **bean riêng** (`BookingCreationService`, `REQUIRES_NEW`) để chắc chắn đã commit → mới gọi side-effect ở service khác (tạo payment). Mỗi bước sau có bù trừ cho bước trước. Bắt lỗi bù trừ **ngoài** proxy, vì lỗi commit chỉ lộ ra sau khi method `@Transactional` trả về.
7. Dữ liệu tham chiếu hiển thị (hãng, máy bay, sân bay...) nạp qua một component loader dùng chung của service (mẫu: `flight-ops-service/service/integration/ReferenceDataLoader`) — gom id, distinct, chia lô 200, mỗi loại một lời gọi batch. Không tự viết lại vòng gọi ở từng service impl.

---

## 9. Events — Spring Events & Kafka

Project có **hai loại event**, không được lẫn:

| | Domain event nội bộ | Integration event (Kafka) |
|---|---|---|
| Phạm vi | Trong một service, cùng JVM | Giữa các service |
| Cơ chế | `ApplicationEventPublisher` + `@TransactionalEventListener` | `KafkaTemplate` + `@KafkaListener` |
| Đặt ở | `event/` của service | `common-lib/payload/event/` nếu ≥ 2 service dùng, ngược lại `event/` của producer |
| Ví dụ | `PaymentSucceededEvent`, `PaymentFailedEvent` (payment-service) | `PaymentTransactionSucceededEvent` → `payment.transaction.succeeded`, `FlightInstanceCreatedEvent` → `flight.instance.created` |

### Domain event nội bộ

- Dùng `@TransactionalEventListener(phase = AFTER_COMMIT)` khi listener gọi ra ngoài (Feign/Kafka) và bên kia sẽ đọc lại dữ liệu vừa ghi — tránh bên kia thấy trạng thái chưa commit.
- Listener tự bắt lỗi và log; không ném ngược làm hỏng luồng đã commit. Phải có cơ chế đối soát (reconcile) cho trường hợp listener thất bại.

### Integration event (Kafka)

**Format:** Java `record` (bất biến), có tối thiểu:

```java
public record PaymentTransactionSucceededEvent(
        UUID eventId,          // UUIDv7, sinh khi publish — dùng cho idempotency
        Instant occurredAt,
        UUID paymentId,        // ID aggregate — dùng làm message key
        UUID bookingId,        // correlation ID
        BigDecimal amount) {}
```

- Event chỉ chứa dữ liệu cần thiết + ID; không nhét entity, không nhét DTO response lớn.
- **Thay đổi schema tương thích ngược:** chỉ thêm field; không đổi tên/xoá/đổi kiểu field đã publish. Breaking → event version mới (`...V2`) / topic mới.

**Topic naming:** `<domain>.<entity>.<action>` — lowercase, dấu chấm, action thì quá khứ: `payment.transaction.succeeded`, `booking.reservation.expired`, `seat.hold.released`.

- Tên topic khai trong `application.yaml` (`kafka.topics.<name>`), đọc qua `@Value`/`@ConfigurationProperties` — không hardcode string trong code.
- **Message key = ID aggregate** (`paymentId`, `bookingId`) để giữ thứ tự theo aggregate trong partition.
- Bootstrap server đọc từ `spring.kafka.bootstrap-servers` (compose set `kafka:29092`, default dev `localhost:9092`).

**Producer:**

- Publish **sau khi commit** DB (`@TransactionalEventListener(AFTER_COMMIT)` → `kafkaTemplate.send(...)`), không publish giữa transaction (rollback DB nhưng event đã đi).
- Xử lý kết quả `send()` (callback/`whenComplete`), log lỗi kèm `eventId`.

**Consumer (BẮT BUỘC idempotent):**

- Không tin Kafka "exactly-once": cùng một event có thể tới nhiều lần. Mỗi consumer phải khử trùng lặp, theo một trong hai cách:
  - Bảng `processed_events(event_id binary(16) PK, processed_at)` — insert trong cùng transaction với thao tác nghiệp vụ; trùng PK = đã xử lý, bỏ qua.
  - Hoặc thao tác tự nhiên idempotent bằng check trạng thái (`if (booking.getStatus() == CONFIRMED) return;`).
- `groupId` = `spring.application.name` của consumer.
- Listener mỏng: deserialize → gọi method service (`@Transactional`) → không chứa business logic.
- Lỗi không phục hồi được (payload sai) → log + bỏ qua/dead-letter, không để retry vô hạn chặn partition.

---

## 10. Security & Identity Propagation (BẮT BUỘC)

```
Client ─(Authorization: Bearer <jwt>)─► api-gateway
    gateway: verify JWT → check role theo route → SET X-User-Id, X-User-Email, X-User-Roles
             (public read & /auth/**: XOÁ mọi X-User-* client tự gửi)
                                       ─► downstream service: tin header X-User-*
```

- **JWT chỉ verify ở gateway.** Service nghiệp vụ **không** có Spring Security, **không** viết `JwtAuthenticationFilter`, **không** parse token. Ngoại lệ duy nhất: `user-service` (phát hành token, endpoint `/auth/**`, dùng Spring Security nội bộ).
- Downstream lấy danh tính **qua header**, không qua `SecurityContext`:

  ```java
  @PostMapping
  public ResponseEntity<ApiResponse<AirlineResponse>> create(
          @RequestHeader("X-User-Id") final UUID userId,
          @RequestBody @Valid final AirlineRequest request) { ... }
  ```

- **Phân quyền chia hai tầng:**
  - **Role** (ai được gọi route/method nào) → chỉ ở gateway, `RouteConfig`. Thêm route/endpoint public mới = sửa `RouteConfig` (`PUBLIC_READS` là danh sách tường minh — thêm vào là quyết định bảo mật).
  - **Ownership** (owner A không sửa được dữ liệu của owner B) → ở `*ServiceImpl`, đối chiếu `userId` từ header với `ownerId`/`userId` của bản ghi; sai → `BusinessException`/403.
  - **Ngoại lệ có kiểm soát — admin ĐỌC:** `ROLE_SYSTEM_ADMIN` được bỏ qua kiểm tra sở hữu cho thao tác **đọc** (hỗ trợ/kiểm soát). Service đọc header `X-User-Roles` (gateway ghi đè từ JWT, client không giả được; có test) thay vì tự parse token. Thao tác **ghi/huỷ** vẫn chỉ chủ sở hữu, trừ khi có quyết định riêng. Ví dụ: `BookingReadAccess` (booking-service).
- Không bao giờ nhận danh tính từ body/query param (`userId` trong request body để quyết định quyền là lỗ hổng).
- Endpoint nội bộ (chỉ service khác gọi, vd `seat-instances/confirm-booked`, `free`) không được thêm vào nhóm route customer ở gateway.
- Service chỉ an toàn vì **không lộ port ra ngoài** — không publish port business service trong `docker-compose.yml`.
- Không log JWT, password, `hash-secret`, key cổng thanh toán.

---

## 11. Repository & Query Rules

### Phân trang cho MỌI endpoint trả list

```java
Page<FareResponse> getFares(final Pageable pageable) {
    return this.fareRepository.findAll(pageable).map(FareMapper::toResponse);
}
```

Không có ngoại lệ "lấy tất cả". Riêng `batch-by-ids` không phân trang nhưng chặn `@Size(max = 200)`.

### Các rule khác

- **Repository chỉ chứa truy vấn** — method name query, `@Query`, `Specification`. Không business logic, không mapping, không gọi service khác.
- **Filter động ≥ 3 điều kiện → `Specification`**, không dùng pattern `(:p is null or f.col = :p)` (chặn index). Lớp `*Specs` ở `repository/spec/`, `final class` + constructor private, mỗi factory trả `null` khi tham số null:

  ```java
  public static Specification<Flight> airlineId(final UUID airlineId) {
      return (root, query, cb) -> airlineId == null ? null : cb.equal(root.get("airlineId"), airlineId);
  }
  // service: Specification.where(FlightSpecs.airlineId(a)).and(FlightSpecs.departureAirportId(d))
  ```

- **Kiểm tra tồn tại → `existsBy*`**, không `findBy*().isPresent()` hay `countBy* > 0`. Luồng update dùng `existsBy...AndIdNot(..., id)`.
- **Ghi hàng loạt → `saveAll()`**, không `save()` trong vòng lặp. > 5.000 bản ghi: chia lô, `flush()`/`clear()` sau mỗi lô.
- **Chỉ cần vài cột → interface/record projection**, không load cả entity (nhất là entity có nhiều `@Embedded`).
- **Index:** `ddl-auto: update` chỉ tạo index cho PK và unique. Thêm method lọc theo cột nào → khai `@Index` cho cột đó trong `@Table(indexes = ...)`. Index tổ hợp: cột lọc `=` đứng trước cột lọc khoảng.

### `@Transactional`

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)   // mặc định cả class
public class FareServiceImpl implements FareService {
    @Override
    @Transactional                // ghi đè cho method ghi
    public FareResponse create(final FareRequest request) { ... }
}
```

- Bắt buộc `@Transactional` cho method ghi đụng ≥ 2 bảng (đặc biệt xoá cha-con, §6).
- Gọi `this.method()` trong cùng class bỏ qua proxy → không có transaction. Cần thì tách bean.
- Không gọi Feign/Kafka bên trong transaction ghi nếu tránh được (§8, §9).

---

## 12. Entity Design & common-lib

### BaseEntity + UUIDv7 (BẮT BUỘC)

Mọi entity `extends BaseEntity` (`common-lib/entity/`, `@MappedSuperclass`) — đây là nơi **duy nhất** khai `id`, `createdAt`, `updatedAt`:

```java
@Id
@UuidGenerator(algorithm = UuidVersion7Strategy.class)   // org.hibernate.id.uuid, có sẵn trong Hibernate 7
@JdbcTypeCode(SqlTypes.BINARY)
@Column(name = "id", columnDefinition = "binary(16)", updatable = false, nullable = false)
private UUID id;

@CreationTimestamp @Column(name = "created_at", updatable = false, nullable = false) private Instant createdAt;
@UpdateTimestamp   @Column(name = "updated_at")                                     private Instant updatedAt;
```

- **CẤM `@GeneratedValue(strategy = GenerationType.UUID)`** — sinh UUIDv4 ngẫu nhiên, phân mảnh index (lỗi âm thầm).
- UUID sinh ở tầng ứng dụng → Hibernate biết ID trước khi insert → `saveAll()` batch được.
- Lưu `binary(16)`, không `char(36)`. Query tay trong MySQL: `BIN_TO_UUID(id)` / `UUID_TO_BIN('...')`.
- **Mọi FK đều `UUID` + `columnDefinition = "binary(16)"`.**
- `ddl-auto: update` **không đổi được kiểu/độ dài cột đã có** → đổi = drop & tạo lại DB dev, hoặc viết script thủ công nếu phải giữ dữ liệu (§17).
- **Không** dùng `@Data`/`@EqualsAndHashCode` trên entity. Không override `equals/hashCode` trừ khi thật cần.

### Naming

- Bảng: số nhiều, snake_case (`fares`, `flight_instances`). Cột: snake_case, luôn khai `@Column(name = ...)` tường minh.
- Lombok trên entity: `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`.

### common-lib — được đặt gì

| Đặt trong `common-lib` | Không đặt |
|---|---|
| Request/Response DTO mà ≥ 2 service dùng (`payload/request|response`) | `@Entity` (trừ `BaseEntity` là `@MappedSuperclass`) |
| Enum nghiệp vụ dùng chung (`enums/`) | Service, Repository, business logic |
| `@Embeddable` dùng chung (`embeddable/`) | `@FeignClient` (thuộc service gọi) |
| `ResourceNotFoundException`, `BusinessException` | `GlobalExceptionHandler` (thuộc từng service) |
| Integration event Kafka dùng ≥ 2 service (`payload/event/`) | Spring config, bean |

Chỉ 1 service dùng → giữ trong service đó.

---

## 13. Coding Standards

### SOLID & DI

- **S** — Controller chỉ HTTP; Service một domain; tách khi class vượt ~200 dòng.
- **O** — mở rộng qua interface/Strategy (vd. `service/gateway/VNPayService`, `RazorpayService`), không nhét thêm nhánh `if`.
- **L** — `*ServiceImpl` không override để ném `UnsupportedOperationException`.
- **I** — interface nhỏ theo domain.
- **D** — **BẮT BUỘC** inject qua interface, không inject `*ServiceImpl`. Constructor injection (`@RequiredArgsConstructor` + `private final`), không `@Autowired` field.
- Thời gian hiện tại lấy từ bean `Clock` (`ClockConfig`) để test được, không gọi `Instant.now()` trực tiếp trong logic có hạn (hold ghế, hết hạn booking).

### Naming

- Package lowercase; Class PascalCase; interface không prefix `I`, impl hậu tố `Impl`.
- Method camelCase bắt đầu bằng động từ; boolean `is*/has*/can*`. Biến có nghĩa, collection số nhiều. Hằng `SCREAMING_SNAKE_CASE`.
- DTO: `*Request` (dùng chung create & update), `*Response`.

### Clean code

- Method ~20 dòng, ≤ 3 tham số (nhiều hơn → gói object). Một method một việc.
- Comment WHY, không comment WHAT. Ngắn gọn: 1–3 dòng, ngắt dòng ≤ 120 ký tự.
- Javadoc không quá 5 dòng, trừ khi mô tả thuật toán toán học/tài chính phức tạp (tính giá, thuế, hoàn tiền).
- Không catch `Exception` chung để nuốt lỗi ở service; ném exception cụ thể. Không return `null` cho tìm theo id.
- Parameter/local không đổi: `final`. Luôn `this.` khi truy cập field instance.
- Hot path (mapping list lớn): ưu tiên `for`; stream OK cho logic đơn giản.

---

## 14. Exception Handling

- `ResourceNotFoundException(resource, field, value)` và `BusinessException(message)` ở `common-lib/exception`.
- Mỗi service có `exception/GlobalExceptionHandler` (`@Slf4j @RestControllerAdvice`) map tối thiểu:

  | Exception | Status |
  |---|---|
  | `ResourceNotFoundException` | 404 |
  | `BusinessException`, `MethodArgumentNotValidException` | 400 (gom field error thành message) |
  | `FeignException` (service có Feign) | 502, message chung |
  | `Exception` (catch-all) | 500, `log.error` đầy đủ, message `"Internal server error"` |

- Service **không** `throw new Exception(...)`; controller **không** khai `throws Exception`.
- Không bao giờ trả stack trace hay message nội bộ của exception hạ tầng ra client.

## 15. API Response Wrapper

`ApiResponse<T>` (`common-lib/payload/response`) với `success`, `message`, `data`; factory `ApiResponse.success(data)` / `ApiResponse.error(message)`.

- **Mọi** endpoint có trả dữ liệu đều bọc `ResponseEntity<ApiResponse<T>>` — đồng bộ toàn project (Feign client phía gọi phụ thuộc vào điều này).
- `DELETE` trả `204 No Content`, không body.

## 16. Validation & Logging

- Request DTO dùng `jakarta.validation.constraints`. **Mọi `@RequestBody` phải có `@Valid`.**
- `@Slf4j` ở service. `info` cho thao tác ghi (create/update/delete, đổi trạng thái booking/payment); `warn`/`error` khi bắt lỗi. Log kèm ID (`bookingId=`, `paymentId=`, `eventId=`) để truy vết qua service.
- **Không** log password, JWT, secret, dữ liệu thẻ/cổng thanh toán.

## 17. Database

- **Database-per-service** (bảng ở [§3](#services--port-mapping)). Service **không** đọc DB của service khác — chỉ qua Feign/Kafka.
- DB mới phải khai trong `docker/mysql/init.sql`.
- `ddl-auto: update` chỉ dev. Lên production → Flyway/Liquibase (hỏi trước).
- **Không** commit credential thật, JWT secret, key cổng thanh toán — dùng biến môi trường + `.env`.

### Dữ liệu mẫu (`docker/mysql/seed/`)

`001-sample-data.sql` nạp user, địa điểm, hãng/máy bay, sơ đồ ghế, chuyến bay 30 ngày tới (kèm cabin + seat instance), fare, suất ăn, dịch vụ thêm, bảo hiểm. **Chạy tay sau khi mọi service đã khởi động** (schema do `ddl-auto` tạo), trên DB trống; không mount vào `initdb`; không chạy trên DB có dữ liệu thật. Idempotent (id cố định tiền tố `5eed` hoặc dẫn xuất bằng `MD5`). Cabin/seat instance được sinh bằng SQL thay cho sự kiện Kafka `flight.instance.created`, nên đổi quy tắc sinh ở `FlightInstanceCabinServiceImpl` hoặc tên cột entity thì **phải sửa seed**. Chi tiết, tài khoản mẫu và cách chạy: [`docker/mysql/seed/README.md`](docker/mysql/seed/README.md).

### Migration thủ công (`docker/mysql/migrations/`)

`ddl-auto: update` chỉ thêm bảng/cột/index mới; nó **không** đổi kiểu hay độ dài cột đã có và không sửa dữ liệu cũ. Khi một thay đổi entity cần điều đó **và** phải giữ dữ liệu của DB dev đang có, viết script SQL thủ công thay vì drop DB. DB dev không có dữ liệu cần giữ thì vẫn mặc định drop & tạo lại.

- **Vị trí/tên:** `docker/mysql/migrations/NNN-<mô-tả-kebab>.sql`, `NNN` tăng dần từ `001`. Hiện có `001-fare-current-price.sql`, `002-flight-schedule-operating-days.sql`.
- **Chạy tay.** Compose chỉ mount `init.sql` vào `/docker-entrypoint-initdb.d`; không công cụ nào tự chạy thư mục này. Đây là giải pháp tạm cho dev, **không thay** Flyway/Liquibase ở production (hỏi trước).
- **Header comment bắt buộc:** đổi gì và vì sao (nhánh/PR gây ra), chạy trên database nào, DB tạo mới (kể cả sau `docker compose down -v`) có cần chạy không, có idempotent không.
- **Đúng đích:** `USE <database>;` phải khớp `spring.datasource.url` của service đó. Tên bảng/cột phải là cột Hibernate thực sinh ra (field `taxedAndFees` → cột `taxed_and_fees`); kiểm tra bằng `SHOW CREATE TABLE` thay vì đoán.
- **Idempotent** (chạy lại không hỏng, vd. `MODIFY COLUMN` về cùng độ dài).
- **Script sửa dữ liệu:** (1) `SELECT` xem trước đúng các dòng sẽ bị ảnh hưởng; (2) `UPDATE` để **comment**, bọc `START TRANSACTION … COMMIT`, chỉ sửa dòng khớp điều kiện — không `UPDATE` cả bảng; (3) nhắc backup bảng trước khi chạy. ID là `binary(16)`: đọc bằng `BIN_TO_UUID(id)`.
- **Commit:** đi cùng PR với thay đổi entity gây ra nó. Nếu commit riêng dùng `chore(docker)`.

---

## 18. Git Convention (BẮT BUỘC)

### Branch

- Base branch: **`develop`** (chưa có `main`). Mọi PR nhắm vào `develop`. **Không commit/force-push trực tiếp lên `develop`.**
- Tên nhánh `<type>/<mô-tả-kebab-case>` — lowercase, không dấu, không space. Prefix: `feature/`, `fix/`, `refactor/`, `docs/`, `chore/`, `test/`. (`feature/` là prefix nhánh; `feat` là type commit.)
- Tách từ `develop` mới nhất; một nhánh = một mục đích. Force-push chỉ trên nhánh cá nhân, dùng `--force-with-lease`.

### Commit Message — Conventional Commits

```
<type>(<scope>): <subject>
```

| Type | Ý nghĩa |
|---|---|
| `feat` | Tính năng mới cho người dùng/API |
| `fix` | Sửa bug |
| `refactor` | Đổi code, không đổi hành vi |
| `perf` | Tối ưu hiệu năng (index, gỡ N+1, batch) |
| `docs` | Chỉ tài liệu |
| `test` | Thêm/sửa test |
| `chore` | Config, việc vặt không đụng logic |
| `build` | Maven POM, version, reactor, Jib |
| `ci` | Pipeline CI/CD |
| `style` | Format, import — không đổi logic |

| Scope | Phạm vi |
|---|---|
| `common` | `common-lib/` |
| `gateway` | `cloud/api-gateway/` |
| `registry` | `cloud/service-registry/` |
| `cloud` | `cloud/pom.xml`, thay đổi chung cả hai module cloud |
| `user` | `services/user-service/` |
| `airline-core` | `services/airline-core-service/` |
| `flight-ops` | `services/flight-ops-service/` |
| `location` | `services/location-service/` |
| `pricing` | `services/pricing-service/` |
| `seat` | `services/seat-service/` |
| `ancillary` | `services/ancillary-service/` |
| `booking` | `services/booking-service/` |
| `payment` | `services/payment-service/` |
| `docker` | `docker-compose*.yml`, `docker/`, cấu hình Jib image |
| `kafka` | Hạ tầng/cấu hình Kafka dùng chung (topic, broker). Producer/consumer của một service → dùng scope service đó |
| `build` | Root `pom.xml`, `services/pom.xml`, cấu trúc reactor |

- Đụng **một** scope → bắt buộc ghi scope. Đụng **nhiều** → bỏ scope. Không bịa scope ngoài bảng.
- Subject: **tiếng Anh**, imperative (`add`, `fix`, không `added`), chữ đầu thường, không dấu chấm cuối, ≤ 72 ký tự.
- Một commit = một thay đổi logic hoàn chỉnh, build được.

```
✅ feat(booking): add seat hold before payment
✅ perf(pricing): add composite index on fares(flight_id, cabin_class_id)
✅ refactor: migrate all entity ids to uuidv7
❌ feat(pricing): Implemented Fares.      # hoa đầu, quá khứ, dấu chấm
❌ update code / fix bug                   # thiếu type, không rõ nghĩa
❌ feat(pricing): add fare + fix port      # gộp nhiều việc
```

### Quy tắc riêng cho Claude khi commit / mở PR

- Commit message **đúng một dòng** theo format trên. **TUYỆT ĐỐI KHÔNG** `Co-Authored-By`, không footer, không "Generated with Claude Code", không bọc code block. Áp dụng cả cho mô tả PR.
- Tạo nhánh `<type>/<mô-tả>` trước khi commit; không commit lên `develop`; chỉ commit/push khi được yêu cầu.

### Pull Request

- Title viết như một commit message conventional (không để tên nhánh tự sinh).
- Description: **Mục đích** · **Thay đổi chính** · **Cách kiểm thử** (lệnh + endpoint đã gọi qua gateway) · **Breaking change** (API/schema/event; có phải drop DB không).
- PR nhỏ, tập trung (~400 dòng là ngưỡng cân nhắc tách). Không merge khi build đỏ. Merge commit cho `feature/*`, squash cho nhánh nhiều commit rác. Xoá nhánh sau merge.

**PR Checklist:**

- [ ] Không có annotation quan hệ JPA mới (§6)
- [ ] Không có `repository.*` / Feign call trong vòng lặp; dữ liệu liên quan load bằng batch (§7)
- [ ] Feign client khai bằng tên Eureka, `FeignException` được bọc ở service (§8)
- [ ] Kafka consumer idempotent; event là `record` có `eventId`; topic đúng `<domain>.<entity>.<action>` (§9)
- [ ] Không thêm Spring Security/JWT filter vào service nghiệp vụ; check ownership bằng `X-User-Id` (§10)
- [ ] Endpoint/route mới đã được khai báo trong `RouteConfig` của gateway với role đúng
- [ ] Endpoint trả list có phân trang; `@RequestBody` có `@Valid`
- [ ] Method ghi đụng ≥ 2 bảng có `@Transactional`; cột lọc mới có `@Index`
- [ ] Inject qua interface, không `*ServiceImpl`
- [ ] Không commit secret, `.env`, `target/`, `.idea/`, `.vscode/`, `*.iml`

### Những điều TUYỆT ĐỐI không làm

- Commit hoặc force-push trực tiếp lên `develop`.
- Commit credential, JWT secret, key VNPay/Razorpay, file `.env`.
- Commit `target/`, `.idea/`, `.vscode/`, `*.iml` (đừng `git add -f`).
- Commit code không compile, hoặc "commit tạm" lên nhánh chung.
- Gộp refactor lớn chung với feature mới trong một PR.

---

## 19. Reference

- **Backlog / bug đã biết / nợ kỹ thuật** → [`docs/REFACTOR-BACKLOG.md`](docs/REFACTOR-BACKLOG.md). `CLAUDE.md` chỉ chứa rule.
- **Tổng quan & mục lục tài liệu** → [`docs/README.md`](docs/README.md), lộ trình → [`docs/ROADMAP.md`](docs/ROADMAP.md).
- **Hướng dẫn phát triển chi tiết** (ví dụ dài, giải thích) → [`docs/development/`](docs/development/).
- **Tài liệu từng tính năng hạ tầng** (gateway routing, Eureka, OpenFeign, communication patterns, Docker, payment, booking) → [`docs/features/`](docs/features/).
- Khi `docs/` mâu thuẫn với file này, **`CLAUDE.md` là nguồn đúng**.
