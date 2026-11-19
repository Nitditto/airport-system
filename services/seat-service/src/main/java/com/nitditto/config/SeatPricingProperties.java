package com.nitditto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Phụ thu chọn chỗ mặc định theo loại ghế (seat.pricing.* trong application.yaml). Đây là phí chọn chỗ thuộc
 * seat-service, KHÔNG phải giá vé: giá vé lấy từ pricing-service.
 */
@ConfigurationProperties(prefix = "seat.pricing")
public record SeatPricingProperties(double defaultWindowSurcharge, double defaultAisleSurcharge) {
}
