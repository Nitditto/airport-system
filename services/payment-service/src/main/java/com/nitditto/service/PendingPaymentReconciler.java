package com.nitditto.service;

import com.nitditto.enums.PaymentGateway;
import com.nitditto.enums.PaymentStatus;
import com.nitditto.model.Payment;
import com.nitditto.repository.PaymentRepository;
import com.nitditto.service.gateway.VNPayQueryService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Đối soát payment VNPay còn {@code PENDING} với cổng thanh toán: bù cho trường hợp khách trả tiền xong nhưng cả trình duyệt
 * lẫn IPN đều không báo về (đóng tab, IPN chưa cấu hình/không tới được). Chỉ HỎI cổng, không bao giờ tự đoán kết quả.
 *
 * <p>Gọi cổng NGOÀI transaction DB (CLAUDE.md §8.5); mỗi payment áp kết quả trong transaction riêng
 * ({@link PaymentService#applyVnpayQuery}) nên một payment lỗi không chặn các payment khác.
 *
 * <p>Chỉ chạy một instance payment-service thì an toàn; nhiều instance cùng chạy thì có thể cùng hỏi một payment, và
 * ràng buộc unique {@code paid_booking_id} chặn việc ghi thành công hai lần.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payment.reconcile.enabled", havingValue = "true", matchIfMissing = true)
public class PendingPaymentReconciler {

  private static final DateTimeFormatter VNP_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
  private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

  private final PaymentRepository paymentRepository;
  private final PaymentService paymentService;
  private final VNPayQueryService queryService;
  private final Clock clock;
  private final Duration minAge;
  private final Duration maxAge;
  private final Duration requeryInterval;
  private final int batchSize;

  public PendingPaymentReconciler(final PaymentRepository paymentRepository, final PaymentService paymentService,
      final VNPayQueryService queryService, final Clock clock,
      @Value("${payment.reconcile.min-age-minutes:2}") final long minAgeMinutes,
      @Value("${payment.reconcile.max-age-hours:48}") final long maxAgeHours,
      @Value("${payment.reconcile.requery-interval-minutes:5}") final long requeryMinutes,
      @Value("${payment.reconcile.batch-size:50}") final int batchSize) {
    this.paymentRepository = paymentRepository;
    this.paymentService = paymentService;
    this.queryService = queryService;
    this.clock = clock;
    this.minAge = Duration.ofMinutes(minAgeMinutes);
    this.maxAge = Duration.ofHours(maxAgeHours);
    this.requeryInterval = Duration.ofMinutes(requeryMinutes);
    this.batchSize = batchSize;
  }

  @Scheduled(fixedDelayString = "${payment.reconcile.interval-ms:120000}",
      initialDelayString = "${payment.reconcile.initial-delay-ms:60000}")
  public void run() {
    try {
      final int checked = this.reconcileOnce();
      if (checked > 0) {
        log.info("Reconciled {} pending VNPay payment(s)", checked);
      }
    } catch (final RuntimeException ex) {
      log.error("Pending payment reconciliation failed", ex);
    }
  }

  /** Một lượt đối soát; trả số payment đã hỏi cổng. */
  public int reconcileOnce() {
    if (!this.queryService.isConfigured()) {
      return 0;
    }
    final Instant now = this.clock.instant();
    final List<Payment> due = this.paymentRepository.findReconcilable(PaymentGateway.VNPAY, PaymentStatus.PENDING,
        now.minus(this.maxAge), now.minus(this.minAge), now.minus(this.requeryInterval),
        PageRequest.of(0, this.batchSize));
    int checked = 0;
    for (final Payment payment : due) {
      try {
        final VNPayQueryService.Result result = this.queryService.query(this.commandFor(payment));
        this.paymentService.applyVnpayQuery(payment.getId(), result);
        checked++;
      } catch (final RuntimeException ex) {
        log.error("Reconciling payment {} failed: {}", payment.getId(), ex.toString());
      }
    }
    return checked;
  }

  private VNPayQueryService.Command commandFor(final Payment payment) {
    final String date = payment.getProviderCreateDate() != null && !payment.getProviderCreateDate().isBlank()
        ? payment.getProviderCreateDate()
        : VNP_DATE.format(payment.getCreatedAt().atZone(VIETNAM));
    final String requestId = UUID.randomUUID().toString().replace("-", "");
    return new VNPayQueryService.Command(payment.getId().toString(), date,
        "Query payment for booking " + payment.getBookingId(), requestId);
  }
}
