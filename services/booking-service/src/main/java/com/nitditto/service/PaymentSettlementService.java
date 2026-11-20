package com.nitditto.service;

import com.nitditto.enums.BookingStatus;
import java.util.UUID;

/**
 * Xử lý kết quả "payment thành công" của một booking: xác nhận booking, hoặc nếu không thể (booking đã huỷ/hết hạn trước khi
 * tiền về, ghế đã mất) thì HOÀN TIỀN cho khách. Không chạy trong transaction DB: {@code confirmPayment} có transaction riêng
 * và việc hoàn tiền gọi HTTP sang payment-service (CLAUDE.md §8.5).
 *
 * <p>Idempotent: gọi lặp (cả đường Kafka lẫn HTTP của payment-service) không hoàn tiền hai lần, vì payment-service chỉ nhận
 * một refund cho mỗi payment.
 */
public interface PaymentSettlementService {

  /** Trả trạng thái booking sau khi xử lý (CONFIRMED, hoặc trạng thái hiện tại nếu khoản thanh toán đã được hoàn). */
  BookingStatus settle(UUID bookingId);
}
