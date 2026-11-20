package com.nitditto.service;

import com.nitditto.payload.response.FareRulesResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * Chính sách hoàn vé khi khách huỷ booking ĐÃ THANH TOÁN, tính từ {@code FareRules} của fare đã mua (cùng các thông số
 * frontend đang hiển thị cho khách: có hoàn không, phí huỷ, hạn hoàn trước giờ bay).
 *
 * <p>Quy tắc (khớp quyết định sản phẩm: vé không đủ điều kiện thì VẪN cho huỷ nhưng KHÔNG hoàn tiền):
 * <ul>
 *   <li>Fare không có rules, hoặc {@code isRefundable} khác true: không hoàn.
 *   <li>Chuyến đã khởi hành: không hoàn.
 *   <li>Quá hạn hoàn ({@code refundDeadlineDays} ngày trước giờ bay): không hoàn. Không có hạn = không giới hạn.
 *   <li>Còn lại: hoàn {@code đã thanh toán − cancellationFee} (phí huỷ là SỐ TIỀN cố định, cùng đơn vị tiền với fare;
 *       null = 0), làm tròn về số nguyên đơn vị tiền; phí lớn hơn số đã trả thì không hoàn.
 * </ul>
 */
public final class RefundPolicy {

  /** Kết quả quyết định: {@code amount} = 0 nghĩa là không hoàn; {@code note} giải thích cho khách. */
  public record Decision(double amount, String note) {

    public boolean refundable() {
      return this.amount > 0;
    }
  }

  private RefundPolicy() {
  }

  public static Decision evaluate(final FareRulesResponse rules, final double amountPaid, final Instant departure,
      final Instant now) {
    if (rules == null) {
      return none("This fare has no refund rules, so it is not refundable");
    }
    if (!Boolean.TRUE.equals(rules.getIsRefundable())) {
      return none("This fare is not refundable");
    }
    if (departure != null && !now.isBefore(departure)) {
      return none("The flight has already departed");
    }
    final Integer deadlineDays = rules.getRefundDeadlineDays();
    if (deadlineDays != null && deadlineDays > 0) {
      if (departure == null) {
        return none("The refund deadline cannot be verified because the departure time is unknown");
      }
      if (now.isAfter(departure.minus(Duration.ofDays(deadlineDays)))) {
        return none("The refund deadline has passed (" + deadlineDays + " days before departure)");
      }
    }
    final double fee = rules.getCancellationFee() == null ? 0.0 : Math.max(0.0, rules.getCancellationFee());
    final double refund = Math.round(amountPaid - fee);
    if (refund <= 0) {
      return none("The cancellation fee covers the whole amount paid");
    }
    return new Decision(refund, fee > 0
        ? "Refunded after a cancellation fee of " + Math.round(fee)
        : "Refunded in full");
  }

  private static Decision none(final String note) {
    return new Decision(0.0, note);
  }
}
