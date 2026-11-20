package com.nitditto.config;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** HTTP client gọi API cổng thanh toán (khác Feign gọi service nội bộ) và Clock để test được logic theo thời gian. */
@Configuration
public class GatewayHttpConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  /** Lấy bảng tỷ giá: chặn lâu sẽ treo việc tạo thanh toán Razorpay nên timeout ngắn; lỗi thì dùng tỷ giá cache. */
  @Bean("fxRestClient")
  public RestClient fxRestClient(@Value("${fx.http.connect-timeout-ms:3000}") final long connectTimeoutMs,
      @Value("${fx.http.read-timeout-ms:5000}") final long readTimeoutMs) {
    final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMs)).build();
    final JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
    return RestClient.builder().requestFactory(factory).build();
  }

  /** Timeout rõ ràng: lời gọi hoàn tiền không được treo vô hạn; hết giờ = kết quả KHÔNG RÕ (xem VNPayRefundService). */
  @Bean("vnpayRestClient")
  public RestClient vnpayRestClient(@Value("${vnpay.http.connect-timeout-ms:3000}") final long connectTimeoutMs,
      @Value("${vnpay.http.read-timeout-ms:15000}") final long readTimeoutMs) {
    final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMs)).build();
    final JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
    return RestClient.builder().requestFactory(factory).build();
  }
}
