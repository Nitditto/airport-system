# 01 — 12 quy tắc vàng

> Hiện thực: toàn bộ `CLAUDE.md`. Trang này là bản rút gọn để nhớ; chi tiết và lý do nằm ở các trang sau.

Nếu chỉ đọc được một trang trong toàn bộ tài liệu này, đọc trang này.

| # | Quy tắc | Vi phạm sẽ ra sao |
|---|---|---|
| 1 | **Không bao giờ dùng annotation quan hệ JPA.** Mọi khoá ngoại là cột `UUID` thô. | Vỡ ranh giới service, N+1 ngầm, vòng lặp serialize |
| 2 | **Mọi entity `extends BaseEntity`.** Không tự khai `id`/`createdAt`/`updatedAt`. | ID không đồng nhất, phân mảnh index |
| 3 | **UUIDv7, không phải v4.** Cấm `@GeneratedValue(strategy = GenerationType.UUID)`. | Index phân mảnh — hỏng âm thầm, chỉ lộ khi bảng lớn |
| 4 | **Mọi endpoint trả list phải phân trang.** Không `findAll()` trần. | Bảng lớn dần là sập |
| 5 | **Không gọi `repository.*` hay HTTP client trong vòng lặp.** | N+1 — query tăng theo số dòng |
| 6 | **Cột dùng để lọc phải có `@Index`.** | Full table scan |
| 7 | **Method ghi đụng ≥2 bảng phải có `@Transactional`.** | Dữ liệu mồ côi khi lỗi giữa chừng |
| 8 | **Xoá cha phải tự chặn/xoá con.** Không còn `cascade` để dựa vào. | FK trỏ vào bản ghi không tồn tại |
| 9 | **Mọi `@RequestBody` phải có `@Valid`.** | Dữ liệu rác vào thẳng DB |
| 10 | **Mọi endpoint trả dữ liệu phải bọc `ApiResponse<T>`.** (`DELETE` → `204`, không body) | Client phải xử lý 2 format khác nhau |
| 11 | **Không tin bất cứ gì client gửi để xác định danh tính.** Lấy từ `SecurityContextHolder`. | Ai cũng đọc được dữ liệu người khác |
| 12 | **Controller/service inject qua interface**, không phải `*ServiceImpl`. | Không thay thế/test được |

## Bảng annotation bị CẤM tuyệt đối

```
@ManyToOne   @OneToMany   @OneToOne   @ManyToMany
@JoinColumn  @JoinTable   @ElementCollection   @MapsId
mọi CascadeType   orphanRemoval
@GeneratedValue(strategy = GenerationType.UUID)   ← sinh UUIDv4, cấm
@Data / @EqualsAndHashCode trên entity            ← Lombok sinh equals trên mọi field
```

**Ngoại lệ hợp lệ duy nhất:** `@Embedded` / `@Embeddable`. Nó nhúng field vào chính bảng
đó, không sinh join, không sinh query phụ — không phải relationship. Giữ nguyên, đừng gỡ
khi refactor.

## Ba câu hỏi tự vấn trước khi commit

1. Tôi có vừa thêm annotation nào trong bảng cấm ở trên không?
2. Có lời gọi `repository.*` hoặc HTTP client nào nằm trong `for` / `stream().map()` / `forEach` không?
3. Endpoint mới trả list — đã phân trang chưa, và cột lọc đã có index chưa?

Nếu cả ba đều "không / rồi" thì mới sang [08-definition-of-done.md](08-definition-of-done.md).
