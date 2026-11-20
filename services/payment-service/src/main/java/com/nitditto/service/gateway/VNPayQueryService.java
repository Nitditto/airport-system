package com.nitditto.service.gateway;

import java.nio.charset.StandardCharsets;
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
 * Tra cứu kết quả một giao dịch thanh toán ở VNPay (API {@code querydr}): dùng khi cả trình duyệt lẫn IPN đều không báo
 * kết quả. KHÔNG bao giờ ném lỗi ra ngoài; mọi kết quả được phân loại:
 *
 * <ul>
 *   <li>{@code PAID}: truy vấn thành công và {@code vnp_TransactionStatus = 00}.
 *   <li>{@code FAILED}: trạng thái giao dịch 02 (thất bại).
 *   <li>{@code IN_PROGRESS}: trạng thái 01 (chưa hoàn tất).
 *   <li>{@code NOT_FOUND}: mã phản hồi 91 (VNPay không có giao dịch: khách chưa từng vào trang thanh toán).
 *   <li>{@code UNKNOWN}: mọi trường hợp còn lại (lỗi mạng, sai chữ ký, mã lạ, trạng thái 04/05/06/07/09...): không được
 *       tự kết luận, cần người xem.
 * </ul>
 *
 * <p><b>Chưa kiểm chứng với VNPay thật.</b> Thứ tự trường ký lấy từ tài liệu công khai của VNPay (chính trang đó ghi công
 * thức chữ ký phản hồi thiếu một toán tử nên cần đối chiếu với sandbox). Có công tắc
 * {@code vnpay.query-verify-response} để tắt kiểm tra chữ ký phản hồi.
 */
@Slf4j
@Service
public class VNPayQueryService {

  public enum Outcome { PAID, FAILED, IN_PROGRESS, NOT_FOUND, UNKNOWN }

  public record Result(Outcome outcome, String transactionStatus, String responseCode, String message,
      String transactionNo, String amount) {

    static Result of(final Outcome outcome, final String status, final String code, final String message,
        final String transactionNo, final String amount) {
      return new Result(outcome, status, code, message, transactionNo, amount);
    }

    static Result unknown(final String message) {
      return new Result(Outcome.UNKNOWN, null, null, message, null, null);
    }
  }

  /** {@code transactionDate} = vnp_CreateDate của giao dịch gốc (yyyyMMddHHmmss). */
  public record Command(String txnRef, String transactionDate, String orderInfo, String requestId) {
  }

  private static final DateTimeFormatter VNP_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
  private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP = new ParameterizedTypeReference<>() {
  };

  private final RestClient restClient;
  private final Clock clock;
  private final String tmnCode;
  private final String hashSecret;
  private final String queryUrl;
  private final String ipAddr;
  private final boolean verifyResponse;

  public VNPayQueryService(@Qualifier("vnpayRestClient") final RestClient restClient, final Clock clock,
      @Value("${vnpay.tmn-code}") final String tmnCode,
      @Value("${vnpay.hash-secret}") final String hashSecret,
      @Value("${vnpay.query-url}") final String queryUrl,
      @Value("${vnpay.refund-ip-addr:127.0.0.1}") final String ipAddr,
      @Value("${vnpay.query-verify-response:true}") final boolean verifyResponse) {
    this.restClient = restClient;
    this.clock = clock;
    this.tmnCode = tmnCode;
    this.hashSecret = hashSecret;
    this.queryUrl = queryUrl;
    this.ipAddr = ipAddr;
    this.verifyResponse = verifyResponse;
  }

  public boolean isConfigured() {
    return this.tmnCode != null && !this.tmnCode.isBlank() && this.hashSecret != null && !this.hashSecret.isBlank();
  }

  public Result query(final Command command) {
    if (!this.isConfigured()) {
      return Result.unknown("VNPay is not configured: set VNPAY_TMN_CODE and VNPAY_HASH_SECRET");
    }
    final Map<String, String> body = this.buildRequest(command);
    final Map<String, Object> response;
    try {
      // Byte[] để có Content-Length rõ ràng, cùng lý do với VNPayRefundService (máy chủ VNPay không đọc được chunked).
      final byte[] json = new JSONObject(body).toString().getBytes(StandardCharsets.UTF_8);
      response = this.restClient.post().uri(this.queryUrl).contentType(MediaType.APPLICATION_JSON).body(json)
          .retrieve().body(JSON_MAP);
    } catch (final RuntimeException ex) {
      log.warn("VNPay query failed for txnRef={}: {}", command.txnRef(), ex.toString());
      return Result.unknown("Could not complete the VNPay call: " + ex.getClass().getSimpleName());
    }
    if (response == null || response.isEmpty()) {
      return Result.unknown("Empty response from VNPay");
    }
    final Map<String, String> fields = new LinkedHashMap<>();
    response.forEach((k, v) -> fields.put(k, v == null ? "" : String.valueOf(v)));

    if (this.verifyResponse && !this.hasValidSignature(fields)) {
      log.error("VNPay query response has an invalid signature for txnRef={}", command.txnRef());
      return Result.unknown("VNPay response signature mismatch");
    }
    return classify(fields);
  }

  static Result classify(final Map<String, String> f) {
    final String code = f.getOrDefault("vnp_ResponseCode", "");
    final String status = f.getOrDefault("vnp_TransactionStatus", "");
    final String message = f.getOrDefault("vnp_Message", "");
    final String transactionNo = f.get("vnp_TransactionNo");
    final String amount = f.get("vnp_Amount");
    if ("91".equals(code)) {
      return Result.of(Outcome.NOT_FOUND, status, code, message, transactionNo, amount);
    }
    if (!"00".equals(code)) {
      return Result.of(Outcome.UNKNOWN, status, code, message, transactionNo, amount);
    }
    final Outcome outcome = switch (status) {
      case "00" -> Outcome.PAID;
      case "01" -> Outcome.IN_PROGRESS;
      case "02" -> Outcome.FAILED;
      default -> Outcome.UNKNOWN;
    };
    return Result.of(outcome, status, code, message, transactionNo, amount);
  }

  Map<String, String> buildRequest(final Command c) {
    final Map<String, String> p = new LinkedHashMap<>();
    p.put("vnp_RequestId", c.requestId());
    p.put("vnp_Version", "2.1.0");
    p.put("vnp_Command", "querydr");
    p.put("vnp_TmnCode", this.tmnCode);
    p.put("vnp_TxnRef", c.txnRef());
    p.put("vnp_OrderInfo", c.orderInfo());
    p.put("vnp_TransactionDate", c.transactionDate());
    p.put("vnp_CreateDate", VNP_DATE.format(this.clock.instant().atZone(VIETNAM)));
    p.put("vnp_IpAddr", this.ipAddr);
    p.put("vnp_SecureHash", VNPayService.hmacSHA512(this.hashSecret, requestHashInput(p)));
    return p;
  }

  /** vnp_RequestId|Version|Command|TmnCode|TxnRef|TransactionDate|CreateDate|IpAddr|OrderInfo */
  static String requestHashInput(final Map<String, String> p) {
    return VNPayRefundService.join(p, "vnp_RequestId", "vnp_Version", "vnp_Command", "vnp_TmnCode", "vnp_TxnRef",
        "vnp_TransactionDate", "vnp_CreateDate", "vnp_IpAddr", "vnp_OrderInfo");
  }

  /** vnp_ResponseId|Command|ResponseCode|Message|TmnCode|TxnRef|Amount|BankCode|PayDate|TransactionNo|TransactionType|TransactionStatus|OrderInfo|PromotionCode|PromotionAmount */
  static String responseHashInput(final Map<String, String> p) {
    return VNPayRefundService.join(p, "vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message",
        "vnp_TmnCode", "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate", "vnp_TransactionNo",
        "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo", "vnp_PromotionCode", "vnp_PromotionAmount");
  }

  private boolean hasValidSignature(final Map<String, String> fields) {
    final String received = fields.get("vnp_SecureHash");
    if (received == null || received.isBlank()) {
      return false;
    }
    return VNPayService.hmacSHA512(this.hashSecret, responseHashInput(fields)).equalsIgnoreCase(received);
  }
}
