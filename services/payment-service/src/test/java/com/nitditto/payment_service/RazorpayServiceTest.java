package com.nitditto.payment_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nitditto.service.gateway.RazorpayService;
import com.razorpay.RazorpayException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RazorpayServiceTest {

  private static RazorpayService service(final String keyId, final String secret) {
    final RazorpayService service = new RazorpayService();
    ReflectionTestUtils.setField(service, "razorpayKeyId", keyId);
    ReflectionTestUtils.setField(service, "razorpaySecret", secret);
    return service;
  }

  @Test
  void withoutKeys_isNotConfigured_andStartupDoesNotFail() {
    final RazorpayService service = service("", "");

    ReflectionTestUtils.invokeMethod(service, "init");

    assertThat(service.isConfigured()).isFalse();
  }

  @Test
  void withoutKeys_fetchingPaymentIsRejected() {
    final RazorpayService service = service("", "");
    ReflectionTestUtils.invokeMethod(service, "init");

    assertThatThrownBy(() -> service.fetchPaymentDetails("pay_123"))
        .isInstanceOf(RazorpayException.class)
        .hasMessageContaining("Razorpay is not configured");
  }

  @Test
  void aPaymentWithoutAConvertedChargeAmount_isRefused_neverSendingVndAsIfItWereInr() {
    final RazorpayService service = service("rzp_test_key", "rzp_test_secret");
    final com.nitditto.model.Payment payment = com.nitditto.model.Payment.builder()
        .bookingId(java.util.UUID.randomUUID()).amount(1_150_000.0).build();
    payment.setId(java.util.UUID.randomUUID());

    assertThatThrownBy(() -> service.createPaymentLink(new com.nitditto.payload.dto.UserDTO(), payment))
        .isInstanceOf(RazorpayException.class).hasMessageContaining("no charge amount");
  }

  @Test
  void withoutKeys_refundIsRejected() {
    final RazorpayService service = service("", "");
    ReflectionTestUtils.invokeMethod(service, "init");

    assertThatThrownBy(() -> service.refundPayment("pay_123", 100L, "r"))
        .isInstanceOf(RazorpayException.class).hasMessageContaining("Razorpay is not configured");
  }

  private static final java.time.Instant NOW = java.time.Instant.parse("2026-11-20T03:00:00Z");

  @Test
  void theLinkExpiresWhenTheBookingDoes_whenThatIsFarEnoughAhead() {
    final java.time.Instant bookingExpiry = NOW.plus(java.time.Duration.ofMinutes(30)).plusMillis(400);

    assertThat(RazorpayService.expireByOf(bookingExpiry, NOW)).isEqualTo(NOW.plusSeconds(30 * 60));
  }

  @Test
  void aBookingWindowShorterThanRazorpaysMinimum_getsTheMinimumLifetime_notAnInvalidTimestamp() {
    // Razorpay đòi expire_by cách lúc tạo >= 15 phút; cửa sổ thanh toán 15 phút của booking sẽ vi phạm
    assertThat(RazorpayService.expireByOf(NOW.plusSeconds(15 * 60), NOW)).isEqualTo(NOW.plusSeconds(16 * 60));
    assertThat(RazorpayService.expireByOf(NOW.minusSeconds(60), NOW)).isEqualTo(NOW.plusSeconds(16 * 60));
  }

  @Test
  void withoutABookingExpiry_theLinkHasNoExpiry() {
    assertThat(RazorpayService.expireByOf(null, NOW)).isNull();
  }

  @Test
  void withKeys_isConfigured() {
    assertThat(service("rzp_test_key", "rzp_test_secret").isConfigured()).isTrue();
  }
}
