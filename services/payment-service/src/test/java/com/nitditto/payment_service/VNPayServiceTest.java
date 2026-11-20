package com.nitditto.payment_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nitditto.exception.BusinessException;
import com.nitditto.model.Payment;
import com.nitditto.payload.response.PaymentLinkResponse;
import com.nitditto.service.gateway.VNPayService;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class VNPayServiceTest {

  private static VNPayService service(final String tmnCode, final String hashSecret) {
    final VNPayService service = new VNPayService();
    ReflectionTestUtils.setField(service, "vnpTmnCode", tmnCode);
    ReflectionTestUtils.setField(service, "vnpHashSecret", hashSecret);
    ReflectionTestUtils.setField(service, "vnpPayUrl", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
    ReflectionTestUtils.setField(service, "vnpReturnUrl", "http://localhost:5173/payment/result");
    return service;
  }

  private static Payment payment() {
    final Payment payment = Payment.builder().userId(UUID.randomUUID()).bookingId(UUID.randomUUID())
        .amount(250_000.0).build();
    payment.setId(UUID.randomUUID());
    return payment;
  }

  @Test
  void createPaymentLink_whenNotConfigured_isRejected() {
    assertThatThrownBy(() -> service("", "").createPaymentLink(payment(), "127.0.0.1", null))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("VNPay is not configured");
  }

  @Test
  void verifySignature_whenNotConfigured_returnsFalse() {
    assertThat(service("", "").verifySignature(Map.of("vnp_TxnRef", "1"), "abc")).isFalse();
  }

  private static Map<String, String> params(final String url) {
    final Map<String, String> params = new HashMap<>();
    for (final String pair : URI.create(url).getRawQuery().split("&")) {
      final String[] kv = pair.split("=", 2);
      params.put(java.net.URLDecoder.decode(kv[0], java.nio.charset.StandardCharsets.UTF_8),
          java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8));
    }
    return params;
  }

  private static final java.time.format.DateTimeFormatter VNP_DATE =
      java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
  private static final java.time.ZoneId VIETNAM = java.time.ZoneId.of("Asia/Ho_Chi_Minh");

  @Test
  void theLinkExpiresExactlyWhenTheBookingDoes_andTheExpiryIsReturned() {
    final java.time.Instant bookingExpiry = java.time.Instant.now().plusSeconds(7 * 60 + 13);

    final PaymentLinkResponse link = service("TMN123", "secret-key")
        .createPaymentLink(payment(), "203.0.113.5", bookingExpiry);

    final Map<String, String> params = params(link.getPaymentLinkUrl());
    final java.time.Instant expected = bookingExpiry.atZone(VIETNAM).withNano(0).toInstant();
    assertThat(params.get("vnp_ExpireDate")).isEqualTo(VNP_DATE.format(expected.atZone(VIETNAM)));
    assertThat(link.getExpiresAt()).isEqualTo(expected);
    // vẫn ký đúng khi hạn thay đổi
    final String hash = params.remove("vnp_SecureHash");
    assertThat(service("TMN123", "secret-key").verifySignature(params, hash)).isTrue();
  }

  @Test
  void withoutABookingExpiry_theDefaultLifetimeApplies() {
    final java.time.Instant before = java.time.Instant.now();

    final PaymentLinkResponse link = service("TMN123", "secret-key").createPaymentLink(payment(), "127.0.0.1", null);

    assertThat(link.getExpiresAt()).isBetween(before.plus(VNPayService.DEFAULT_LINK_LIFETIME).minusSeconds(2),
        java.time.Instant.now().plus(VNPayService.DEFAULT_LINK_LIFETIME).plusSeconds(2));
  }

  @Test
  void aBookingThatIsAboutToExpireOrAlreadyExpired_getsNoDeadLink() {
    final VNPayService service = service("TMN123", "secret-key");

    assertThatThrownBy(() -> service.createPaymentLink(payment(), "127.0.0.1", java.time.Instant.now().plusSeconds(20)))
        .isInstanceOf(BusinessException.class).hasMessageContaining("payment window");
    assertThatThrownBy(() -> service.createPaymentLink(payment(), "127.0.0.1", java.time.Instant.now().minusSeconds(60)))
        .isInstanceOf(BusinessException.class).hasMessageContaining("payment window");
  }

  @Test
  void anAbsurdlyFarExpiry_isCappedAtOneDay() {
    final PaymentLinkResponse link = service("TMN123", "secret-key")
        .createPaymentLink(payment(), "127.0.0.1", java.time.Instant.now().plus(java.time.Duration.ofDays(30)));

    assertThat(link.getExpiresAt()).isBefore(java.time.Instant.now().plus(java.time.Duration.ofHours(25)));
  }

  @Test
  void theCustomersIpEndsUpInTheSignedParameter_andABlankOneFallsBackToLoopback() {
    final VNPayService service = service("TMN123", "secret-key");

    assertThat(params(service.createPaymentLink(payment(), "203.0.113.5", null).getPaymentLinkUrl())
        .get("vnp_IpAddr")).isEqualTo("203.0.113.5");
    assertThat(params(service.createPaymentLink(payment(), "2001:db8::1", null).getPaymentLinkUrl())
        .get("vnp_IpAddr")).isEqualTo("2001:db8::1");
    assertThat(params(service.createPaymentLink(payment(), " ", null).getPaymentLinkUrl())
        .get("vnp_IpAddr")).isEqualTo("127.0.0.1");
  }

  @Test
  void createPaymentLink_whenConfigured_producesUrlWhoseSignatureVerifies() {
    final VNPayService service = service("TMN123", "secret-key");

    final PaymentLinkResponse link = service.createPaymentLink(payment(), "127.0.0.1", null);

    final Map<String, String> params = new HashMap<>();
    for (final String pair : URI.create(link.getPaymentLinkUrl()).getRawQuery().split("&")) {
      final String[] kv = pair.split("=", 2);
      params.put(java.net.URLDecoder.decode(kv[0], java.nio.charset.StandardCharsets.UTF_8),
          java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8));
    }
    final String hash = params.remove("vnp_SecureHash");

    assertThat(service.verifySignature(params, hash)).isTrue();
    params.put("vnp_Amount", "1");
    assertThat(service.verifySignature(params, hash)).isFalse();
  }
}
