-- =============================================================================
-- Thêm giá trị DUPLICATE vào enum payments.status (fix/payment-single-success, #63).
-- DUPLICATE ghi nhận khoản thanh toán thứ hai cho một booking đã thanh toán (khách bị thu tiền hai lần, cần hoàn tay).
--
-- Chạy THỦ CÔNG trên database airline_payment đã tồn tại; KHÔNG được mount vào initdb.
-- Hibernate tạo payments.status thành ENUM trên MySQL, và ddl-auto: update KHÔNG thêm giá trị vào enum đã có, nên khi
-- ghi DUPLICATE sẽ lỗi "Data truncated for column 'status'" và payment đó kẹt ở PENDING.
-- Database tạo mới (kể cả sau `docker compose down -v`) đã có DUPLICATE từ entity, không cần chạy.
-- Idempotent: chạy lại nhiều lần không sao. Không đụng dữ liệu, chỉ mở rộng danh sách giá trị cho phép.
-- =============================================================================
USE airline_payment;

-- 1) Kiểm tra định nghĩa hiện tại của cột
SELECT COLUMN_TYPE AS current_definition
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'airline_payment' AND TABLE_NAME = 'payments' AND COLUMN_NAME = 'status';

-- 2) Thêm DUPLICATE (giữ nguyên các giá trị cũ, vẫn cho NULL như entity)
ALTER TABLE payments MODIFY COLUMN status
    ENUM('CANCELLED', 'DUPLICATE', 'FAILED', 'PENDING', 'PROCESSING', 'REFUNDED', 'SUCCESS') NULL;
