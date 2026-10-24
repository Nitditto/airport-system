package com.nitditto.event;

import java.util.UUID;

/** Phát ra khi một payment chuyển sang SUCCESS; booking-service được báo SAU KHI transaction commit. */
public record PaymentSucceededEvent(UUID paymentId, UUID bookingId) {
}
