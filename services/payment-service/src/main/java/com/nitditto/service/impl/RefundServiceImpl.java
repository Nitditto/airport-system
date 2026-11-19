package com.nitditto.service.impl;

import com.nitditto.enums.PaymentGateway;
import com.nitditto.enums.PaymentStatus;
import com.nitditto.enums.RefundStatus;
import com.nitditto.exception.BusinessException;
import com.nitditto.exception.ResourceNotFoundException;
import com.nitditto.model.Payment;
import com.nitditto.model.Refund;
import com.nitditto.payload.request.RefundRequest;
import com.nitditto.payload.response.RefundResponse;
import com.nitditto.repository.PaymentRepository;
import com.nitditto.repository.RefundRepository;
import com.nitditto.service.RefundService;
import com.nitditto.service.gateway.VNPayRefundService;
import com.nitditto.service.gateway.VNPayRefundService.Outcome;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Hoàn tiền theo 3 bước TÁCH transaction, vì lời gọi cổng là HTTP chậm không được nằm trong transaction DB:
 * (1) ghi nhận yêu cầu PENDING (commit) → (2) gọi cổng → (3) ghi kết quả (commit).
 * Nếu tiến trình chết giữa (2) và (3) bản ghi còn PENDING mà tiền có thể đã hoàn: PENDING không bao giờ tự chạy lại,
 * admin đối chiếu rồi mới retry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

  private static final DateTimeFormatter VNP_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
  private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

  private final PaymentRepository paymentRepository;
  private final RefundRepository refundRepository;
  private final VNPayRefundService vnPayRefundService;
  private final TransactionTemplate transactionTemplate;

  @Override
  public RefundResponse requestRefund(final RefundRequest request) {
    final Intake intake = this.recordRequest(request);
    if (!intake.created()) {
      // Đã có yêu cầu cho payment này: trả lại nguyên trạng, không gọi cổng lần nữa.
      return toResponse(intake.refund());
    }
    return this.execute(intake.refund().getId(), request.getRequestedBy());
  }

  @Override
  public RefundResponse retry(final UUID refundId, final boolean confirmNotRefunded) {
    final Refund prepared = this.transactionTemplate.execute(status -> {
      final Refund refund = this.refundRepository.findById(refundId)
          .orElseThrow(() -> new ResourceNotFoundException("Refund", "id", refundId));
      if (refund.getStatus() == RefundStatus.SUCCESS) {
        return refund;
      }
      // FAILED không rõ kết quả, hoặc PENDING bị bỏ dở (tiến trình chết giữa lúc gọi cổng): tiền có thể đã hoàn.
      if ((refund.isOutcomeUnknown() || refund.getStatus() == RefundStatus.PENDING) && !confirmNotRefunded) {
        throw new BusinessException("The previous attempt may have refunded the customer. Check the VNPay merchant "
            + "portal, then retry with confirmNotRefunded=true");
      }
      refund.setStatus(RefundStatus.PENDING);
      return this.refundRepository.save(refund);
    });
    if (prepared.getStatus() == RefundStatus.SUCCESS) {
      return toResponse(prepared);
    }
    return this.execute(refundId, "admin-retry");
  }

  @Override
  public Page<RefundResponse> list(final RefundStatus status, final Pageable pageable) {
    final Page<Refund> page = status == null ? this.refundRepository.findAll(pageable)
        : this.refundRepository.findByStatus(status, pageable);
    return page.map(RefundServiceImpl::toResponse);
  }

  // ---- bước 1: ghi nhận ----

  private record Intake(Refund refund, boolean created) {
  }

  private Intake recordRequest(final RefundRequest request) {
    try {
      return this.transactionTemplate.execute(status -> {
        final Payment payment = this.paidPaymentOf(request.getBookingId());
        final Refund existing = this.refundRepository.findByPaymentId(payment.getId()).orElse(null);
        if (existing != null) {
          if (Math.abs(existing.getAmount() - request.getAmount()) > 0.0001) {
            throw new BusinessException("A refund of a different amount was already requested for this booking");
          }
          return new Intake(existing, false);
        }
        if (payment.getAmount() == null || request.getAmount() > payment.getAmount()) {
          throw new BusinessException("Refund amount exceeds the amount paid");
        }
        final Refund created = this.refundRepository.saveAndFlush(Refund.builder()
            .paymentId(payment.getId()).bookingId(payment.getBookingId()).userId(payment.getUserId())
            .amount(request.getAmount()).reason(request.getReason()).status(RefundStatus.PENDING).attempts(0)
            .build());
        return new Intake(created, true);
      });
    } catch (final DataIntegrityViolationException race) {
      // Hai request song song cùng payment: bên thua đọc lại bản ghi của bên thắng.
      final Refund existing = this.transactionTemplate.execute(status -> this.refundRepository
          .findByPaymentId(this.paidPaymentOf(request.getBookingId()).getId()).orElseThrow(() -> race));
      return new Intake(existing, false);
    }
  }

  private Payment paidPaymentOf(final UUID bookingId) {
    final List<Payment> paid = this.paymentRepository.findByBookingIdAndStatusIn(bookingId,
        List.of(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED));
    if (paid.isEmpty()) {
      throw new BusinessException("Booking has no successful payment to refund");
    }
    return paid.get(0);
  }

  // ---- bước 2 + 3: gọi cổng rồi ghi kết quả ----

  private record Prepared(UUID paymentId, PaymentGateway provider, VNPayRefundService.Command command) {
  }

  private RefundResponse execute(final UUID refundId, final String requestedBy) {
    final Prepared prepared;
    try {
      prepared = this.transactionTemplate.execute(status -> this.prepare(refundId, requestedBy));
    } catch (final ObjectOptimisticLockingFailureException lost) {
      // Tiến trình khác đang thực hiện cùng refund: không gọi cổng lần nữa.
      return this.current(refundId);
    }
    if (prepared == null) {
      return this.current(refundId);
    }

    final VNPayRefundService.Result result = prepared.provider() == PaymentGateway.VNPAY
        ? this.vnPayRefundService.refund(prepared.command())
        : new VNPayRefundService.Result(Outcome.REJECTED, null,
            "Refund is not supported for provider " + prepared.provider() + ": refund manually", null);

    return this.transactionTemplate.execute(status -> this.record(refundId, prepared.paymentId(), result));
  }

  private Prepared prepare(final UUID refundId, final String requestedBy) {
    final Refund refund = this.refundRepository.findById(refundId)
        .orElseThrow(() -> new ResourceNotFoundException("Refund", "id", refundId));
    if (refund.getStatus() != RefundStatus.PENDING) {
      return null;
    }
    final Payment payment = this.paymentRepository.findById(refund.getPaymentId())
        .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", refund.getPaymentId()));

    final String requestId = UUID.randomUUID().toString().replace("-", "");
    refund.setRequestId(requestId);
    refund.setAttempts(refund.getAttempts() + 1);
    this.refundRepository.saveAndFlush(refund);

    final boolean full = payment.getAmount() != null && Math.abs(payment.getAmount() - refund.getAmount()) < 0.0001;
    final String orderInfo = "Refund booking " + refund.getBookingId();
    final VNPayRefundService.Command command = new VNPayRefundService.Command(payment.getId().toString(),
        refund.getAmount(), full, payment.getTransactionId(), this.originalTransactionDate(payment), orderInfo,
        requestedBy == null || requestedBy.isBlank() ? "airline-system" : requestedBy, requestId);
    return new Prepared(payment.getId(), payment.getProvider(), command);
  }

  private RefundResponse record(final UUID refundId, final UUID paymentId, final VNPayRefundService.Result result) {
    final Refund refund = this.refundRepository.findById(refundId).orElseThrow();
    switch (result.outcome()) {
      case SUCCESS -> {
        refund.setStatus(RefundStatus.SUCCESS);
        refund.setProviderTransactionNo(result.transactionNo());
        refund.setFailureReason(null);
        refund.setOutcomeUnknown(false);
        final Payment payment = this.paymentRepository.findById(paymentId).orElseThrow();
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundId(result.transactionNo());
        this.paymentRepository.save(payment);
        log.info("Refund {} succeeded for booking {} (provider transaction {})", refundId, refund.getBookingId(),
            result.transactionNo());
      }
      case REJECTED -> {
        refund.setStatus(RefundStatus.FAILED);
        refund.setOutcomeUnknown(false);
        refund.setFailureReason(truncate("Rejected" + codeOf(result) + ": " + result.message()));
        log.warn("Refund {} rejected for booking {}: {}", refundId, refund.getBookingId(), result.message());
      }
      case UNKNOWN -> {
        refund.setStatus(RefundStatus.FAILED);
        refund.setOutcomeUnknown(true);
        refund.setFailureReason(truncate("UNKNOWN OUTCOME, verify in the VNPay portal before retrying: "
            + result.message()));
        log.error("Refund {} has an UNKNOWN outcome for booking {}: {}", refundId, refund.getBookingId(),
            result.message());
      }
    }
    return toResponse(this.refundRepository.save(refund));
  }

  private RefundResponse current(final UUID refundId) {
    return this.transactionTemplate.execute(status -> toResponse(this.refundRepository.findById(refundId)
        .orElseThrow(() -> new ResourceNotFoundException("Refund", "id", refundId))));
  }

  /** vnp_CreateDate của giao dịch gốc; payment cũ (trước khi lưu cột này) suy ra từ createdAt, có thể lệch vài giây. */
  private String originalTransactionDate(final Payment payment) {
    if (payment.getProviderCreateDate() != null && !payment.getProviderCreateDate().isBlank()) {
      return payment.getProviderCreateDate();
    }
    log.warn("Payment {} has no stored VNPay create date; deriving it from createdAt (the gateway may reject it)",
        payment.getId());
    return VNP_DATE.format(payment.getCreatedAt().atZone(VIETNAM));
  }

  private static String codeOf(final VNPayRefundService.Result result) {
    return result.responseCode() == null ? "" : " (" + result.responseCode() + ")";
  }

  private static String truncate(final String value) {
    return value != null && value.length() > 500 ? value.substring(0, 500) : value;
  }

  static RefundResponse toResponse(final Refund refund) {
    return RefundResponse.builder().refundId(refund.getId()).paymentId(refund.getPaymentId())
        .bookingId(refund.getBookingId()).amount(refund.getAmount()).status(refund.getStatus())
        .failureReason(refund.getFailureReason()).providerTransactionNo(refund.getProviderTransactionNo()).build();
  }
}
