package com.nitditto.service;

import com.nitditto.enums.RefundStatus;
import com.nitditto.model.Booking;
import java.util.UUID;

/**
 * Hoàn tiền khi huỷ một booking ĐÃ XÁC NHẬN: quyết định số tiền theo chính sách rồi nhờ payment-service ghi nhận và
 * thực hiện. Gọi TRƯỚC khi đổi trạng thái booking: nếu payment-service không ghi nhận được yêu cầu (lỗi/không phản hồi)
 * thì ném lỗi và booking giữ nguyên để người dùng thử lại; ghi nhận rồi mới huỷ nên yêu cầu hoàn tiền không bao giờ
 * bị mất. Yêu cầu hoàn là idempotent theo payment nên thử lại sau khi lỡ commit huỷ vẫn an toàn.
 *
 * <p>Không chạy trong transaction DB (gọi HTTP sang pricing/flight/payment).
 */
public interface BookingRefundService {

  /**
   * Kết quả hoàn tiền của một lần huỷ. {@code status} null = không có yêu cầu hoàn tiền (vé không đủ điều kiện hoặc
   * không có thanh toán); {@code amount} là số tiền đã yêu cầu hoàn.
   */
  record Outcome(Double amount, RefundStatus status, String note) {

    public static Outcome notApplicable() {
      return new Outcome(null, null, null);
    }
  }

  Outcome refundForCancellation(Booking booking, UUID requesterUserId);
}
