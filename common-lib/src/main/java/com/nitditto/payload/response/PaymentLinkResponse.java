package com.nitditto.payload.response;

import java.time.Instant;
import java.util.UUID;

import com.nitditto.enums.PaymentStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentLinkResponse {

    private UUID id;
    private String paymentLinkUrl;
    private String paymentLinkId;
    private PaymentStatus status;
    private Instant expiresAt;
    /** Thời điểm tạo giao dịch phía cổng (VNPay: yyyyMMddHHmmss, GMT+7); cần lưu lại để hoàn tiền sau này. */
    private String createDate;
}
