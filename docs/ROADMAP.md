# Airline Microservices — Roadmap

Roadmap đầy đủ cho dự án `airline-microservice`, viết lại/bổ sung từ bản nháp ban đầu (chỉ có Section 9-22). Đã thêm **Section 1-8** (nền tảng — khớp với 5 service đã dựng trong repo hiện tại) và **Section 23-26** (những mảng còn thiếu để dự án đi tới production, bám theo "Known Gaps" trong [CLAUDE.md](../CLAUDE.md)).

Chú thích trạng thái:
- ✅ Đã có trong repo hiện tại
- 🚧 Đã có nhưng chưa hoàn chỉnh / cần refactor
- ⬜ Chưa bắt đầu

---

## Section 1: Tổng quan dự án & Kiến trúc Microservices ✅

1. Giới thiệu bài toán nghiệp vụ hàng không & phạm vi hệ thống (đặt vé, giá vé, hành lý, lịch bay...)
2. So sánh Monolith vs Microservices — lý do tách theo domain (user, airline-core, location, flight-ops, pricing...)
3. Nguyên tắc Database-per-service & giới hạn giao tiếp giữa các service (xem `CLAUDE.md` mục "Cross-Service Communication")
4. Thiết lập Multi-module Maven Reactor: root `pom.xml` → `common-lib` / `cloud` / `services` → 5 service module
5. Quy ước package layout dùng chung: `controller/ service/ service/impl/ repository/ model/ mapper/ config/`

## Section 2: Common Library — Nền tảng dùng chung 🚧

1. Enum nghiệp vụ dùng chung: `AircraftStatus`, `AirlineStatus`, `CabinClassType`, `FlightStatus`, `UserRole` ✅
2. `@Embeddable` value object dùng chung: `Address`, `GeoCode`, `SeatBenefits`, `BoardingBenefits`... ✅
3. Request/Response DTO tập trung cho mọi service (`payload/request`, `payload/response`) ✅
4. **BaseEntity chuẩn hoá** (`id` + `createdAt` + `updatedAt`, thống nhất `GenerationType.IDENTITY`) — ⬜ chưa làm, hiện mỗi entity tự khai riêng lẻ và không đồng nhất strategy
5. **Exception Handling Framework** dùng chung: `ResourceNotFoundException`, `BusinessException` — ⬜ chưa có, toàn repo đang `throw new Exception(...)`
6. **`ApiResponse<T>` generic wrapper** — ⬜ hiện `ApiResponse` chỉ có field `message`, chỉ dùng ở `HomeController`

## Section 3: User & Authentication Service (port 5001) 🚧

1. Thiết kế schema `User` & vai trò (`UserRole`) ✅
2. Khởi tạo project, cấu hình datasource riêng cho user-service — ⬜ hiện đang trỏ nhầm sang DB `airline_location` (copy-paste từ location-service), cần tách DB riêng (`airline_user`)
3. Xây dựng Auth API: `AuthController`/`AuthService` (register/login) ✅
4. `JwtProvider` & `JwtConstant` — sinh & validate token ✅
5. Cấu hình `SecurityConfig` — ⬜ hiện đang `permitAll()` toàn bộ request, JWT sinh ra nhưng chưa thực sự enforce ở filter chain; cần bật authorization theo route (public: `/auth/**`, còn lại yêu cầu token + kiểm tra role)
6. Kiểm thử luồng đăng ký/đăng nhập & phân quyền theo `UserRole`

## Section 4: Airline Core Service (port 5002) ✅

1. Thiết kế schema `Aircraft` & `Airline`
2. Xây dựng Aircraft API (CRUD)
3. Xây dựng Airline API (CRUD) & `AirlineDropDownItem` cho các dropdown UI
4. Cung cấp endpoint `POST /batch-by-ids` để service khác (flight-ops, pricing) tham chiếu theo `airlineId`/`aircraftId`

## Section 5: Location Service (port 5004) ✅

1. Thiết kế schema `Airport` & `City`
2. Xây dựng City API (CRUD)
3. Xây dựng Airport API — liên kết `City` qua `cityId` (JPA relationship, cùng service nên OK theo rule "Relationship trong cùng service")
4. Cung cấp endpoint `POST /batch-by-ids` cho Airport/City

## Section 6: Flight Operations Service (port 5005 — ⚠️ trùng port với pricing-service) 🚧

1. Thiết kế schema `Flight`, `FlightInstance`, `FlightSchedule`
2. Xây dựng Flight API — tham chiếu `airlineId` bằng `Long` (không JPA relationship xuyên service)
3. Xây dựng FlightInstance API (chuyến bay cụ thể theo ngày/giờ thực tế)
4. Xây dựng FlightSchedule API (lịch bay lặp lại theo tuần)
5. Kiểm thử luồng tạo lịch bay → sinh flight instance
6. **Fix port collision**: đổi port sang giá trị chưa dùng (đề xuất giữ `flight-ops-service` ở `5005`, đổi `pricing-service` sang `5003`)

## Section 7: Pricing Service (port 5005 → cần đổi, đề xuất 5003) ✅ (mới hoàn thành theo git log)

1. Thiết kế schema `Fare`, `FareRules`, `BaggagePolicy`
2. Xây dựng Fare API — quan hệ `@OneToOne` nội bộ với `FareRules`/`BaggagePolicy` (cascade + orphanRemoval)
3. Xây dựng FareRules API (điều kiện đổi/hoàn vé)
4. Xây dựng BaggagePolicy API (hạn mức hành lý theo hạng vé)
5. Fare tham chiếu `flightId`, `cabinClassId` bằng `Long` — cung cấp `POST /api/fares/batch-by-ids` cho Booking Service dùng sau này (Section 11)

## Section 8: Rà soát & Chuẩn hoá 5 service đã có trước khi mở rộng ⬜

*(Bước bắt buộc trước khi bắt đầu Section 9 — tránh nhân bản các gap kỹ thuật sang service mới)*

1. Áp dụng `BaseEntity` + `GlobalExceptionHandler` + `ApiResponse<T>` đồng bộ cho cả 5 service hiện có
2. Rà soát toàn bộ controller, bổ sung `@Valid` còn thiếu (vd. `FareController.updateFare` đang thiếu)
3. Thêm `@Slf4j` logging cho tầng Service (`info` khi create/update/delete, `warn`/`error` khi bắt exception nghiệp vụ)
4. Fix bug đã liệt kê: port collision, sai datasource user-service, `permitAll()` ở SecurityConfig
5. Viết Unit test tối thiểu cho Service layer (JUnit + Mockito) — hiện repo **chưa có bất kỳ test nào**
6. Thống nhất `GenerationType.IDENTITY` cho toàn bộ entity (đang lẫn `AUTO`/`IDENTITY` giữa các service)

---

## Section 9: Seat Inventory & Seat Map Service ⬜

1. Seat Service Architecture & Schema Design (CabinClass, SeatMap, Seat, FlightInstanceCabin, SeatInstance)
2. Seat Service Project Setup
3. Developing the Cabin Class API (dùng `CabinClassType` enum có sẵn trong common-lib)
4. Building the Interactive Seat Map API
5. Core Seat Management API
6. Developing the Flight Instance Cabin API (số ghế còn trống theo từng chuyến bay cụ thể)
7. Building the Seat Instance Management API (trạng thái từng ghế: available/held/booked)
8. Cung cấp `POST /batch-by-ids` cho Booking Service kiểm tra ghế trống
9. Validating Seat Service Functionality (test giữ ghế song song — race condition khi 2 người chọn cùng ghế)

## Section 10: Ancillary Services & In-Flight Management ⬜

1. Ancillary Service Architecture Design (Ancillary, Insurance, Meal, FlightCabinAncillary)
2. Project Setup for Ancillary Services
3. Building the Core Ancillary API
4. Implementing Insurance Coverage API
5. Developing the Meal Management API
6. Building the Flight-Specific Meal API
7. Managing Flight Cabin Ancillaries (dịch vụ khả dụng theo hạng ghế)
8. Testing Ancillary Service Integrations

## Section 11: Flight Booking & Reservation Management ⬜

1. Flight Booking Architecture Design (Booking, BookingPassenger, BookingSeat, BookingAncillary — snapshot fare tại thời điểm đặt)
2. Booking Service Project Initialization
3. Building the Core Flight Booking API (tạo booking, giữ ghế tạm thời)
4. Booking Status State Machine (`PENDING` → `AWAITING_PAYMENT` → `CONFIRMED` / `CANCELLED` / `EXPIRED`)
5. Tích hợp giữ/nhả ghế với Seat Service (Section 9) qua REST hoặc Feign — có cơ chế timeout tự huỷ giữ ghế
6. Testing the Reservation Workflow (đặt vé thành công, hết hạn giữ ghế, huỷ booking)

## Section 12: Payment Gateway Integration & Architecture ⬜

1. Payment Service Architectural Design (Payment, Transaction, Refund)
2. Setting Up the Payment Service Project
3. Implementing the Payment Processing API
4. Tích hợp cổng thanh toán (VNPay/Momo/Stripe sandbox — chọn 1 theo thị trường mục tiêu)
5. Xử lý Payment Webhook/Callback (xác thực chữ ký từ cổng thanh toán)
6. Đồng bộ trạng thái Payment → Booking (qua sự kiện Kafka, xem Section 17)
7. Testing Payment flows (thành công, thất bại, timeout, refund)

## Section 13: Inter-Service Communication & Service Discovery ⬜

1. RestTemplate for Synchronous Calls
2. Implementing Service Registry with Netflix Eureka (bắt đầu triển khai module `cloud/service-registry` đang rỗng trong pom)
3. Declarative REST Clients with Spring Cloud Feign
4. Strategies for Service-to-Service Communication (Part 1)
5. Advanced Service-to-Service Communication (Part 2)
6. Resilience: Circuit Breaker / Retry / Fallback với Resilience4j (tránh cascading failure khi 1 service down)

## Section 14: Implementing the API Gateway ⬜

1. Understanding the Role of an API Gateway
2. Implementing Spring Cloud Gateway (`cloud/api-gateway`)
3. Cấu hình Routing & Filter theo từng service
4. Global Exception Handling & Rate Limiting tại tầng Gateway

## Section 15: Securing Microservices with JWT ⬜

1. Secure Microservices With JWT
2. Implementing JWT-Based Authentication
3. Xác thực JWT tập trung tại API Gateway (chặn request trái phép trước khi vào service)
4. Role-based Authorization theo từng route/service
5. Refresh Token & xử lý token hết hạn
6. Validating and Testing JWT Integration

## Section 16: Containerization with Docker ⬜

1. Docker Fundamentals and Installation
2. Building Docker Images with Google Jib
3. Generating Optimized Docker Images (multi-stage, giảm kích thước image)
4. Cấu hình theo môi trường (dev/prod profile qua biến môi trường, không hardcode datasource)
5. Orchestrating Services with Docker Compose (bao gồm MySQL, Redis, Kafka, các service + gateway)

## Section 17: Event-Driven Systems with Apache Kafka ⬜

1. Introduction to Message Brokers and Kafka
2. Setting Up Apache Kafka
3. Implementing Kafka Producers and Consumers
4. Integrating Kafka in the Flight Service (thông báo thay đổi lịch bay/huỷ chuyến)
5. Testing Kafka Integration (Part 1)
6. Advanced Kafka Testing and Validation (Part 2)

## Section 18: Developing the Global Flight Search API ⬜

1. Search Architecture: tổng hợp dữ liệu từ Flight/FlightInstance (flight-ops) + Fare (pricing) + Seat availability (seat service)
2. Building the Consolidated Flight Search API
3. Chiến lược cache kết quả tìm kiếm (kết hợp với Redis — Section 20)
4. Phân trang & lọc kết quả (khoảng giá, hạng ghế, số điểm dừng, hãng bay)
5. Testing Search API (độ chính xác kết quả + hiệu năng khi gọi nhiều service song song)

## Section 19: Centralized Config with Spring Cloud Config ⬜

1. Introduction to Externalized Configuration
2. Implementing the Config Server (`cloud/config-server`)
3. Dockerizing the Config Server
4. Regenerating and Testing Dockerized Configs

## Section 20: Distributed Caching with Redis ⬜

1. Implementing Distributed Caching with Redis (cache kết quả tra cứu ít thay đổi: Airport, Airline, CabinClass)
2. Chiến lược invalidate cache khi có update/delete
3. Testing and Validating Redis Cache Operations

## Section 21: Event-Driven Notification Service ⬜

1. Notification Service Architecture and Setup
2. Implementing Email Notification Service
3. Integrating Apache Kafka in Notification Service (lắng nghe sự kiện booking/payment)
4. Testing and Validating Notification Service

## Section 22: Cloud Deployment & Production Readiness ⬜

1. Deploying Microservices on Hostinger VPS
2. Connect Domain Name
3. What is Nginx
4. Install Nginx On VPS (reverse proxy cho Gateway)
5. What is SSL certificates
6. Install SSL certificate for our domain
7. Quản lý biến môi trường/secret trên VPS (không commit `application.yaml` chứa mật khẩu thật)

---

## Section 23: Observability — Logging & Distributed Tracing ⬜

*(Bổ sung mới — bắt buộc khi hệ thống có >1 service gọi lẫn nhau qua Gateway/Feign/Kafka)*

1. Structured Logging với SLF4J + Logback (JSON format) cho toàn bộ service
2. Distributed Tracing với Micrometer Tracing + Zipkin — theo dõi 1 request xuyên nhiều service
3. Correlation ID / Trace ID propagation qua Feign header và Kafka message header
4. (Tuỳ chọn) Tập trung log về ELK stack hoặc Grafana Loki

## Section 24: API Documentation & Contract ⬜

*(Bổ sung mới — hiện repo chưa có OpenAPI/Swagger ở bất kỳ service nào)*

1. Tích hợp `springdoc-openapi` cho từng service
2. Tổng hợp Swagger UI của tất cả service qua API Gateway
3. Versioning API (`/api/v1/...`) — chuẩn bị cho breaking change của `ApiResponse<T>` (Section 2)

## Section 25: Testing & CI/CD Pipeline ⬜

*(Bổ sung mới — hiện repo chưa có test, chưa có CI/CD)*

1. Unit test cho Service layer (JUnit 5 + Mockito) — bắt buộc trước khi merge
2. Integration test cho Controller (`@SpringBootTest` + Testcontainers MySQL)
3. GitHub Actions: build + test tự động cho module thay đổi (dùng `mvn -pl <module> -am test`)
4. Đo coverage tối thiểu với JaCoCo
5. Tự động build & push Docker image khi merge vào `develop`/`main`

## Section 26: Capstone — End-to-End Flow & Handover ⬜

1. Kiểm thử luồng end-to-end: Search → Book → Pay → Notify
2. Load testing cơ bản (k6/JMeter) cho Search API và Booking API dưới tải đồng thời
3. Checklist go-live: đổi `ddl-auto` khỏi `update`, xoá secret hardcode, bật auth thật ở Gateway
4. Viết tài liệu bàn giao theo domain (`docs/features/<domain>/*.md` — tương tự cấu trúc `docs/features` của dự án wiki-service) cho mỗi service mới thêm

---

## Ghi chú

- Sections 1-8 phản ánh đúng trạng thái đã implement trong repo tại thời điểm viết roadmap này (xem [CLAUDE.md](../CLAUDE.md) mục "Repository & Module Structure" và "Known Gaps").
- Section 8 nên làm **trước** Section 9 để tránh nhân bản nợ kỹ thuật (thiếu exception handling, thiếu test, thiếu logging) sang các service mới.
- Sections 23-25 không có trong roadmap gốc nhưng cần thiết cho một hệ microservices nhiều service gọi lẫn nhau qua Gateway/Kafka — nên chèn trước Section 22 (Cloud Deployment) trong thực tế triển khai, dù đánh số sau để giữ nguyên thứ tự gốc.
