# Centralized Config (Spring Cloud Config Server)

`cloud/config-server` (:8888, profile `native`) phục vụ thư mục [`config-repo/`](../../config-repo) cho mọi service nghiệp vụ và `api-gateway`.

## Cách hoạt động

```
config-repo/application.yaml          ─┐ dùng chung cho mọi client
config-repo/<tên-service>.yaml         ─┴▶ config-server ──GET /{service}/default──▶ client lúc khởi động
```

- Client lấy `application.yaml` + `<spring.application.name>.yaml` (file riêng thắng file chung), ghép vào `Environment`.
- Thứ tự ưu tiên (cao → thấp): biến môi trường > config-server > `application.yaml` đóng gói trong jar.
- Hiện `config-repo/` có: `application.yaml` (Eureka `register/fetch/prefer-ip`, JPA `open-in-view`, `ddl-auto`, batch) và file riêng chứa tên topic Kafka của `flight-ops`, `seat`, `booking`, `payment`.

## Client

Mọi service nghiệp vụ và gateway: dependency `spring-cloud-starter-config` (khai một lần ở `services/pom.xml`, riêng gateway ở pom của nó) và

```yaml
spring:
  config:
    import: "${CONFIG_IMPORT_MODE:optional:}configserver:${CONFIG_SERVER_URL:http://localhost:8888}"
```

- **IDE / test:** `optional:` nên thiếu config-server vẫn chạy bằng `application.yaml` đóng gói (chỉ có warning `Could not locate PropertySource`). Vì vậy `application.yaml` của service vẫn giữ giá trị mặc định đủ chạy một mình.
- **Docker compose:** `CONFIG_IMPORT_MODE=""` bỏ tiền tố `optional:` → thiếu config-server thì service **không khởi động** (`ConfigClientFailFastException`), `restart: unless-stopped` thử lại cho tới khi config-server lên.
- Không dùng `SPRING_CONFIG_IMPORT` để bắt buộc: import khai báo bằng biến môi trường chạy trước `application.yaml`, lúc đó chưa có `spring.application.name`, client chỉ nhận `application.yaml` chung (không nhận file riêng).

## Quy tắc nội dung `config-repo/`

- Chỉ giá trị cố định, **không bí mật**. Secret (`JWT_SECRET`, `VNPAY_*`, `RAZORPAY_*`) ở `.env` / biến môi trường (CLAUDE.md §4, §17).
- **Không placeholder `${...}`**: config-server tự giải placeholder bằng môi trường của nó, không phải của client (làm mất khả năng ghi đè bằng biến môi trường của client).
- Giá trị phụ thuộc môi trường (datasource URL, Eureka URL, Kafka bootstrap) để biến môi trường quyết định; compose vẫn ghi đè như trước.
- `server.port`, `spring.application.name`, `spring.config.import` luôn ở `application.yaml` của service (cần trước khi gọi server).

## Chạy

```bash
# IDE: từ thư mục gốc microservices (search-locations mặc định ../../config-repo so với thư mục module)
mvn -pl cloud/config-server spring-boot:run
curl http://localhost:8888/flight-ops-service/default      # thấy flight-ops-service.yaml + application.yaml

# Docker: config-server là một service của docker-compose.yml, mount ./config-repo vào /config-repo (chỉ đọc)
mvn package jib:dockerBuild -DskipTests
docker compose up -d
```

- Cổng 8888 **không** publish ra host trong compose; chỉ container trong mạng `airline` gọi được.
- `config-server` có healthcheck (bash `/dev/tcp` gửi `GET /actuator/health`, vì image Jib không có curl/wget) và mọi client `depends_on: config-server: condition: service_healthy`. Nếu chỉ chờ `service_started` thì client lao vào kết nối khi JVM chưa lắng nghe :8888 và crash vì `ConfigClientFailFastException` (restart loop), nhất là `api-gateway` vì nó không chờ MySQL.
- Đổi `config-repo/*.yaml` rồi **restart** service liên quan để nhận giá trị mới. Chưa có refresh runtime (`/actuator/refresh`, Spring Cloud Bus).
- `service-registry` và `config-server` không import config.

## Thêm cấu hình / service mới

- Cấu hình dùng chung: thêm vào `config-repo/application.yaml` (nhớ: không placeholder, không bí mật).
- Cấu hình riêng một service: tạo `config-repo/<spring.application.name>.yaml`.
- Service mới: thêm `spring.config.import` như trên vào `application.yaml`, đặt `CONFIG_IMPORT_MODE`/`CONFIG_SERVER_URL` trong block compose của nó và `depends_on: config-server`.

## Giới hạn hiện tại

- Backend `native` (thư mục trong repo), chưa dùng Git remote nên chưa có lịch sử/phiên bản hoá config riêng ngoài Git của repo này.
- Không mã hoá giá trị (`{cipher}`), không xác thực cho config-server (an toàn nhờ không publish cổng ra host).
