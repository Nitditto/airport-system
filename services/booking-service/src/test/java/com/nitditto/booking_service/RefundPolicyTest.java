package com.nitditto.booking_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nitditto.payload.response.FareRulesResponse;
import com.nitditto.service.RefundPolicy;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefundPolicyTest {

  private static final Instant NOW = Instant.parse("2026-11-20T03:00:00Z");
  private static final double PAID = 1_150_000.0;

  private static Instant inDays(final long days) {
    return NOW.plus(Duration.ofDays(days));
  }

  private static FareRulesResponse rules(final Boolean refundable, final Double fee, final Integer deadlineDays) {
    final FareRulesResponse rules = new FareRulesResponse();
    rules.setIsRefundable(refundable);
    rules.setCancellationFee(fee);
    rules.setRefundDeadlineDays(deadlineDays);
    return rules;
  }

  @Test
  void fareWithoutRules_isNotRefundable() {
    final RefundPolicy.Decision decision = RefundPolicy.evaluate(null, PAID, inDays(10), NOW);

    assertThat(decision.refundable()).isFalse();
    assertThat(decision.amount()).isZero();
    assertThat(decision.note()).contains("no refund rules");
  }

  @Test
  void nonRefundableFare_getsNothing_whetherFlagIsFalseOrMissing() {
    assertThat(RefundPolicy.evaluate(rules(false, 0.0, null), PAID, inDays(10), NOW).refundable()).isFalse();
    assertThat(RefundPolicy.evaluate(rules(null, 0.0, null), PAID, inDays(10), NOW).refundable()).isFalse();
  }

  @Test
  void refundableWithoutFeeOrDeadline_refundsEverythingPaid() {
    final RefundPolicy.Decision decision = RefundPolicy.evaluate(rules(true, null, null), PAID, inDays(1), NOW);

    assertThat(decision.amount()).isEqualTo(PAID);
    assertThat(decision.note()).isEqualTo("Refunded in full");
  }

  @Test
  void cancellationFee_isAFixedAmountDeductedFromWhatWasPaid() {
    final RefundPolicy.Decision decision = RefundPolicy.evaluate(rules(true, 100_000.0, null), PAID, inDays(10), NOW);

    assertThat(decision.amount()).isEqualTo(1_050_000.0);
    assertThat(decision.note()).contains("cancellation fee of 100000");
  }

  @Test
  void feeThatCoversTheWholeAmount_meansNoRefund() {
    assertThat(RefundPolicy.evaluate(rules(true, PAID, null), PAID, inDays(10), NOW).refundable()).isFalse();
    final RefundPolicy.Decision over = RefundPolicy.evaluate(rules(true, PAID + 1, null), PAID, inDays(10), NOW);
    assertThat(over.refundable()).isFalse();
    assertThat(over.note()).contains("covers the whole amount");
  }

  @Test
  void negativeFee_isTreatedAsZero() {
    assertThat(RefundPolicy.evaluate(rules(true, -500.0, null), PAID, inDays(10), NOW).amount()).isEqualTo(PAID);
  }

  @Test
  void deadline_isInclusiveAtTheBoundary_andClosedOneSecondLater() {
    final FareRulesResponse threeDays = rules(true, 0.0, 3);

    // đúng 3 ngày trước giờ bay: còn được hoàn
    assertThat(RefundPolicy.evaluate(threeDays, PAID, inDays(3), NOW).refundable()).isTrue();
    // thiếu 1 giây: hết hạn
    final RefundPolicy.Decision late = RefundPolicy.evaluate(threeDays, PAID, inDays(3).minusSeconds(1), NOW);
    assertThat(late.refundable()).isFalse();
    assertThat(late.note()).contains("deadline has passed (3 days before departure)");
  }

  @Test
  void noDeadline_meansNoTimeLimitUntilDeparture() {
    assertThat(RefundPolicy.evaluate(rules(true, 0.0, null), PAID, NOW.plusSeconds(1), NOW).refundable()).isTrue();
    assertThat(RefundPolicy.evaluate(rules(true, 0.0, 0), PAID, NOW.plusSeconds(1), NOW).refundable()).isTrue();
  }

  @Test
  void departedFlight_isNeverRefunded_evenWithoutDeadline() {
    final RefundPolicy.Decision atDeparture = RefundPolicy.evaluate(rules(true, 0.0, null), PAID, NOW, NOW);
    final RefundPolicy.Decision after = RefundPolicy.evaluate(rules(true, 0.0, null), PAID, NOW.minusSeconds(60), NOW);

    assertThat(atDeparture.refundable()).isFalse();
    assertThat(atDeparture.note()).contains("already departed");
    assertThat(after.refundable()).isFalse();
  }

  @Test
  void deadlineThatCannotBeVerified_failsClosed() {
    final RefundPolicy.Decision decision = RefundPolicy.evaluate(rules(true, 0.0, 3), PAID, null, NOW);

    assertThat(decision.refundable()).isFalse();
    assertThat(decision.note()).contains("cannot be verified");
  }

  @Test
  void amountIsRoundedToAWholeCurrencyUnit() {
    assertThat(RefundPolicy.evaluate(rules(true, 0.0, null), 1000.6, inDays(10), NOW).amount()).isEqualTo(1001.0);
  }
}
