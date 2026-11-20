package com.nitditto.service.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Cấu hình hoá việc chọn IP khách gửi cho VNPay (xem {@link ClientIp}). */
@Component
public class ClientIpPolicy {

  private final boolean allowIpv6;

  public ClientIpPolicy(@Value("${vnpay.allow-ipv6-client-ip:false}") final boolean allowIpv6) {
    this.allowIpv6 = allowIpv6;
  }

  /** @param forwardedFor giá trị header X-Forwarded-For (có thể null) */
  public String resolve(final String forwardedFor) {
    return ClientIp.fromForwardedFor(forwardedFor, this.allowIpv6);
  }
}
