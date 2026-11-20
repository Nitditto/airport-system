package com.nitditto.service.gateway;

import com.nitditto.service.gateway.VNPayRefundService.Outcome;
import com.nitditto.service.gateway.VNPayRefundService.Result;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

/**
 * Gọi API hoàn tiền của Razorpay. Cùng nguyên tắc với {@link VNPayRefundService} (dùng chung kiểu kết quả): KHÔNG bao giờ
 * ném lỗi ra ngoài, và với tiền thì "không biết" phải khác "bị từ chối".
 *
 * <ul>
 *   <li>{@code SUCCESS}: Razorpay tạo được refund (trạng thái {@code pending} hoặc {@code processed}: tiền sẽ về khách).
 *   <li>{@code REJECTED}: Razorpay trả lỗi nghiệp vụ {@code BAD_REQUEST_ERROR} (vượt số đã thu, payment chưa capture...),
 *       hoặc bất kỳ mã HTTP 4xx nào (không tìm thấy payment, sai khoá, quá tần suất...: yêu cầu CHƯA được xử lý), hoặc chưa
 *       cấu hình: chắc chắn CHƯA hoàn. Đã kiểm chứng với Razorpay thật: payment không tồn tại trả "Status Code: 404".
 *   <li>{@code UNKNOWN}: mọi lỗi còn lại (mạng, timeout, lỗi cổng, phản hồi không đọc được): tiền CÓ THỂ đã được hoàn,
 *       không được tự retry.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RazorpayRefundService {

  /** {@code amountMinor} theo đơn vị nhỏ nhất của loại tiền ĐÃ THU (paise), không phải VND. */
  public record Command(String razorpayPaymentId, long amountMinor, String receipt) {
  }

  // SDK Razorpay đưa lỗi vào message: "BAD_REQUEST_ERROR:<mô tả>" khi có thân lỗi, hoặc "Status Code: <mã HTTP>" khi không.
  private static final java.util.regex.Pattern NOT_PROCESSED =
      java.util.regex.Pattern.compile("^(BAD_REQUEST_ERROR|Status Code: 4\\d\\d)");

  private final RazorpayService razorpayService;

  public Result refund(final Command command) {
    if (!this.razorpayService.isConfigured()) {
      return new Result(Outcome.REJECTED, null, "Razorpay is not configured", null);
    }
    if (command.razorpayPaymentId() == null || command.razorpayPaymentId().isBlank()) {
      return new Result(Outcome.REJECTED, null, "The payment has no Razorpay payment id to refund", null);
    }
    final JSONObject response;
    try {
      response = this.razorpayService.refundPayment(command.razorpayPaymentId(), command.amountMinor(),
          command.receipt());
    } catch (final RazorpayException ex) {
      final String message = ex.getMessage() == null ? "" : ex.getMessage();
      if (NOT_PROCESSED.matcher(message).find()) {
        log.warn("Razorpay rejected the refund of {} (receipt {}): {}", command.razorpayPaymentId(),
            command.receipt(), message);
        return new Result(Outcome.REJECTED, "REQUEST_NOT_PROCESSED", message, null);
      }
      log.error("Razorpay refund of {} (receipt {}) has an unknown outcome: {}", command.razorpayPaymentId(),
          command.receipt(), message);
      return new Result(Outcome.UNKNOWN, null, "Could not complete the Razorpay call: " + message, null);
    } catch (final RuntimeException ex) {
      log.error("Razorpay refund of {} (receipt {}) failed unexpectedly: {}", command.razorpayPaymentId(),
          command.receipt(), ex.toString());
      return new Result(Outcome.UNKNOWN, null, "Could not complete the Razorpay call: " + ex.getClass().getSimpleName(),
          null);
    }
    final String id = response == null ? "" : response.optString("id", "");
    final String status = response == null ? "" : response.optString("status", "");
    if (id.isBlank()) {
      return new Result(Outcome.UNKNOWN, null, "Razorpay answered without a refund id", null);
    }
    if ("failed".equalsIgnoreCase(status)) {
      return new Result(Outcome.UNKNOWN, status, "Razorpay created the refund " + id + " but reports it failed", id);
    }
    return new Result(Outcome.SUCCESS, status, "Refund " + status, id);
  }
}
