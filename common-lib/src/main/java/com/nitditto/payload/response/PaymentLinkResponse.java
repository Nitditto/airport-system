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
    private String paymentUrl;
    private PaymentStatus status;
    private Instant expiresAt;
}
