package com.nitditto.service.gateway;

/**
 * Quy đổi số tiền VND của booking sang loại tiền mà cổng thanh toán nước ngoài thu (Razorpay thu INR). Giá vé luôn tính
 * bằng VND ở toàn hệ thống; chỉ lúc tạo link thanh toán mới quy đổi, và kết quả quy đổi được lưu cùng payment.
 */
public interface ExchangeRateService {

    /**
     * @param currency   mã tiền tệ đích, hiện chỉ hỗ trợ {@code INR}
     * @param amountVnd  số tiền VND cần thu
     * @throws com.nitditto.exception.BusinessException khi không có tỷ giá đủ tin cậy (không bao giờ đoán tỷ giá)
     */
    Quote quoteFromVnd(String currency, double amountVnd);

    /**
     * @param currency    mã tiền tệ khách thật sự trả
     * @param amountMinor số tiền theo đơn vị nhỏ nhất (INR: paise), làm tròn LÊN để bên nhận không bị thiệt
     * @param rate          số VND cho 1 đơn vị {@code currency} (tỷ giá thị trường, CHƯA cộng biên)
     * @param marginPercent biên an toàn (%) đã cộng vào số tiền thu để bù trượt giá giữa lúc báo giá và lúc quyết toán
     * @param source        nguồn tỷ giá, để ghi vết
     */
    record Quote(String currency, long amountMinor, double rate, double marginPercent, String source) {

        /** Số tiền theo đơn vị chính (INR: rupee). */
        public double amountMajor() {
            return this.amountMinor / 100.0;
        }
    }
}
