package com.nitditto.enums;

/** Trạng thái một yêu cầu hoàn tiền (payment-service). FAILED cần admin kiểm tra ở cổng thanh toán rồi retry thủ công. */
public enum RefundStatus {
    PENDING, SUCCESS, FAILED
}
