# Docker Compose & Jib

> Hiện thực: [`docker-compose.yml`](../../docker-compose.yml), [`docker-compose.dev.yml`](../../docker-compose.dev.yml), [`docker/mysql/init.sql`](../../docker/mysql/init.sql), cấu hình `jib-maven-plugin` ở [`pom.xml`](../../pom.xml) gốc.

Trang này mô tả cách đóng gói toàn bộ hệ thống thành container và chạy bằng một lệnh. Dành cho dev cần chạy full stack local mà không phải bật từng service trong IDE.

## 1. Tổng quan

```
                  ┌────────────────────── mạng "airline" ──────────────────────┐
 client ──:5000─▶ │ api-gateway ──▶ service-registry (:8761) ◀── mọi service   │
                  │      │                                                      │
                  │      └─(lb qua Eureka)─▶ user / airline-core / flight-ops / │
                  │                          location / pricing / seat /        │
                  │                          ancillary / booking / payment      │
                  │                                    │                        │
                  │                                    ▼                        │
                  │                          mysql (9 database)                 │
                  └─────────────────────────────────────────────────────────────┘
```

- **11 container BE + 1 MySQL** (+ `frontend` nginx khi bật profile `full`): `service-registry`, `api-gateway` và 9 service nghiệp vụ (`user`, `airline-core`, `flight-ops`, `location`, `pricing`, `seat`, `ancillary`, `booking`, `payment`).
- Image **không viết Dockerfile** — build bằng [Jib](https://github.com/GoogleContainerTools/jib) trực tiếp từ Maven. Chỉ goal `jib:dockerBuild` cần Docker daemon.
- Chỉ `api-gateway`, `service-registry` và `mysql` publish port ra máy host (thêm `frontend` :5173 nếu bật profile `full`; nginx của nó proxy `/auth`, `/api` sang gateway nên không cần CORS). Các service nghiệp vụ chỉ truy cập được trong mạng compose (qua gateway hoặc Feign + Eureka).

## 2. Điều kiện cần

| Thứ | Ghi chú |
|---|---|
| Docker Desktop (hoặc Docker Engine + Compose v2) | `docker compose version` phải chạy được |
| JDK 17 + Maven | Build từ thư mục gốc, **không** dùng `mvnw` lẻ trong từng service |
| File `.env` ở thư mục gốc | Copy từ [`.env.example`](../../.env.example) rồi điền giá trị; compose tự đọc vì nằm cạnh |

## 3. Quy trình chạy

```bash
# 1. Tạo .env (một lần)
cp .env.example .env        # sửa JWT_SECRET (>= 32 ký tự), RAZORPAY_*, ADMIN_*

# 2. Build và cài common-lib + toàn bộ module vào local repo
mvn clean install

# 3. Đóng gói image vào Docker daemon local
mvn compile jib:dockerBuild

# 4. Chạy
docker compose up -d                       # chỉ BE
docker compose --profile full up -d --build   # BE + frontend (cần repo ../frontend)

# 5. Theo dõi
docker compose ps
docker compose logs -f api-gateway
```

Bước 2 bắt buộc chạy **từ thư mục gốc**: các service phụ thuộc `common-lib`, nếu bỏ qua Jib sẽ báo thiếu class `AirlineResponse`, `AirlineRequest`... Nếu chỉ build một module: `mvn -pl services/pricing-service -am compile jib:dockerBuild`.

Dừng / dọn:

```bash
docker compose down                      # dừng stack BE, giữ dữ liệu MySQL
docker compose --profile full down       # dừng cả frontend (nếu chạy với profile full)
docker compose --profile "*" down -v     # dừng tất cả và XOÁ volume mysql-data (mất toàn bộ DB)
```
> **Lưu ý**: Nếu bạn khởi động bằng `--profile full`, lệnh `docker compose down` thông thường sẽ **không** dừng container `frontend`. Hãy thêm `--profile full` (hoặc `--profile "*"`) khi `down`.


Sau khi lên, kiểm tra:

- Eureka dashboard: <http://localhost:8761> — phải thấy đủ service đăng ký.
- Web (chỉ khi `--profile full`): <http://localhost:5173>. Gateway: <http://localhost:5000/actuator/health>.
- MySQL từ host: `localhost:3307`, user `root`.

## 4. Jib

Cấu hình tập trung ở `pluginManagement` của `pom.xml` gốc:

```xml
<jib.version>3.4.5</jib.version>
<docker.image.prefix>nitditto</docker.image.prefix>
...
<to>
  <image>${docker.image.prefix}/${project.artifactId}:${project.version}</image>
</to>
```

- Mỗi module (9 service + `api-gateway` + `service-registry`) chỉ khai `groupId`/`artifactId` của plugin, **không** ghim version hay image name riêng — đúng rule quản lý version tập trung.
- Tên image = `nitditto/<artifactId>:0.0.1-SNAPSHOT`, ví dụ `nitditto/pricing-service:0.0.1-SNAPSHOT`.
- Đổi Docker Hub username: sửa property `docker.image.prefix` (hoặc `-Ddocker.image.prefix=abc`) **và** đặt cùng giá trị cho `DOCKER_IMAGE_PREFIX` khi chạy compose.
- Jib tách layer dependency / resource / class nên build lại sau khi sửa code rất nhanh.

| Goal | Dùng khi |
|---|---|
| `jib:dockerBuild` | Build thẳng vào Docker daemon local (dùng cho compose) |
| `jib:buildTar` | Xuất `target/jib-image.tar`, không cần daemon |
| `jib:build` | Build và push lên registry (cần `docker login`) |

## 5. Biến môi trường ghi đè

Các `application.yaml` hardcode `localhost` để chạy local bằng IDE. Trong container `localhost` là chính nó, nên compose ghi đè bằng env (Spring relaxed binding), **không sửa yaml**. Chạy trong IDE vẫn dùng `localhost` như cũ.

| Biến | Áp dụng | Tác dụng |
|---|---|---|
| `SPRING_DATASOURCE_URL` | 9 service nghiệp vụ | `jdbc:mysql://mysql:3306/<db riêng>` |
| `SPRING_DATASOURCE_PASSWORD` | 9 service nghiệp vụ | Mặc định `123456`, đổi qua `MYSQL_ROOT_PASSWORD` |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | 9 service nghiệp vụ | `http://service-registry:8761/eureka/` |
| `EUREKA_URL` | `api-gateway` | Yaml của gateway đã có placeholder `${EUREKA_URL:...}` |
| `JWT_SECRET` | `user-service`, `api-gateway` | **Phải giống nhau** (user ký, gateway verify). Thiếu thì compose từ chối chạy |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | `user-service` | Seed tài khoản admin đầu tiên; để trống = không seed |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` | `payment-service` | Key Razorpay test |
| `CORS_ALLOWED_ORIGINS` | `api-gateway` | Origin cách nhau bằng dấu phẩy; trống = chặn mọi origin |

Biến điều khiển chính compose (đặt trong `.env` hoặc shell):

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `DOCKER_IMAGE_PREFIX` | `nitditto` | Prefix image |
| `IMAGE_TAG` | `0.0.1-SNAPSHOT` | Tag image |
| `MYSQL_ROOT_PASSWORD` | `123456` | Mật khẩu root MySQL (khớp password trong yaml dev) |

## 6. MySQL

- Image `mysql:8.4`, một instance chứa **9 database** (database-per-service vẫn giữ nguyên về mặt logic): `airline`, `airline_user`, `airline_flight`, `airline_location`, `airline_pricing`, `airline_seat`, `airline_ancillary`, `airline_booking`, `airline_payment`.
- [`docker/mysql/init.sql`](../../docker/mysql/init.sql) tạo database bằng `CREATE DATABASE IF NOT EXISTS`. Hibernate (`ddl-auto: update`) chỉ tạo **bảng**, không tạo database.
- Script trong `docker-entrypoint-initdb.d` **chỉ chạy lần đầu** khi volume trống. Thêm service mới có database mới → sửa `init.sql` rồi `docker compose down -v` (mất dữ liệu), hoặc tạo database thủ công qua `docker compose exec mysql mysql -uroot -p`.
- Dữ liệu lưu ở volume `mysql-data`, sống sót qua `down` (không kèm `-v`).
- Publish ra host ở `3307` (không phải 3306) để không đụng MySQL cài sẵn trên máy.
- Khoá chính lưu `binary(16)`; query tay phải bọc `BIN_TO_UUID(id)` / `UUID_TO_BIN('...')`.

## 7. Thứ tự khởi động

```
mysql (healthy) ──┐
                  ├──▶ 9 service nghiệp vụ
service-registry ─┤        (started)
                  └──▶ api-gateway (chỉ chờ service-registry)
```

- MySQL có healthcheck (`mysqladmin ping`); các service nghiệp vụ dùng `condition: service_healthy`, tránh crash vì kết nối DB khi MySQL chưa sẵn sàng.
- `service-registry` chỉ chờ `service_started` — image Jib không có `curl` nên chưa có healthcheck. Eureka client tự retry đăng ký, vài giây đầu có thể thấy warning kết nối registry.
- Service đăng ký với Eureka bằng IP container (`prefer-ip-address: true`) nên gateway và Feign gọi chéo được trong mạng compose. Sau khi khởi động, đợi khoảng 30–60 giây để gateway thấy đủ instance.

## 8. Xử lý sự cố

| Triệu chứng | Nguyên nhân & cách xử lý |
|---|---|
| `Unauthorized for registry-1.docker.io/library/eclipse-temurin: 401` khi chạy Jib | Credential Docker Hub lưu trong máy đã hỏng/hết hạn. `docker logout` rồi chạy lại (pull ẩn danh), hoặc `docker login` lại |
| Jib báo không tìm thấy class `*Request`/`*Response` | Chưa `mvn clean install` ở thư mục gốc, thiếu `common-lib` |
| `required variable JWT_SECRET is missing` | Chưa có `.env` hoặc thiếu `JWT_SECRET` |
| Service restart liên tục, log `Communications link failure` | MySQL chưa healthy hoặc sai `MYSQL_ROOT_PASSWORD`. Nếu đã đổi password sau lần khởi tạo đầu: `docker compose down -v` |
| `Unknown database 'airline_xxx'` | Volume cũ được tạo trước khi `init.sql` có database đó → `down -v` hoặc tạo tay |
| Gateway trả 503 / không tìm thấy service | Service chưa đăng ký xong Eureka; xem <http://localhost:8761> |
| Port 5173/5000/8761/3307 đã bị chiếm | Đổi phần bên trái của `ports:` trong compose |
| Sửa code mà container vẫn chạy bản cũ | Chạy lại `mvn compile jib:dockerBuild` rồi `docker compose up -d` (image BE do Jib build, compose không tự build; riêng `frontend` thêm `--profile full --build`) |

## 9. Giới hạn hiện tại

- Compose chỉ phục vụ **dev/demo**: mật khẩu MySQL mặc định `123456`, `ddl-auto: update`. Không dùng nguyên trạng cho production.
- Image dùng tag `SNAPSHOT`, chưa push lên registry, chưa có CI/CD.
- Chưa healthcheck cho Eureka và các service nghiệp vụ.
- Frontend build bằng `Dockerfile` riêng, `VITE_*` nhúng lúc build nên đổi biến phải `--build` lại.
- Chưa có config server; mọi cấu hình đi qua env của compose.
- Mới xác nhận `docker compose config` hợp lệ; chưa chạy thử `docker compose up` đầy đủ.

## 10. Dev chỉ cần MySQL

Chạy service từ IDE (`application.yaml` trỏ `localhost:3306`, root/`123456`) thì chỉ cần bật MySQL:

```bash
docker compose -f docker-compose.dev.yml up -d
```

Dev frontend chạy `npm run dev` trên máy host (HMR) và gọi gateway `:5000`, không cần profile `full`.
