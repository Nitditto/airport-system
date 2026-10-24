package com.nitditto.payload.request;

import com.nitditto.enums.PaymentGateway;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentVerifyRequest {

    private PaymentGateway gateway;

    private String razorpayPaymentId;

    private String stripePaymentIntentId;

    private String vnpTxnRef;

    private String vnpResponseCode;

    private String vnpTransactionNo;

    private String vnpSecureHash;

    private Map<String, String> vnpParams;
}
