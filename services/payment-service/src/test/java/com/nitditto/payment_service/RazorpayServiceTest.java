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
  void withKeys_isConfigured() {
    assertThat(service("rzp_test_key", "rzp_test_secret").isConfigured()).isTrue();
  }
}
