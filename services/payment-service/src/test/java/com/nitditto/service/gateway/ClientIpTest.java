package com.nitditto.service.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClientIpTest {

  private static String ipv4Only(final String header) {
    return ClientIp.fromForwardedFor(header, false);
  }

  private static String withIpv6(final String header) {
    return ClientIp.fromForwardedFor(header, true);
  }

  @Test
  void theFirstAddressOfXForwardedFor_isTheCustomer() {
    assertThat(ipv4Only("203.0.113.5")).isEqualTo("203.0.113.5");
    assertThat(ipv4Only("203.0.113.5, 10.0.0.2, 172.18.0.4")).isEqualTo("203.0.113.5");
    assertThat(ipv4Only(" 198.51.100.7 ")).isEqualTo("198.51.100.7");
  }

  @Test
  void ipv6_isNotSentToVnpayByDefault_becauseItsDocumentationOnlyShowsIpv4() {
    assertThat(ipv4Only("2001:db8::1")).isEqualTo(ClientIp.FALLBACK);
    assertThat(ipv4Only("2001:0db8:85a3:0000:0000:8a2e:0370:7334")).isEqualTo(ClientIp.FALLBACK);
  }

  @Test
  void ipv6_isForwardedWhenExplicitlyEnabled() {
    assertThat(withIpv6("2001:db8::1")).isEqualTo("2001:db8::1");
    assertThat(withIpv6("2001:db8::1, 10.0.0.2")).isEqualTo("2001:db8::1");
  }

  @Test
  void anIpv4MappedIpv6Address_isSentAsPlainIpv4_evenWhenIpv6IsDisabled() {
    assertThat(ipv4Only("::ffff:203.0.113.5")).isEqualTo("203.0.113.5");
    assertThat(ipv4Only("::FFFF:198.51.100.7, 10.0.0.2")).isEqualTo("198.51.100.7");
  }

  @Test
  void anAddressShorterThanTheSevenCharacterMinimumOfVnpay_isNeverSent() {
    // vnp_IpAddr là Alphanumeric[7,45]: "::1" (3 ký tự) và "::" vi phạm độ dài tối thiểu
    assertThat(withIpv6("::1")).isEqualTo(ClientIp.FALLBACK);
    assertThat(withIpv6("::")).isEqualTo(ClientIp.FALLBACK);
    assertThat(withIpv6("1::2")).isEqualTo(ClientIp.FALLBACK);
    assertThat(ipv4Only("0.0.0.0")).isEqualTo("0.0.0.0"); // đúng 7 ký tự: hợp lệ
  }

  @Test
  void missingOrInvalidValues_fallBackToLoopback_andNeverReachTheSignedParameters() {
    for (final String bad : new String[] {null, "", "  ", "evil.example.com", "999.1.1.1", "1.2.3", "1.2.3.4<x>",
        "unknown", "1.2.3.4:80", "'; drop table", "a".repeat(60)}) {
      assertThat(ipv4Only(bad)).as(String.valueOf(bad)).isEqualTo(ClientIp.FALLBACK);
      assertThat(withIpv6(bad)).as(String.valueOf(bad)).isEqualTo(ClientIp.FALLBACK);
    }
  }

  @Test
  void theFallbackItselfSatisfiesVnpaysLengthRule() {
    assertThat(ClientIp.FALLBACK.length()).isBetween(7, 45);
  }

  @Test
  void thePolicyAppliesTheConfiguredSwitch() {
    assertThat(new ClientIpPolicy(false).resolve("2001:db8::1")).isEqualTo(ClientIp.FALLBACK);
    assertThat(new ClientIpPolicy(true).resolve("2001:db8::1")).isEqualTo("2001:db8::1");
    assertThat(new ClientIpPolicy(false).resolve("203.0.113.5")).isEqualTo("203.0.113.5");
  }
}
