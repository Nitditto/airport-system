package com.nitditto.enums;

public enum SeatAvailabilityStatus {
    AVAILABLE,
    BOOKED,
    OCCUPIED,
    BLOCKED,
    // Chỉ xuất hiện trong response (ghế đang được người khác giữ chỗ tạm thời), KHÔNG lưu vào DB:
    // việc giữ chỗ nằm ở SeatInstance.heldBy/heldUntil.
    HELD
}
