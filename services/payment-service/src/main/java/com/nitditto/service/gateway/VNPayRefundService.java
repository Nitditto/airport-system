package com.nitditto.service.gateway;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Gọi API hoàn tiền của VNPay (POST JSON tới merchant_webapi). KHÔNG bao giờ ném lỗi ra ngoài: mọi kết quả được phân
 * loại để tầng trên quyết định, vì với tiền, "không biết" phải khác "bị từ chối".
 *
 * <ul>
 *   <li>{@code SUCCESS}: cổng trả mã 00 (và chữ ký phản hồi hợp lệ nếu bật kiểm tra).
 *   <li>{@code REJECTED}: cổng trả mã khác 00, hoặc chưa cấu hình: chắc chắn CHƯA hoàn tiền.
 *   <li>{@code UNKNOWN}: timeout/lỗi mạng/phản hồi không đọc được hoặc sai chữ ký: tiền CÓ THỂ đã được hoàn. Không
 *       được tự retry; phải kiểm tra ở cổng thanh toán.
 * </ul>
 *
 * <p><b>Chưa kiểm chứng với VNPay thật</b> (sandbox hạn chế tính năng hoàn tiền, cần VNPay mở cho merchant): thứ tự
 * trường để ký lấy theo tài liệu/thư viện công khai. Có công tắc {@code vnpay.refund-verify-response} để tắt kiểm tra
 * chữ ký phản hồi nếu định dạng thực tế khác.
 */
@Slf4j
@Service
public class VNPayRefundService {

  public enum Outcome { SUCCESS, REJECTED, UNKNOWN }

  public record Result(Outcome outcome, String responseCode, String message, String transactionNo) {

    static Result success(final String code, final String message, final String transactionNo) {
      return new Result(Outcome.SUCCESS, code, message, transactionNo);
    }

    static Result rejected(final String code, final String message) {
      return new Result(Outcome.REJECTED, code, message, null);
    }

    static Result unknown(final String message) {
      return new Result(Outcome.UNKNOWN, null, message, null);
    }
  }

  /** Dữ liệu một lần gọi hoàn tiền. {@code transactionDate} = vnp_CreateDate của giao dịch GỐC (yyyyMMddHHmmss). */
  public record Command(String txnRef, double amount, boolean fullRefund, String transactionNo,
      String transactionDate, String orderInfo, String createBy, String requestId) {
  }

  private static final DateTimeFormatter VNP_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
  private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP = new ParameterizedTypeReference<>() {
  };

  private final RestClient restClient;
  private final Clock clock;
  private final String tmnCode;
  private final String hashSecret;
  private final String refundUrl;
  private final String ipAddr;
  private final boolean verifyResponse;

  public VNPayRefundService(@Qualifier("vnpayRestClient") final RestClient restClient, final Clock clock,
      @Value("${vnpay.tmn-code}") final String tmnCode,
      @Value("${vnpay.hash-secret}") final String hashSecret,
      @Value("${vnpay.refund-url}") final String refundUrl,
      @Value("${vnpay.refund-ip-addr:127.0.0.1}") final String ipAddr,
      @Value("${vnpay.refund-verify-response:true}") final boolean verifyResponse) {
    this.restClient = restClient;
    this.clock = clock;
    this.tmnCode = tmnCode;
    this.hashSecret = hashSecret;
    this.refundUrl = refundUrl;
    this.ipAddr = ipAddr;
    this.verifyResponse = verifyResponse;
  }

  public boolean isConfigured() {
    return this.tmnCode != null && !this.tmnCode.isBlank() && this.hashSecret != null && !this.hashSecret.isBlank();
  }

  public Result refund(final Command command) {
    if (!this.isConfigured()) {
      return Result.rejected(null, "VNPay is not configured: set VNPAY_TMN_CODE and VNPAY_HASH_SECRET");
    }
    final Map<String, String> body = this.buildRequest(command);
    final Map<String, Object> response;
    try {
      // Gửi MẢNG BYTE đã serialize để có Content-Length rõ ràng. Để RestClient tự ghi Map thì thân được gửi theo khối
      // (chunked, không có Content-Length): máy chủ không hỗ trợ chunked đọc ra thân rỗng (E2E bắt được lỗi này).
      final byte[] json = new JSONObject(body).toString().getBytes(StandardCharsets.UTF_8);
      response = this.restClient.post().uri(this.refundUrl).contentType(MediaType.APPLICATION_JSON).body(json)
          .retrieve().body(JSON_MAP);
    } catch (final RuntimeException ex) {
      log.error("VNPay refund call failed for txnRef={} requestId={}: {}", command.txnRef(), command.requestId(),
          ex.toString());
      return Result.unknown("Could not complete the VNPay call: " + ex.getClass().getSimpleName());
    }
    if (response == null || response.isEmpty()) {
      return Result.unknown("Empty response from VNPay");
    }
    final Map<String, String> fields = new LinkedHashMap<>();
    response.forEach((k, v) -> fields.put(k, v == null ? "" : String.valueOf(v)));

    if (this.verifyResponse && !this.hasValidSignature(fields)) {
      log.error("VNPay refund response has an invalid signature for txnRef={} requestId={}", command.txnRef(),
          command.requestId());
      return Result.unknown("VNPay response signature mismatch");
    }
    final String code = fields.getOrDefault("vnp_ResponseCode", "");
    final String message = fields.getOrDefault("vnp_Message", "");
    if ("00".equals(code)) {
      return Result.success(code, message, fields.get("vnp_TransactionNo"));
    }
    if (code.isEmpty()) {
      return Result.unknown("Response without vnp_ResponseCode");
    }
    return Result.rejected(code, message);
  }

  Map<String, String> buildRequest(final Command c) {
    final Map<String, String> p = new LinkedHashMap<>();
    p.put("vnp_RequestId", c.requestId());
    p.put("vnp_Version", "2.1.0");
    p.put("vnp_Command", "refund");
    p.put("vnp_TmnCode", this.tmnCode);
    p.put("vnp_TransactionType", c.fullRefund() ? "02" : "03");
    p.put("vnp_TxnRef", c.txnRef());
    p.put("vnp_Amount", String.valueOf(toVnpAmount(c.amount())));
    p.put("vnp_OrderInfo", c.orderInfo());
    p.put("vnp_TransactionNo", c.transactionNo());
    p.put("vnp_TransactionDate", c.transactionDate());
    p.put("vnp_CreateBy", c.createBy());
    p.put("vnp_CreateDate", VNP_DATE.format(this.clock.instant().atZone(VIETNAM)));
    p.put("vnp_IpAddr", this.ipAddr);
    p.put("vnp_SecureHash", VNPayService.hmacSHA512(this.hashSecret, requestHashInput(p)));
    return p;
  }

  /** vnp_RequestId|Version|Command|TmnCode|TransactionType|TxnRef|Amount|TransactionNo|TransactionDate|CreateBy|CreateDate|IpAddr|OrderInfo */
  static String requestHashInput(final Map<String, String> p) {
    return join(p, "vnp_RequestId", "vnp_Version", "vnp_Command", "vnp_TmnCode", "vnp_TransactionType", "vnp_TxnRef",
        "vnp_Amount", "vnp_TransactionNo", "vnp_TransactionDate", "vnp_CreateBy", "vnp_CreateDate", "vnp_IpAddr",
        "vnp_OrderInfo");
  }

  /** vnp_ResponseId|Command|ResponseCode|Message|TmnCode|TxnRef|Amount|BankCode|PayDate|TransactionNo|TransactionType|TransactionStatus|OrderInfo */
  static String responseHashInput(final Map<String, String> p) {
    return join(p, "vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message", "vnp_TmnCode", "vnp_TxnRef",
        "vnp_Amount", "vnp_BankCode", "vnp_PayDate", "vnp_TransactionNo", "vnp_TransactionType",
        "vnp_TransactionStatus", "vnp_OrderInfo");
  }

  private boolean hasValidSignature(final Map<String, String> fields) {
    final String received = fields.get("vnp_SecureHash");
    if (received == null || received.isBlank()) {
      return false;
    }
    final String expected = VNPayService.hmacSHA512(this.hashSecret, responseHashInput(fields));
    return expected.equalsIgnoreCase(received);
  }

  static String join(final Map<String, String> p, final String... keys) {
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < keys.length; i++) {
      if (i > 0) {
        sb.append('|');
      }
      final String v = p.get(keys[i]);
      sb.append(v == null ? "" : v);
    }
    return sb.toString();
  }

  /** VNPay tính theo đơn vị nhỏ nhất: số tiền × 100, làm tròn về số nguyên (cùng cách với lúc tạo link thanh toán). */
  static long toVnpAmount(final double amount) {
    return BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP)
        .longValueExact();
  }
}
