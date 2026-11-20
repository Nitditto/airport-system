package com.nitditto.booking_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nitditto.config.FeignAuthForwardingConfig;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class FeignAuthForwardingConfigTest {

  private final RequestInterceptor interceptor = new FeignAuthForwardingConfig().authorizationForwardingInterceptor();

  @AfterEach
  void clearContext() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void forwardsCallerJwt_whenInsideAnHttpRequest() {
    final MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer user-jwt");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    final RequestTemplate template = new RequestTemplate();

    this.interceptor.apply(template);

    assertThat(template.headers().get("Authorization")).containsExactly("Bearer user-jwt");
  }

  @Test
  void forwardsTheRealClientIpWrittenByTheGateway_forVnpayIpAddr() {
    final MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Forwarded-For", "203.0.113.5");
    request.addHeader("X-Some-Other-Header", "not-forwarded");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    final RequestTemplate template = new RequestTemplate();

    this.interceptor.apply(template);

    assertThat(template.headers().get("X-Forwarded-For")).containsExactly("203.0.113.5");
    assertThat(template.headers()).doesNotContainKey("X-Some-Other-Header"); // chỉ đúng header này, không copy tất cả
  }

  @Test
  void doesNotForwardAnythingWhenThereIsNoForwardedFor() {
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    final RequestTemplate template = new RequestTemplate();

    this.interceptor.apply(template);

    assertThat(template.headers()).doesNotContainKey("X-Forwarded-For");
  }

  @Test
  void addsNothing_outsideAnHttpRequest_egKafkaListenerOrScheduler() {
    final RequestTemplate template = new RequestTemplate();

    this.interceptor.apply(template);

    assertThat(template.headers()).doesNotContainKey("Authorization");
  }

  @Test
  void addsNothing_whenIncomingRequestHasNoAuthorization() {
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    final RequestTemplate template = new RequestTemplate();

    this.interceptor.apply(template);

    assertThat(template.headers()).doesNotContainKey("Authorization");
  }

  @Test
  void keepsExplicitAuthorization_setByTheClientItself() {
    final MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer user-jwt");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    final RequestTemplate template = new RequestTemplate().header("Authorization", "Bearer service-token");

    this.interceptor.apply(template);

    assertThat(template.headers().get("Authorization")).containsExactly("Bearer service-token");
    assertThat(List.copyOf(template.headers().get("Authorization"))).hasSize(1);
  }
}
