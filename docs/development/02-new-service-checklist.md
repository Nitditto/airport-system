# 02 — Dựng một service mới từ 0

> Hiện thực: `CLAUDE.md` mục "Repository & Module Structure", "Build Commands", "Database".
> Case thật dùng xuyên suốt trang này: **`seat-service`** (Section 9 của ROADMAP).

## Bảng đăng ký port & database (cập nhật khi thêm service)

Trước khi chọn port/DB cho service mới, **đối chiếu bảng này** — dự án đã từng dính
port collision một lần (`flight-ops` vs `pricing`), đừng lặp lại.

| Service | Port | Database |
|---|---|---|
| user-service | 5001 | `airline_user` |
| airline-core-service | 5002 | `airline` |
| flight-ops-service | 5003 | `airline_flight` |
| location-service | 5004 | `airline_location` |
| pricing-service | 5005 | `airline_pricing` |
| **seat-service** | **5006** | **`airline_seat`** |
| *(service tiếp theo)* | *5007* | *`airline_<domain>`* |

Kiểm tra nhanh trước khi chốt port:

```bash
grep -h -A1 "^server:" services/*/src/main/resources/application.yaml | grep port
```

## Bước 1 — Khai module vào reactor

`services/pom.xml`:

```xml
<modules>
    <module>user-service</module>
    ...
    <module>seat-service</module>   <!-- thêm dòng này -->
</modules>
```

## Bước 2 — `pom.xml` của service

Bắt buộc có `common-lib`; **không tự ghim `<version>`** cho bất kỳ dependency nào
(version do parent POM / BOM quản lý — xem `CLAUDE.md` "Lưu ý khi thêm dependency/version").

```xml
<parent>
    <groupId>com.nitditto</groupId>
    <artifactId>services</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</parent>
<artifactId>seat-service</artifactId>

<dependencies>
    <dependency>
        <groupId>com.nitditto</groupId>
        <artifactId>common-lib</artifactId>
        <version>${project.version}</version>   <!-- ${project.version} là OK, không phải ghim cứng -->
    </dependency>
    <!-- spring-boot-starter-data-jpa, -webmvc, mysql-connector-j, lombok, devtools -->
</dependencies>
```

> **Dọn rác:** Spring Initializr sinh kèm `mvnw`, `mvnw.cmd`, `.mvn/`, `HELP.md` trong
> mỗi service. Repo **không dùng** các wrapper lẻ đó (luôn chạy `mvn` từ thư mục gốc).
> Xoá chúng khi tạo service mới thay vì để tích tụ thêm.

## Bước 3 — `application.yaml` ĐẦY ĐỦ

Đây là chỗ dễ thiếu nhất vì service mới thường copy file tối giản của Spring Initializr.

### ❌ SAI — `seat-service/application.yaml` hiện tại trong repo

```yaml
server:
  port: 5006
spring:
  application:
    name: seat-service
  datasource:
    url: jdbc:mysql://localhost:3306/airline_seat
    username: root
    password: 123456
  jpa:
    hibernate:
      ddl-auto: update
```

Thiếu 4 thứ bắt buộc: `open-in-view: false`, `jdbc.batch_size`, `order_inserts`,
`order_updates`. Service sẽ chạy được — và đó chính là vấn đề, không có gì báo lỗi cho bạn.

### ✅ ĐÚNG — bám theo `pricing-service` (đã chuẩn hoá ở Nhóm C)

```yaml
server:
  port: 5006

spring:
  application:
    name: seat-service

  datasource:
    url: jdbc:mysql://localhost:3306/airline_seat
    username: root
    password: 123456
  jpa:
    open-in-view: false          # giải phóng DB connection ngay khi service trả về
    hibernate:
      ddl-auto: update           # DEV ONLY
    properties:
      hibernate:
        jdbc:
          batch_size: 50         # saveAll() mới thật sự batch dưới JDBC
        order_inserts: true
        order_updates: true
```

Khi cần **đếm số query để soi N+1**, bật tạm khối này rồi tắt lại, đừng commit:

```yaml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
logging:
  level:
    org.hibernate.SQL: DEBUG
```

## Bước 4 — Tạo database

Hibernate `ddl-auto: update` tự tạo **bảng**, nhưng **không tự tạo database**:

```sql
CREATE DATABASE airline_seat;
```

## Bước 5 — Package layout (giống hệt 5 service kia)

```
services/seat-service/src/main/java/com/nitditto/
├── SeatServiceApplication.java
├── controller/          # @RestController
├── service/             # interface — hợp đồng nghiệp vụ
│   └── impl/            # *ServiceImpl
├── repository/          # Spring Data JPA interface
│   └── spec/            # *Specs (chỉ khi cần filter động ≥3 điều kiện)
├── model/               # @Entity — extends BaseEntity
├── mapper/              # static method thuần, KHÔNG inject repository
├── converter/           # AttributeConverter (thay cho @ElementCollection)
└── exception/           # GlobalExceptionHandler riêng của service này
```

## Bước 6 — `GlobalExceptionHandler` (bắt buộc, copy từ service có sẵn)

`@RestControllerAdvice` có scope theo application context nên **mỗi service phải có
bản riêng** — không dùng chung từ `common-lib` được. Copy nguyên từ
`services/pricing-service/.../exception/GlobalExceptionHandler.java`, đổi package.

Bốn handler bắt buộc: `ResourceNotFoundException` → 404, `BusinessException` → 400,
`MethodArgumentNotValidException` → 400 (gom field error), và **catch-all `Exception.class`**
→ 500 với message chung chung `"Internal server error"` (log đầy đủ ở server, **không bao
giờ lộ stack trace ra client**).

## Bước 7 — Build & chạy

Luôn chạy từ **thư mục gốc**, không dùng `mvnw` lẻ trong service:

```bash
mvn -pl services/seat-service -am clean install
```

```bash
mvn -pl services/seat-service spring-boot:run
```

## Checklist dựng service mới

- [ ] Đã thêm `<module>` vào `services/pom.xml`
- [ ] Port chưa trùng (đối chiếu bảng đăng ký ở đầu trang)
- [ ] Database riêng, đã `CREATE DATABASE`, **không trỏ nhầm vào DB service khác**
- [ ] `application.yaml` có đủ `open-in-view: false` + 3 thuộc tính batching
- [ ] `pom.xml` không tự ghim `<version>` nào
- [ ] Đã xoá `mvnw`/`mvnw.cmd`/`.mvn/`/`HELP.md` thừa
- [ ] Có `exception/GlobalExceptionHandler.java` với đủ 4 handler
- [ ] Package layout khớp 5 service hiện có
- [ ] `mvn -pl services/<tên> -am clean install` pass
