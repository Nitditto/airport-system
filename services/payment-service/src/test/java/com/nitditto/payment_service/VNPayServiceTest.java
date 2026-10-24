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
    assertThatThrownBy(() -> service("", "").createPaymentLink(payment(), "127.0.0.1"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("VNPay is not configured");
  }

  @Test
  void verifySignature_whenNotConfigured_returnsFalse() {
    assertThat(service("", "").verifySignature(Map.of("vnp_TxnRef", "1"), "abc")).isFalse();
  }

  @Test
  void createPaymentLink_whenConfigured_producesUrlWhoseSignatureVerifies() {
    final VNPayService service = service("TMN123", "secret-key");

    final PaymentLinkResponse link = service.createPaymentLink(payment(), "127.0.0.1");

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
