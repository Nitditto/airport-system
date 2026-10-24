-- =============================================================================
-- Mở rộng flight_schedules.operating_days từ VARCHAR(32) lên VARCHAR(80)
-- (feature/flight-public-search). Lịch bay hằng ngày ("MONDAY,TUESDAY,...") dài hơn 32 ký tự
-- nên bị lỗi "Data too long".
--
-- Chạy THỦ CÔNG trên database airline_flight đã tồn tại; KHÔNG được mount vào initdb.
-- ddl-auto: update chỉ tạo bảng/cột mới, không mở rộng cột đã có.
-- Database tạo mới (kể cả sau `docker compose down -v`) đã có VARCHAR(80) từ entity, không cần chạy.
-- Idempotent: chạy lại nhiều lần không sao.
-- =============================================================================
USE airline_flight;

-- 1) Kiểm tra độ dài hiện tại
SELECT CHARACTER_MAXIMUM_LENGTH AS current_length
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'airline_flight' AND TABLE_NAME = 'flight_schedules' AND COLUMN_NAME = 'operating_days';

-- 2) Mở rộng cột (giữ nguyên NULL như entity)
ALTER TABLE flight_schedules MODIFY COLUMN operating_days VARCHAR(80) NULL;
