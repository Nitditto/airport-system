package com.nitditto.payload.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Yêu cầu hoàn tiền cho thanh toán thành công của một booking. Chỉ booking-service gọi (qua Feign, không qua gateway);
 * số tiền do booking-service quyết định theo chính sách hoàn vé (FareRules), payment-service chỉ kiểm tra không vượt số
 * đã thanh toán.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest {

    @NotNull(message = "Booking id is required")
    private UUID bookingId;

    @NotNull(message = "Refund amount is required")
    @Positive(message = "Refund amount must be positive")
    private Double amount;

    @Size(max = 255, message = "Reason must not exceed 255 characters")
    private String reason;

    /** Ai yêu cầu (chỉ để ghi vết/vnp_CreateBy, KHÔNG dùng để quyết định quyền). */
    @Size(max = 64, message = "RequestedBy must not exceed 64 characters")
    private String requestedBy;
}
