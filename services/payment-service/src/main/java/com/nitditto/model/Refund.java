package com.nitditto.model;

import com.nitditto.entity.BaseEntity;
import com.nitditto.enums.RefundStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Một yêu cầu hoàn tiền cho một payment. Unique theo {@code payment_id}: mỗi payment chỉ hoàn tối đa MỘT lần, nên gọi
 * lặp (retry huỷ booking, hai request song song) không thể hoàn tiền hai lần ở phía chúng ta.
 */
@Entity
@Table(name = "refunds",
    uniqueConstraints = @UniqueConstraint(name = "uk_refund_payment", columnNames = "payment_id"),
    indexes = @Index(name = "idx_refund_status", columnList = "status"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Refund extends BaseEntity {

  @Column(name = "payment_id", columnDefinition = "binary(16)", nullable = false)
  private UUID paymentId;

  @Column(name = "booking_id", columnDefinition = "binary(16)", nullable = false)
  private UUID bookingId;

  @Column(name = "user_id", columnDefinition = "binary(16)", nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private Double amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private RefundStatus status;

  @Column(length = 255)
  private String reason;

  /** vnp_RequestId của lần gọi gần nhất (mỗi lần gọi một id mới; cổng yêu cầu duy nhất trong ngày). */
  @Column(name = "request_id", length = 64)
  private String requestId;

  /** Mã giao dịch hoàn tiền phía cổng (khi SUCCESS). */
  @Column(name = "provider_transaction_no", length = 64)
  private String providerTransactionNo;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "failure_reason", length = 500)
  private String failureReason;

  /**
   * true khi lần gọi cổng gần nhất KHÔNG rõ kết quả (timeout, phản hồi sai chữ ký...): tiền có thể đã được hoàn.
   * Phải kiểm tra ở cổng thanh toán trước khi retry, nên retry yêu cầu xác nhận rõ ràng.
   */
  @Column(name = "outcome_unknown", nullable = false)
  private boolean outcomeUnknown;

  /** Chống hai tiến trình cùng thực thi một refund (lần ghi sau thua với OptimisticLockException). */
  @Version
  private Long version;
}
