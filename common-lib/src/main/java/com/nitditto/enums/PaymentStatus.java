package com.nitditto.enums;

public enum PaymentStatus {
    PENDING, SUCCESS, FAILED, CANCELLED, REFUNDED, PROCESSING,
    /** Khách bị thu tiền lần thứ hai cho một booking đã thanh toán: cần hoàn tay, không phải payment hợp lệ của booking. */
    DUPLICATE
}
