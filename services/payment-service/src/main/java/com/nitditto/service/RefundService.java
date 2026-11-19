package com.nitditto.service;

import com.nitditto.enums.RefundStatus;
import com.nitditto.payload.request.RefundRequest;
import com.nitditto.payload.response.RefundResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RefundService {

  /**
   * Ghi nhận một cách BỀN VỮNG yêu cầu hoàn tiền cho payment thành công của booking, rồi thực hiện ngay với cổng thanh
   * toán. Idempotent theo payment: gọi lại trả về yêu cầu đã có (không hoàn lần hai).
   *
   * <p>Kết quả cổng thất bại KHÔNG ném lỗi: trả về status FAILED để người gọi vẫn hoàn tất việc của mình (vd. huỷ
   * booking) và admin xử lý tay. Chỉ ném lỗi khi yêu cầu không hợp lệ (không có payment thành công, số tiền vượt
   * số đã thanh toán, đã có yêu cầu với số tiền khác).
   */
  RefundResponse requestRefund(RefundRequest request);

  /**
   * Thực hiện lại một refund FAILED. Nếu lần trước không rõ kết quả (tiền có thể đã được hoàn), phải xác nhận rõ ràng
   * {@code confirmNotRefunded=true} sau khi đã kiểm tra ở cổng thanh toán.
   */
  RefundResponse retry(UUID refundId, boolean confirmNotRefunded);

  Page<RefundResponse> list(RefundStatus status, Pageable pageable);
}
