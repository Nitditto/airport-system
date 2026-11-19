package com.nitditto.payload.response;

import com.nitditto.enums.RefundStatus;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResponse {

    private UUID refundId;
    private UUID paymentId;
    private UUID bookingId;
    private Double amount;
    private RefundStatus status;
    /** Lý do thất bại (khi FAILED), để admin xử lý tay. */
    private String failureReason;
    /** Mã giao dịch hoàn tiền phía cổng thanh toán (khi SUCCESS). */
    private String providerTransactionNo;
}
