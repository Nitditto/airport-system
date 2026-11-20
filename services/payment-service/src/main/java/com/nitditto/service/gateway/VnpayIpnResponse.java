package com.nitditto.service.gateway;

/**
 * Phản hồi cho lệnh gọi IPN của VNPay. VNPay đọc đúng hai trường {@code RspCode}/{@code Message} ở gốc JSON (không có
 * lớp bọc {@code ApiResponse}), nên đây là ngoại lệ có chủ ý của quy ước response chung.
 *
 * <p>Mã theo tài liệu IPN của VNPay: 00 thành công; 01 không thấy đơn; 02 đơn đã được xác nhận trước đó; 04 sai số tiền;
 * 97 sai chữ ký; 99 lỗi khác (VNPay sẽ gọi lại).
 */
public record VnpayIpnResponse(String RspCode, String Message) {

    public static final VnpayIpnResponse CONFIRMED = new VnpayIpnResponse("00", "Confirm Success");
    public static final VnpayIpnResponse ORDER_NOT_FOUND = new VnpayIpnResponse("01", "Order not found");
    public static final VnpayIpnResponse ALREADY_CONFIRMED = new VnpayIpnResponse("02", "Order already confirmed");
    public static final VnpayIpnResponse INVALID_AMOUNT = new VnpayIpnResponse("04", "Invalid amount");
    public static final VnpayIpnResponse INVALID_SIGNATURE = new VnpayIpnResponse("97", "Invalid signature");
    public static final VnpayIpnResponse UNKNOWN_ERROR = new VnpayIpnResponse("99", "Unknown error");
}
