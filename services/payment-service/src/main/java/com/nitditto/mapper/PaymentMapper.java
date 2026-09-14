package com.nitditto.mapper;

import com.nitditto.model.Payment;
import com.nitditto.payload.dto.PaymentDTO;

import java.time.ZoneId;

public final class PaymentMapper {

    private PaymentMapper() {
    }

    public static PaymentDTO toDTO(final Payment payment) {
        if (payment == null) {
            return null;
        }

        final PaymentDTO.PaymentDTOBuilder builder = PaymentDTO.builder()
                .id(payment.getId())
                .userId(payment.getUserId())
                .bookingId(payment.getBookingId())
                .amount(payment.getAmount())
                .paymentStatus(payment.getStatus())
                .paymentGateway(payment.getProvider())
                .transactionId(payment.getTransactionId())
                .gatewayPaymentId(payment.getProviderPaymentId())
                .failureReason(payment.getFailureReason());

        if (payment.getPaidAt() != null) {
            builder.completedAt(payment.getPaidAt());
        }
        if (payment.getCreatedAt() != null) {
            builder.createdAt(payment.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        if (payment.getUpdatedAt() != null) {
            builder.updatedAt(payment.getUpdatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        return builder.build();
    }
}
