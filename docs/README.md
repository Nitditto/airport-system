# Airline Microservices

Hệ thống quản lý hàng không dạng multi-module Maven (`com.nitditto:airline-microservice`,
repo `Nitditto/airport-system`), tách theo domain thành các microservice độc lập:
airline core, flight operations, location, pricing, seat, ancillary và user/auth.

Mỗi service có **database riêng** (database-per-service), giao tiếp với nhau bằng
tham chiếu `UUID` thô + endpoint batch-fetch — chưa có service discovery, config server,
API gateway hay message broker.

## Tech stack

- Java 17 + Spring Boot 4.0.7 + Spring Cloud 2025.1.0
- MySQL + Spring Data JPA (Hibernate, `ddl-auto: update`)
- Khóa chính UUIDv7 sinh ở tầng ứng dụng (`BaseEntity` trong `common-lib`)
- Spring Security + JWT tự viết — chỉ ở `user-service`
- Lombok, Maven multi-module reactor build

## Các service

Reactor 3 cấp: `root pom` → (`common-lib`, `cloud`, `services`) → `services/pom.xml` → 7 service.

| Service | Port | Domain (entity chính) |
|---|---|---|
| `user-service` | 5001 | `User` — đăng ký/đăng nhập, JWT, Spring Security |
| `airline-core-service` | 5002 | `Airline`, `Aircraft` |
| `flight-ops-service` | 5003 | `Flight`, `FlightInstance`, `FlightSchedule` |
| `location-service` | 5004 | `Airport`, `City` |
| `pricing-service` | 5005 | `Fare`, `FareRules`, `BaggagePolicy` |
| `seat-service` | 5006 | `CabinClass`, `SeatMap`, `Seat`, `SeatInstance`, `FlightInstanceCabin` |
| `ancillary-service` | 5008 | `Ancillary`, `InsuranceCoverage`, `Meal`, `FlightMeal`, `FlightCabinAncillary` |

`common-lib` là jar dùng chung: DTO (`payload/request`, `payload/response`), enum nghiệp vụ,
`@Embeddable` value object, `BaseEntity`, exception dùng chung. **Không** chứa `@Entity`,
business logic hay Service/Repository.

`cloud/` hiện là aggregator rỗng — service-registry / config-server / api-gateway mới là
comment, chưa implement.

## Cấu trúc một service

```
com.nitditto
├── controller/    @RestController
├── service/       interface nghiệp vụ
│   └── impl/       *ServiceImpl
├── repository/    Spring Data JPA interface
├── model/         @Entity (extends BaseEntity)
├── mapper/        static mapper Entity <-> DTO (viết tay)
├── exception/     GlobalExceptionHandler riêng (@RestControllerAdvice)
└── config/        (hiện chỉ user-service: SecurityConfig, Jwt*)
```

Luồng xử lý: `Request → Controller → Service → ServiceImpl → Repository → MySQL`,
mapper lo phần Entity ↔ DTO.

## Build & run

Không có Maven Wrapper ở thư mục gốc — dùng `mvn` cài trên máy, luôn chạy từ thư mục gốc.

```bash
# Build toàn bộ reactor
mvn clean install

# Build / run một service
mvn -pl services/pricing-service -am clean install
mvn -pl services/pricing-service spring-boot:run

# Test
mvn test
mvn -pl services/pricing-service test -Dtest=ClassName#methodName

# Package bỏ qua test
mvn clean package -DskipTests
```

## Tài liệu

| Bạn đang định làm gì | Đọc file nào |
|---|---|
| Hiểu luật của dự án (nguồn quy tắc gốc) | [`../CLAUDE.md`](../CLAUDE.md) |
| Biết dự án đi tới đâu, còn những gì | [`ROADMAP.md`](ROADMAP.md) |
| Việc refactor còn tồn đọng | [`REFACTOR-BACKLOG.md`](REFACTOR-BACKLOG.md) |
| **Bắt đầu code một service mới** | [`development/02-new-service-checklist.md`](development/02-new-service-checklist.md) |
| Viết entity / repository / service / controller | `development/03` → `development/06` |
| Trước khi mở Pull Request | [`development/08-definition-of-done.md`](development/08-definition-of-done.md) |

### `development/`

| File | Nội dung |
|---|---|
| [01-golden-rules.md](development/01-golden-rules.md) | 12 quy tắc vàng — đọc 5 phút, nhớ cả đời |
| [02-new-service-checklist.md](development/02-new-service-checklist.md) | Dựng service mới từ 0 tới chạy được (case thật: `seat-service`) |
| [03-entity-and-persistence.md](development/03-entity-and-persistence.md) | Entity, `BaseEntity`, UUIDv7, index, converter — ĐÚNG/SAI |
| [04-repository-and-queries.md](development/04-repository-and-queries.md) | Repository, phân trang, N+1, projection, Specification — ĐÚNG/SAI |
| [05-service-layer.md](development/05-service-layer.md) | Service/Impl, transaction, exception, logging, mapper — ĐÚNG/SAI |
| [06-api-layer.md](development/06-api-layer.md) | Controller, `ApiResponse<T>`, `@Valid`, HTTP status — ĐÚNG/SAI |
| [07-security.md](development/07-security.md) | JWT, phân quyền, không tin dữ liệu client gửi — ĐÚNG/SAI |
| [08-definition-of-done.md](development/08-definition-of-done.md) | Checklist tự soát trước khi mở PR |
| [09-bug-co-that-trong-repo.md](development/09-bug-co-that-trong-repo.md) | Bảo tàng bug thật đã gặp — học từ lỗi của chính dự án |

### `features/`

Tài liệu theo từng mảng kiến trúc/tính năng — mỗi file có phần **5W1H** (What/Why/Who/When/Where/How)
để trả lời "cái này sinh ra để làm gì" trước khi đi vào cách cài đặt.

| File | Trạng thái | Nội dung |
|---|---|---|
| [spring-cloud-service-discovery.md](features/spring-cloud-service-discovery.md) | 🟢 Đã có | Vì sao cần Eureka, cơ chế đăng ký/tra cứu/load balancing, Eureka vs Consul |
| [eureka-service-registry.md](features/eureka-service-registry.md) | 🟢 Đã có | Hướng dẫn cài đặt Eureka Server/Client từng bước |
| [openfeign-client.md](features/openfeign-client.md) | 🟡 Có hạ tầng, chưa dùng thật | `@FeignClient`, timeout/retry/logging/interceptor, nợ kỹ thuật cần dọn |
| [docker-compose.md](features/docker-compose.md) | 🟡 Đã có, chưa chạy thử đầy đủ | Build image bằng Jib, chạy full stack bằng Docker Compose, env ghi đè, xử lý sự cố |
| [inter-service-communication.md](features/inter-service-communication.md) | 🟢 Đã có | `RestTemplate` + Eureka giữa `flight-ops-service` ↔ `airline-core-service`, chống N+1 qua HTTP |
| [resilience-fault-tolerance.md](features/resilience-fault-tolerance.md) | ⬜ Chưa làm | Circuit Breaker/Retry/Fallback/Bulkhead với Resilience4j |
| [api-gateway-routing.md](features/api-gateway-routing.md) | ⬜ Chưa làm | Spring Cloud Gateway, routing theo tên service, JWT tập trung, rate limiting |
| [communication-patterns.md](features/communication-patterns.md) | 🟢/⬜ Một phần | Khi nào chọn Sync (REST/Feign) vs Async (Kafka), áp vào luồng Booking/Payment |
| [observability.md](features/observability.md) | ⬜ Chưa làm | Correlation ID/Tracing (Zipkin), structured logging, metrics (Prometheus/Grafana) |
| [booking-service.md](features/booking-service.md) | ⬜ Thiết kế | Domain Booking — service điều phối trung tâm |
| [payment-service.md](features/payment-service.md) | 🟢 Đã có | Domain Payment — tích hợp Razorpay |
