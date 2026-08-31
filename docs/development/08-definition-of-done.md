# 08 — Definition of Done

> Hiện thực: `CLAUDE.md` mục "N+1 Query Prevention / Checklist khi review code" và "Git Convention".

Chạy checklist này **trước khi mở Pull Request**, không phải sau khi reviewer hỏi.

## A. Tự soát code

### Entity & Persistence

- [ ] Entity mới `extends BaseEntity`, không tự khai `id`/`createdAt`/`updatedAt`
- [ ] Không lọt annotation quan hệ nào: `@ManyToOne` `@OneToMany` `@OneToOne` `@ManyToMany` `@JoinColumn` `@JoinTable` `@ElementCollection` `@MapsId` `CascadeType` `orphanRemoval`
- [ ] Không có `@GeneratedValue(strategy = GenerationType.UUID)` (sinh UUIDv4)
- [ ] Mọi FK là `UUID` + `columnDefinition = "binary(16)"`
- [ ] Cột mới dùng để lọc đã có `@Index`
- [ ] Không dùng `@Data`/`@EqualsAndHashCode` trên entity

Lệnh soát nhanh:

```bash
grep -rn "@ManyToOne\|@OneToMany\|@OneToOne\|@ManyToMany\|@JoinColumn\|@ElementCollection\|GenerationType.UUID\|GenerationType.AUTO" --include=*.java services/ common-lib/
```

### Query & Performance

- [ ] Không có `repository.*` hay HTTP client nằm trong `for` / `stream().map()` / `forEach`
- [ ] Mỗi loại dữ liệu con đúng **một** query `...In(ids)`, danh sách ID đã `.distinct()`
- [ ] Endpoint trả list đã phân trang (`Page<T>` + `Pageable`)
- [ ] Kiểm tra tồn tại dùng `existsBy*`, không `findBy*`/`countBy*`
- [ ] Ghi hàng loạt dùng `saveAll()`, không `save()` trong loop
- [ ] **Đã bật SQL log và đếm số query thực tế** cho endpoint trả list

Bật đếm query (nhớ tắt và **không commit**):

```yaml
spring:
  jpa:
    show-sql: true
logging:
  level:
    org.hibernate.SQL: DEBUG
```

Nếu số dòng SQL tăng theo số bản ghi trả về thì đang có N+1 — không cần đoán.

### Service & API

- [ ] Method ghi đụng ≥2 bảng có `@Transactional`
- [ ] Luồng xoá cha đã chặn hoặc xoá con tường minh
- [ ] Ném `ResourceNotFoundException`/`BusinessException`, không `throw new Exception`
- [ ] Controller inject **interface**, không phải `*ServiceImpl`
- [ ] Mọi `@RequestBody` có `@Valid` — kiểm tra cả `create` **và** `update`
- [ ] Mọi endpoint trả dữ liệu bọc `ApiResponse<T>`; `DELETE` trả `204` không body
- [ ] Mọi phép tính trên `Double`/`Integer` đến từ request đều null-safe
- [ ] `@Slf4j` + `log.info` cho create/update/delete

### Security

- [ ] Danh tính lấy từ `SecurityContextHolder`, không từ header client
- [ ] Không commit secret; không log password/token

## B. Build & chạy thật

```bash
mvn clean install
```

- [ ] Build toàn reactor pass
- [ ] Đã **chạy thật** service và gọi thử endpoint mới (không chỉ compile)
- [ ] Đã dọn dữ liệu test tạo ra trong lúc verify

> **Vì sao bắt buộc chạy thật:** một entity thiếu `@Entity`, một `@Query` trỏ vào property
> không còn tồn tại, hay một index trùng tên — tất cả đều **compile sạch** và chỉ chết lúc
> Hibernate khởi động hoặc lúc gọi endpoint.

## C. Git

- [ ] Nhánh tách từ `develop` đã cập nhật, đặt tên `<type>/<mô-tả-kebab-case>` (dùng `feature/`, **không** `feat/`)
- [ ] Một nhánh = một mục đích
- [ ] Commit theo Conventional Commits, có scope đúng bảng (`common` `cloud` `user` `airline-core` `location` `flight-ops` `pricing` `build`)
- [ ] Subject tiếng Anh, thể mệnh lệnh, chữ đầu thường, không dấu chấm cuối, ≤72 ký tự
- [ ] Body giải thích **WHY** khi thay đổi không hiển nhiên
- [ ] Có footer `BREAKING CHANGE:` nếu phá client hoặc phá schema
- [ ] Không commit `target/`, `.idea/`, `.vscode/`, file build
- [ ] Đã tự review diff: `git diff develop...HEAD`

```bash
git diff develop...HEAD --stat
```

```bash
git status --short
```

## D. Pull Request

- [ ] PR nhắm vào `develop`
- [ ] Title viết như một commit message conventional (**không** để GitHub tự sinh từ tên nhánh)
- [ ] Description có đủ: Mục đích / Thay đổi chính / Cách kiểm thử / Breaking change / Checklist
- [ ] PR dưới ~400 dòng thay đổi, hoặc giải thích rõ vì sao phải lớn
- [ ] Build không đỏ

## E. Sau khi merge

- [ ] Xoá nhánh cả local lẫn remote

```bash
git branch -d feature/seat-service
```

```bash
git push origin --delete feature/seat-service
```

---

## Nợ tài liệu đang tồn đọng

Ghi lại ở đây để không quên; **không** thuộc phạm vi bất kỳ PR tính năng nào:

1. **`docs/ROADMAP.md` đã lỗi thời** — viết trước đợt refactor Nhóm A/B/C/D. Còn nói
   `GenerationType.IDENTITY`, còn nói "JPA relationship trong cùng service thì OK"
   (nay đã cấm hoàn toàn), còn ghi port collision `flight-ops` vs `pricing` (đã fix), và
   còn đánh dấu Section 8 là ⬜ dù phần lớn đã xong. Cần một đợt sync riêng nhánh `docs/`.
2. **Bảng port trong `CLAUDE.md` mục "Repository & Module Structure"** ghi `flight-ops`
   port 5003 nhưng phần mô tả cây thư mục cần rà lại cho khớp `application.yaml` thực tế,
   và chưa liệt kê `seat-service` (5006).
3. **`AircraftRepository` thiếu `existsByAirlineId`** — `AirlineServiceImpl.deleteAirline()`
   đang dùng `findByAirlineId(...).isEmpty()` để kiểm tra tồn tại.
4. **`getUserById(UUID)` trong `user-service`** cho phép bất kỳ user đã đăng nhập nào đọc
   thông tin user khác qua ID (IDOR). Đã nhận diện, cố ý để ngoài phạm vi đợt vá header
   `X-User-Email`, cần task riêng.
5. **`airline-core-service` tin header `X-User-Id`** do client gửi — cùng loại lỗ hổng với
   `X-User-Email` đã vá. Chỉ an toàn khi có API Gateway đặt header sau khi verify JWT.
6. **Chưa có test nào trong toàn repo**, chưa có CI/CD, chưa có Docker.
