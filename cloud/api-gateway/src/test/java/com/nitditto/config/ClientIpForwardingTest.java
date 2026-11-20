package com.nitditto.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.ServerRequest;

/** Gateway ghi đè X-Forwarded-For bằng IP thật của khách (vnp_IpAddr dùng giá trị này). */
class ClientIpForwardingTest {

  private static ServerRequest request(final String remoteAddr, final String forwardedFor) {
    final MockHttpServletRequest servlet = new MockHttpServletRequest("GET", "/api/airports");
    servlet.setRemoteAddr(remoteAddr);
    servlet.setRemotePort(51234);
    if (forwardedFor != null) {
      servlet.addHeader("X-Forwarded-For", forwardedFor);
    }
    return ServerRequest.create(servlet, List.of(new StringHttpMessageConverter()));
  }

  private static String forwarded(final ServerRequest request) {
    return request.headers().firstHeader("X-Forwarded-For");
  }

  private static RouteConfig gateway(final boolean trustForwardedFor) {
    return new RouteConfig(null, trustForwardedFor);
  }

  @Test
  void byDefault_theClientIpIsTheTcpPeer_andASpoofedHeaderIsDiscarded() {
    final ServerRequest out = gateway(false).withClientIp(request("203.0.113.5", "6.6.6.6, 7.7.7.7"));

    assertThat(forwarded(out)).isEqualTo("203.0.113.5");
    assertThat(out.headers().asHttpHeaders().get("X-Forwarded-For")).containsExactly("203.0.113.5");
  }

  @Test
  void withoutAnIncomingHeader_theTcpPeerIsStillSet() {
    assertThat(forwarded(gateway(false).withClientIp(request("198.51.100.9", null)))).isEqualTo("198.51.100.9");
  }

  @Test
  void ipv6Peers_areForwardedAsTheirLiteral() {
    assertThat(forwarded(gateway(false).withClientIp(request("2001:db8::1", null)))).isEqualTo("2001:db8:0:0:0:0:0:1");
  }

  @Test
  void behindATrustedProxy_theFirstValidAddressOfTheHeaderIsTheClient() {
    final ServerRequest out = gateway(true).withClientIp(request("10.0.0.2", "198.51.100.7, 10.0.0.2"));

    assertThat(forwarded(out)).isEqualTo("198.51.100.7");
  }

  @Test
  void behindATrustedProxy_garbageInTheHeaderFallsBackToThePeer_neverForwarded() {
    for (final String garbage : new String[] {"evil.example.com", "1.2.3.4<script>", "999.1.1.1", ""}) {
      assertThat(forwarded(gateway(true).withClientIp(request("10.0.0.2", garbage)))).as(garbage)
          .isEqualTo("10.0.0.2");
    }
  }

  @Test
  void ipLiteralValidation() {
    assertThat(ClientIpFormat.isIpLiteral("203.0.113.5")).isTrue();
    assertThat(ClientIpFormat.isIpLiteral("2001:db8::1")).isTrue();
    assertThat(ClientIpFormat.isIpLiteral("::1")).isTrue();
    assertThat(ClientIpFormat.isIpLiteral("256.1.1.1")).isFalse();
    assertThat(ClientIpFormat.isIpLiteral("1.2.3")).isFalse();
    assertThat(ClientIpFormat.isIpLiteral("a.b.c.d")).isFalse();
    assertThat(ClientIpFormat.isIpLiteral("localhost")).isFalse();
    assertThat(ClientIpFormat.isIpLiteral("")).isFalse();
    assertThat(ClientIpFormat.isIpLiteral(null)).isFalse();
  }
}
