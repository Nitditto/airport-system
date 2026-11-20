package com.nitditto.service.gateway;

/**
 * IP của khách để gửi cho cổng thanh toán. Header {@code X-Forwarded-For} do gateway ghi (xem RouteConfig của api-gateway);
 * ở đây chỉ chấp nhận một địa chỉ IP hợp lệ (không phân giải DNS, không tin chuỗi bất kỳ) để dữ liệu lạ không lọt vào tham số
 * được ký của cổng thanh toán.
 *
 * <p>Theo tài liệu VNPay, {@code vnp_IpAddr} là Alphanumeric[7,45] và ví dụ chỉ có IPv4; tài liệu KHÔNG nói rõ có nhận IPv6.
 * Vì vậy: địa chỉ IPv4-mapped IPv6 ({@code ::ffff:a.b.c.d}) được đổi về IPv4; IPv6 thật chỉ được gửi khi bật
 * {@code vnpay.allow-ipv6-client-ip} (mặc định tắt: gửi IPv6 mà VNPay từ chối thì khách không trả được tiền, nặng hơn việc
 * mất tín hiệu chống gian lận); và địa chỉ ngắn hơn 7 ký tự (như {@code ::1}) bị loại vì vi phạm độ dài tối thiểu.
 */
public final class ClientIp {

  /** Dùng khi không có IP hợp lệ/được phép (gọi nội bộ không qua gateway, header thiếu/sai, IPv6 khi chưa bật). */
  public static final String FALLBACK = "127.0.0.1";

  private static final int MIN_LENGTH = 7;
  private static final int MAX_LENGTH = 45;
  private static final String MAPPED_PREFIX = "::ffff:";

  private ClientIp() {
  }

  /** Lấy địa chỉ đầu tiên (khách) trong danh sách X-Forwarded-For; không hợp lệ/không được phép thì {@link #FALLBACK}. */
  public static String fromForwardedFor(final String header, final boolean allowIpv6) {
    if (header == null || header.isBlank()) {
      return FALLBACK;
    }
    String first = header.split(",", 2)[0].trim();
    if (first.regionMatches(true, 0, MAPPED_PREFIX, 0, MAPPED_PREFIX.length()) && isIpv4(first.substring(MAPPED_PREFIX.length()))) {
      first = first.substring(MAPPED_PREFIX.length());
    }
    if (!isIpLiteral(first) || first.length() < MIN_LENGTH) {
      return FALLBACK;
    }
    if (first.indexOf(':') >= 0 && !allowIpv6) {
      return FALLBACK;
    }
    return first;
  }

  static boolean isIpLiteral(final String value) {
    if (value == null || value.isEmpty() || value.length() > MAX_LENGTH) {
      return false;
    }
    return value.indexOf(':') >= 0 ? isIpv6(value) : isIpv4(value);
  }

  private static boolean isIpv4(final String value) {
    final String[] parts = value.split("[.]", -1);
    if (parts.length != 4) {
      return false;
    }
    for (final String part : parts) {
      if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)
          || Integer.parseInt(part) > 255) {
        return false;
      }
    }
    return true;
  }

  private static boolean isIpv6(final String value) {
    // chỉ ký tự hex, ':' và '.' (IPv4 nhúng); không phân giải tên miền. InetAddress.getByName với literal không gọi DNS.
    if (!value.chars().allMatch(c -> Character.digit(c, 16) >= 0 || c == ':' || c == '.')) {
      return false;
    }
    try {
      java.net.InetAddress.getByName(value);
      return true;
    } catch (final java.net.UnknownHostException ex) {
      return false;
    }
  }
}
