# Tài liệu dự án Airline Microservices

## Đọc gì, khi nào

| Bạn đang định làm gì | Đọc file nào |
|---|---|
| Hiểu luật của dự án (nguồn quy tắc gốc) | [`../CLAUDE.md`](../CLAUDE.md) |
| Biết dự án đi tới đâu, còn những gì | [`ROADMAP.md`](ROADMAP.md) |
| **Bắt đầu code một service mới** | [`development/02-new-service-checklist.md`](development/02-new-service-checklist.md) |
| Viết entity / repository / service / controller | `development/03` → `development/06` |
| Trước khi mở Pull Request | [`development/08-definition-of-done.md`](development/08-definition-of-done.md) |

## Quan hệ giữa `CLAUDE.md` và `docs/development/`

Hai tài liệu này **không trùng vai trò**, đừng copy nội dung qua lại:

- **`CLAUDE.md` = LUẬT.** Nói *cái gì bị cấm, cái gì bắt buộc, và tại sao*. Đây là nguồn
  quy tắc duy nhất. Khi có mâu thuẫn giữa hai bên, **`CLAUDE.md` thắng**.
- **`docs/development/` = HƯỚNG DẪN THỰC HÀNH.** Nói *làm thế nào cho đúng luật đó*,
  bằng code mẫu ĐÚNG/SAI lấy từ chính repo này, cộng checklist thao tác.

Mỗi trang trong `development/` đều ghi rõ nó hiện thực mục nào của `CLAUDE.md`.
Nếu bạn sửa luật trong `CLAUDE.md`, phải rà lại trang tương ứng ở đây — ngược lại
tài liệu sẽ trôi khỏi thực tế và trở thành thứ gây hại hơn là không có.

## Mục lục `development/`

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

## Cảnh báo về `ROADMAP.md`

`ROADMAP.md` được viết **trước** đợt refactor Nhóm A/B/C/D nên một số chỗ đã lỗi thời và
**mâu thuẫn với luật hiện tại** — cụ thể nó còn nói `GenerationType.IDENTITY`, còn nói
"JPA relationship trong cùng service thì OK", và còn ghi port collision đã fix xong.
Đừng dùng `ROADMAP.md` làm căn cứ kỹ thuật; nó chỉ còn giá trị về **phạm vi và thứ tự
công việc**. Cần một đợt sync riêng cho nó (xem `08-definition-of-done.md`, mục "Nợ tài liệu").
