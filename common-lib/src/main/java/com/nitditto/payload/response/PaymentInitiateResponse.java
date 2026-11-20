package com.nitditto.payload.response;

import com.nitditto.enums.PaymentGateway;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInitiateResponse {

    private UUID paymentId;
    private PaymentGateway paymentGateway;
    private String transactionId;
    private Double amount;

    private String providerReferenceId;

    private String description;
    private String checkoutUrl;

    private String message;
    private Boolean success;

    /**
     * Cổng thanh toán thu bằng loại tiền KHÁC VND (Razorpay thu INR): số tiền khách thật sự phải trả và tỷ giá đã dùng, để
     * giao diện hiển thị rõ. Null khi cổng thu đúng VND (VNPay). {@code amount} luôn là số tiền VND của booking.
     */
    private String chargeCurrency;
    /** Số tiền khách trả theo {@code chargeCurrency} (đơn vị chính, ví dụ rupee). */
    private Double chargeAmount;
    /** Số VND cho 1 đơn vị {@code chargeCurrency} đã dùng để quy đổi. */
    private Double exchangeRate;
    /** Biên an toàn (%) đã cộng vào số tiền thu để bù trượt giá; {@code chargeAmount} đã bao gồm biên này. */
    private Double exchangeRateMarginPercent;
    /** Nguồn tỷ giá (ví dụ "Vietcombank transfer ..."). */
    private String exchangeRateSource;
    /** Link thanh toán hết hạn lúc này (cổng có hạn, ví dụ VNPay); null nếu link không có hạn. Giao diện dùng để đếm ngược. */
    private java.time.Instant expiresAt;

}
