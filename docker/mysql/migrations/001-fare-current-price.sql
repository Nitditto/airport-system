-- =============================================================================
-- Vá dữ liệu Fare tạo trước khi sửa FareMapper (fix/pricing-fare-total).
-- Chạy THỦ CÔNG trên database airline_pricing; KHÔNG được mount vào initdb.
--
-- Trước đây FareMapper.toEntity lưu current_price = base_fare + taxed_and_fees + airline_fees
-- khi request không gửi currentPrice, trong khi Fare.getTotalPrice() lại cộng thêm current_price
-- => tổng tiền bị tính gấp đôi.
--
-- Chỉ reset các dòng khớp đúng mẫu đó. Dòng có current_price là điều chỉnh/khuyến mãi thật
-- (khác tổng trên) được giữ nguyên, nên KHÔNG dùng "UPDATE fares SET current_price = 0" cho mọi dòng.
-- Ngoại lệ không phân biệt được: dòng mà người dùng chủ động nhập current_price đúng bằng tổng đó.
-- =============================================================================
USE airline_pricing;

-- 1) Xem trước các dòng sẽ bị ảnh hưởng
SELECT BIN_TO_UUID(id) AS id, name, base_fare, taxed_and_fees, airline_fees, current_price
FROM fares
WHERE ABS(current_price - (base_fare + COALESCE(taxed_and_fees, 0) + COALESCE(airline_fees, 0))) < 0.01
  AND current_price <> 0;

-- 2) Áp dụng (bỏ comment sau khi đã kiểm tra bước 1; nên backup bảng fares trước)
-- START TRANSACTION;
-- UPDATE fares
-- SET current_price = 0
-- WHERE ABS(current_price - (base_fare + COALESCE(taxed_and_fees, 0) + COALESCE(airline_fees, 0))) < 0.01
--   AND current_price <> 0;
-- COMMIT;
