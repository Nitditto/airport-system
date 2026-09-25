# Spring Cloud OpenFeign (Declarative HTTP Client)

> **Trạng thái**: 🟡 **Đã có sẵn hạ tầng nhưng chưa được dùng thật** — `AirlineClient`/`LocationClient`
> (`@FeignClient`) đã tồn tại trong `flight-ops-service` và `@EnableFeignClients` đã bật ở
> `FlightOpsServiceApplication`, nhưng `FlightServiceImpl` hiện đang gọi qua
> `AircraftIntegrationService`/`AirlineIntegrationService` (`RestTemplate` thủ công, xem
> [`inter-service-communication.md`](inter-service-communication.md)), **không** qua hai Feign
> Client này. Đây là nợ kỹ thuật cần dọn — xem §6.
> **Tài liệu liên quan**: [`ROADMAP.md`](../ROADMAP.md) Section 13 ·
> [`spring-cloud-service-discovery.md`](spring-cloud-service-discovery.md) ·
> [`inter-service-communication.md`](inter-service-communication.md) · [`../../CLAUDE.md`](../../CLAUDE.md).

---

## 1. 5W1H

| | Trả lời |
| :--- | :--- |
| **What** — Là gì? | Một cách gọi HTTP sang service khác bằng **Java interface khai báo** (`@FeignClient` + `@GetMapping`/`@PostMapping`) thay vì tự viết `RestTemplate.exchange(url, ...)`. Spring sinh implementation ngầm lúc runtime (dynamic proxy). |
| **Why** — Tại sao cần? | `RestTemplate` thủ công buộc phải tự nối chuỗi URL, tự `ParameterizedTypeReference` để unwrap `ApiResponse<T>`, tự try/catch `RestClientException` — lặp lại y hệt ở mỗi `*IntegrationService` (xem `AircraftIntegrationService`/`AirlineIntegrationService` hiện tại). Feign nén toàn bộ phần lặp đó vào 1 interface, giảm chỗ để gõ sai URL hoặc quên `.distinct()` khi ghép batch. |
| **Who** — Ai dùng? | Service **gọi ra ngoài** khai báo interface (hiện tại: `flight-ops-service` gọi `airline-core-service`, `location-service`). Service **bị gọi** không cần biết gì về Feign — nó chỉ là một REST API bình thường (`@RestController` + `ApiResponse<T>` như mọi service khác trong repo). |
| **When** — Khi nào dùng, khi nào không? | Dùng khi gọi HTTP đồng bộ sang **service khác trong hệ thống** đã đăng ký Eureka. **Không dùng** cho: gọi trong cùng 1 service (đó là inject thẳng `*Service` interface, xem [`../../CLAUDE.md`](../../CLAUDE.md) §11 Dependency Injection Pattern); giao tiếp bất đồng bộ (đó là Kafka, xem [`communication-patterns.md`](communication-patterns.md)); gọi API bên thứ ba không có trong hệ thống Eureka (dùng `RestClient`/`WebClient` thường, có thể vẫn khai Feign với `url =` cố định nhưng mất lợi ích load-balancing). |
| **Where** — Đặt ở đâu? | Package `client/` của service **gọi** (vd. `com.nitditto.client` trong `flight-ops-service`), tên interface `<TargetDomain>Client`. Response DTO dùng lại `payload/response/*Response` có sẵn trong `common-lib` — không tạo DTO riêng cho Feign. |
| **How** — Vận dụng ra sao? | Xem §3 (khai báo cơ bản) và §4 (timeout/retry/logging/interceptor — phần hay bị bỏ sót). |

---

## 2. So sánh với `RestTemplate` thủ công (đang dùng song song trong repo)

| Tiêu chí | `RestTemplate` (`*IntegrationService` hiện tại) | `@FeignClient` (`AirlineClient`/`LocationClient` có sẵn, chưa dùng) |
| :--- | :--- | :--- |
| Cách viết | Tự tạo chuỗi URL, tự `restTemplate.exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<...>(){})` | Khai method trên interface với `@GetMapping`/`@PostMapping`, Spring tự sinh phần gọi HTTP |
| Quản lý địa chỉ | Nối `"http://" + serviceName + path` thủ công mỗi chỗ gọi | `@FeignClient(name = "airline-core-service")` — 1 chỗ duy nhất |
| Tích hợp Eureka/Load Balancer | Cần `RestTemplate` bean đánh dấu `@LoadBalanced` | Tự động, không cần cấu hình thêm (miễn đã có `spring-cloud-starter-loadbalancer` trên classpath) |
| Unwrap `ApiResponse<T>` | Tự viết `ParameterizedTypeReference<ApiResponse<X>>` mỗi method | Vẫn phải khai kiểu trả về là `ApiResponse<X>` (xem lưu ý §3.3) hoặc để service đích trả thẳng `X` cho endpoint dành riêng cho nội bộ |
| Rủi ro | Dễ gõ sai URL (đã từng xảy ra: gọi nhầm port, thiếu `/` — xem [`inter-service-communication.md`](inter-service-communication.md)) | An toàn kiểu hơn, nhưng lỗi runtime nếu interface không khớp `@RequestMapping` thật của controller đích |

---

## 3. Khai báo cơ bản (đã có sẵn trong repo)

### 3.1. Dependency (`pom.xml` của `flight-ops-service` — đã thêm)

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```

### 3.2. Bật Feign tại Main Class (đã bật)

```java
// FlightOpsServiceApplication.java
@SpringBootApplication
@EnableJpaAuditing
@EnableFeignClients // quét mọi interface có @FeignClient trong com.nitditto và package con
public class FlightOpsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlightOpsServiceApplication.class, args);
    }
}
```

### 3.3. Interface hiện có

```java
// services/flight-ops-service/src/main/java/com/nitditto/client/AirlineClient.java
@FeignClient(name = "airline-core-service")
public interface AirlineClient {

    @GetMapping("/api/airlines/{id}")
    AirlineResponse getAirlineById(@PathVariable UUID id);

    @GetMapping("/api/aircrafts/{id}")
    AircraftResponse getAircraftById(@PathVariable UUID id);
}
```

**Điểm cần chú ý — sai lệch kiểu trả về:** `AirlineController.getAirlineById()` thật sự trả về
`ResponseEntity<ApiResponse<AirlineResponse>>`, nhưng interface trên khai `AirlineResponse` trần
(không bọc `ApiResponse`). Feign sẽ deserialize thẳng phần JSON body vào `AirlineResponse` —
nghĩa là nó cố map field `success`/`message`/`data` của `ApiResponse` vào field của
`AirlineResponse`, và mọi field không khớp bị bỏ qua thầm lặng → **`AirlineResponse` trả về rỗng
toàn bộ** dù HTTP 200. Đây là lỗi rất dễ bỏ sót vì không có exception nào được ném ra. Sửa đúng
phải khai `ApiResponse<AirlineResponse> getAirlineById(...)` rồi gọi `.getData()`, giống hệt cách
`AircraftIntegrationService` đang làm với `RestTemplate` + `ParameterizedTypeReference`.

### 3.4. Endpoint batch — Feign cho danh sách

Theo quy tắc chống N+1 ở [`../../CLAUDE.md`](../../CLAUDE.md) §7 Loại 2, **không** gọi Feign trong
vòng lặp. Bổ sung method batch giống hệt endpoint `POST /batch-by-ids` đã có ở `airline-core-service`:

```java
@FeignClient(name = "airline-core-service")
public interface AirlineClient {

    @GetMapping("/api/airlines/{id}")
    ApiResponse<AirlineResponse> getAirlineById(@PathVariable UUID id);

    @PostMapping("/api/airlines/batch-by-ids")
    ApiResponse<Map<UUID, AirlineResponse>> getAirlinesByIds(@RequestBody List<UUID> ids);

    @PostMapping("/api/aircrafts/batch-by-ids")
    ApiResponse<Map<UUID, AircraftResponse>> getAircraftsByIds(@RequestBody List<UUID> ids);
}
```

---

## 4. Cấu hình Timeout, Retry, Logging, Interceptor

Đây là phần **quan trọng nhất và dễ bị bỏ quên nhất** khi giới thiệu Feign — một interface trông
gọn gàng dễ khiến người mới nghĩ "xong rồi", nhưng mặc định Feign **không có timeout hợp lý, không
retry, và không log gì cả**. Thiếu phần này, một `airline-core-service` phản hồi chậm có thể treo
toàn bộ thread pool của `flight-ops-service`.

### 4.1. Timeout

Cấu hình theo tên client (namespace = giá trị trong `@FeignClient(name = "...")`) trong
`application.yaml` của service gọi:

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          airline-core-service:
            connect-timeout: 3000   # ms — thời gian chờ mở kết nối TCP
            read-timeout: 5000      # ms — thời gian chờ đọc response sau khi đã kết nối
          default:                  # áp dụng cho mọi Feign client không khai riêng
            connect-timeout: 3000
            read-timeout: 5000
```

So sánh với `RestTemplate`: tương đương `RestTemplateBuilder.connectTimeout(...).readTimeout(...)`
đã cấu hình trong [`AutoConfig.java`](../../services/flight-ops-service/src/main/java/com/nitditto/config/AutoConfig.java)
của `flight-ops-service` — cùng một mục đích (tránh treo thread khi service đích không phản hồi),
chỉ khác chỗ khai báo (YAML thay vì code Java).

### 4.2. Retry

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          airline-core-service:
            retryer: com.nitditto.config.CustomFeignRetryer  # tuỳ chọn, mặc định Retryer.NEVER_RETRY
```

Mặc định Feign **không retry** (`Retryer.NEVER_RETRY`). Muốn bật retry đơn giản, dùng
`Retryer.Default(period, maxPeriod, maxAttempts)` khai qua `@Bean` thay vì string class name phức
tạp:

```java
@Configuration
public class FeignRetryConfig {

    @Bean
    public Retryer feignRetryer() {
        // backoff 100ms -> tối đa 1s, thử lại tối đa 3 lần
        return new Retryer.Default(100, 1000, 3);
    }
}
```

**Chỉ retry cho GET (idempotent).** Retry một `POST /api/fares` (tạo mới) khi service đích đã xử
lý xong nhưng response bị mất trên đường về sẽ tạo **bản ghi trùng**. Nếu cần retry cho request
ghi dữ liệu, endpoint đó phải idempotent trước (idempotency key), việc đó nằm ngoài phạm vi cấu
hình Feign.

### 4.3. Logging

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          airline-core-service:
            logger-level: basic   # none (mặc định) | basic | headers | full
logging:
  level:
    com.nitditto.client.AirlineClient: DEBUG   # logger-level chỉ có tác dụng nếu logger ở mức DEBUG
```

| Mức | Log gì | Dùng khi |
| :--- | :--- | :--- |
| `none` | Không log gì (mặc định) | Production, traffic cao |
| `basic` | Method, URL, status code, thời gian | Vừa đủ để debug 404/timeout mà không tốn log |
| `headers` | Thêm request/response headers | Debug lỗi auth/content-type |
| `full` | Thêm cả body | **Chỉ bật tạm ở dev** — dễ log lọt dữ liệu nhạy cảm, tốn hiệu năng do đọc/ghi lại body |

### 4.4. Interceptor — gắn thêm dữ liệu vào mọi request

`RequestInterceptor` chạy trước mỗi lời gọi Feign, dùng để gắn header dùng chung — ví dụ propagate
`X-User-Id` hiện đang được truyền thủ công qua `@RequestHeader` ở các controller
(`AircraftController`, `AirlineController`...), hoặc sau này là Correlation ID cho tracing
(xem [`observability.md`](observability.md)):

```java
@Configuration
public class FeignClientConfig {

    @Bean
    public RequestInterceptor correlationIdInterceptor() {
        return requestTemplate -> {
            final String correlationId = MDC.get("correlationId");
            if (correlationId != null) {
                requestTemplate.header("X-Correlation-Id", correlationId);
            }
        };
    }
}
```

Áp cho **một** Feign client cụ thể bằng thuộc tính `configuration` thay vì toàn cục:

```java
@FeignClient(name = "airline-core-service", configuration = FeignClientConfig.class)
public interface AirlineClient { ... }
```

---

## 5. Xử lý lỗi — `ErrorDecoder` thay cho try/catch rải rác

`RestTemplate` hiện tại bắt `RestClientException` ngay trong từng `*IntegrationService`
(xem `AircraftIntegrationService.getAircraftById`). Feign gom việc này vào **một nơi duy nhất** —
`ErrorDecoder` — áp dụng cho mọi method của client đó:

```java
public class AirlineClientErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();

    @Override
    public Exception decode(final String methodKey, final Response response) {
        if (response.status() == 404) {
            return new ResourceNotFoundException("Airline", "response", methodKey);
        }
        return this.defaultDecoder.decode(methodKey, response);
    }
}
```

Khai trong cùng `configuration = FeignClientConfig.class` như interceptor ở §4.4. Ngoại lệ ném ra
từ đây bay thẳng lên `FlightServiceImpl` — vẫn cần try/catch ở tầng service hoặc kết hợp với
**fallback method** của Resilience4j để không làm sập cả request (xem
[`resilience-fault-tolerance.md`](resilience-fault-tolerance.md) §3).

---

## 6. Việc cần làm để dọn nợ kỹ thuật (không tự ý làm — cần bàn trước theo CLAUDE.md)

Hiện repo có **2 cách gọi song song** sang `airline-core-service`/`location-service`:
`AircraftIntegrationService`/`AirlineIntegrationService` (RestTemplate, đang thật sự được
`FlightServiceImpl` dùng) và `AirlineClient`/`LocationClient` (Feign, đã khai báo nhưng
không nơi nào inject). Giữ cả hai gây nhầm lẫn cho người đọc code sau này. Hai hướng xử lý:

1. **Migrate hẳn sang Feign**: sửa `AirlineClient`/`LocationClient` theo §3.3-3.4, thêm cấu hình
   §4, rồi thay `FlightServiceImpl` dùng `AirlineClient`/`AircraftClient` (cần tách interface theo
   đúng 1 domain/client, không gộp `AirlineClient` chứa cả `getAircraftById`), xoá
   `AircraftIntegrationService`/`AirlineIntegrationService`.
2. **Xoá Feign client chưa dùng**, giữ nguyên hướng `RestTemplate` hiện tại nếu đội chưa muốn học
   thêm 1 lớp trừu tượng mới ở giai đoạn này.

Đây là quyết định kiến trúc — theo đúng tinh thần [`../../CLAUDE.md`](../../CLAUDE.md) mục "Ngoài
phạm vi kiến trúc hiện tại": **hỏi trước khi tự chọn hướng và xoá code**.
