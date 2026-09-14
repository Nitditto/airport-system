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

}
