package com.nitditto.service.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nitditto.service.gateway.VNPayRefundService.Command;
import com.nitditto.service.gateway.VNPayRefundService.Outcome;
import com.nitditto.service.gateway.VNPayRefundService.Result;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Hợp đồng với API hoàn tiền VNPay ở mức HTTP: thứ tự trường ký, mã giao dịch, và cách phân loại kết quả.
 * LƯU Ý: thứ tự trường lấy từ tài liệu/thư viện công khai và CHƯA được kiểm chứng với VNPay thật (sandbox hạn chế
 * hoàn tiền); test này khoá hành vi của code, không chứng minh VNPay chấp nhận.
 */
class VNPayRefundServiceTest {

  private static final String URL = "https://vnpay.test/merchant_webapi/api/transaction";
  private static final String TMN = "TESTTMN";
  private static final String SECRET = "refund-hash-secret";
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-20T03:00:00Z"), ZoneOffset.UTC);

  private MockRestServiceServer server;
  private RestClient restClient;

  @BeforeEach
  void setUp() {
    final RestClient.Builder builder = RestClient.builder();
    this.server = MockRestServiceServer.bindTo(builder).build();
    this.restClient = builder.build();
  }

  private VNPayRefundService service(final boolean verifyResponse) {
    return new VNPayRefundService(this.restClient, CLOCK, TMN, SECRET, URL, "10.0.0.5", verifyResponse);
  }

  private static Command command(final boolean full) {
    return new Command("0f4c1c7e-1111-2222-3333-444455556666", 1_050_000.0, full, "14123456", "20261120100000",
        "Refund booking X", "user-1", "req1234567890abcdef1234567890abcd");
  }

  @Test
  void requestIsSignedOverTheDocumentedFieldOrder_andCarriesTheOriginalTransactionData() {
    // Chuỗi ký dựng tay, độc lập với code: RequestId|Version|Command|TmnCode|Type|TxnRef|Amount|TransNo|TransDate|By|Date|Ip|Info
    final String expectedInput = String.join("|", "req1234567890abcdef1234567890abcd", "2.1.0", "refund", TMN, "03",
        "0f4c1c7e-1111-2222-3333-444455556666", "105000000", "14123456", "20261120100000", "user-1",
        "20261120100000", "10.0.0.5", "Refund booking X");
    final String expectedHash = VNPayService.hmacSHA512(SECRET, expectedInput);
    this.server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.vnp_Command").value("refund"))
        .andExpect(jsonPath("$.vnp_TransactionType").value("03"))
        .andExpect(jsonPath("$.vnp_Amount").value("105000000"))
        .andExpect(jsonPath("$.vnp_TxnRef").value("0f4c1c7e-1111-2222-3333-444455556666"))
        .andExpect(jsonPath("$.vnp_TransactionNo").value("14123456"))
        .andExpect(jsonPath("$.vnp_TransactionDate").value("20261120100000"))
        // CLOCK = 03:00Z = 10:00 giờ Việt Nam
        .andExpect(jsonPath("$.vnp_CreateDate").value("20261120100000"))
        .andExpect(jsonPath("$.vnp_SecureHash").value(expectedHash))
        .andRespond(withSuccess(signedResponse("00", "Confirm Success", "99887766"), MediaType.APPLICATION_JSON));

    final Result result = this.service(true).refund(command(false));

    this.server.verify();
    assertThat(result.outcome()).isEqualTo(Outcome.SUCCESS);
    assertThat(result.transactionNo()).isEqualTo("99887766");
  }

  @Test
  void fullRefundUsesTransactionType02() {
    this.server.expect(requestTo(URL)).andExpect(jsonPath("$.vnp_TransactionType").value("02"))
        .andRespond(withSuccess(signedResponse("00", "ok", "1"), MediaType.APPLICATION_JSON));

    assertThat(this.service(true).refund(command(true)).outcome()).isEqualTo(Outcome.SUCCESS);
  }

  @Test
  void aNonZeroResponseCode_isARejection_meaningTheCustomerWasNotRefunded() {
    this.server.expect(requestTo(URL))
        .andRespond(withSuccess(signedResponse("94", "Duplicate request", null), MediaType.APPLICATION_JSON));

    final Result result = this.service(true).refund(command(false));

    assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(result.responseCode()).isEqualTo("94");
    assertThat(result.message()).isEqualTo("Duplicate request");
  }

  @Test
  void aResponseWithABadSignature_isUnknown_notSuccess_whenVerificationIsOn() {
    final Map<String, String> tampered = response("00", "Confirm Success", "99887766");
    tampered.put("vnp_SecureHash", "deadbeef");
    this.server.expect(requestTo(URL)).andRespond(withSuccess(toJson(tampered), MediaType.APPLICATION_JSON));

    final Result result = this.service(true).refund(command(false));

    assertThat(result.outcome()).isEqualTo(Outcome.UNKNOWN);
    assertThat(result.message()).contains("signature");
  }

  @Test
  void verificationCanBeSwitchedOff_whenTheRealFormatDiffers() {
    final Map<String, String> unsigned = response("00", "Confirm Success", "99887766");
    this.server.expect(requestTo(URL)).andRespond(withSuccess(toJson(unsigned), MediaType.APPLICATION_JSON));

    assertThat(this.service(false).refund(command(false)).outcome()).isEqualTo(Outcome.SUCCESS);
  }

  @Test
  void serverError_isUnknown_becauseTheMoneyMayHaveMoved() {
    this.server.expect(requestTo(URL)).andRespond(withServerError());

    final Result result = this.service(true).refund(command(false));

    assertThat(result.outcome()).isEqualTo(Outcome.UNKNOWN);
  }

  @Test
  void anEmptyBody_isUnknown() {
    this.server.expect(requestTo(URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    assertThat(this.service(true).refund(command(false)).outcome()).isEqualTo(Outcome.UNKNOWN);
  }

  @Test
  void aResponseWithoutAResponseCode_isUnknown() {
    this.server.expect(requestTo(URL)).andRespond(withSuccess("{\"vnp_Message\":\"hello\"}", MediaType.APPLICATION_JSON));

    assertThat(this.service(false).refund(command(false)).outcome()).isEqualTo(Outcome.UNKNOWN);
  }

  @Test
  void notConfigured_isRejectedWithoutAnyHttpCall() {
    final VNPayRefundService unconfigured = new VNPayRefundService(this.restClient, CLOCK, "", "", URL, "127.0.0.1", true);

    final Result result = unconfigured.refund(command(false));

    assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(result.message()).contains("not configured");
    this.server.verify(); // không có expectation nào được đăng ký: nếu có lời gọi HTTP, test sẽ lỗi
  }

  @Test
  void amountConversionRoundsToWholeMinorUnits() {
    assertThat(VNPayRefundService.toVnpAmount(1_050_000.0)).isEqualTo(105_000_000L);
    assertThat(VNPayRefundService.toVnpAmount(1000.6)).isEqualTo(100_060L);
  }

  // ---- phản hồi giả của VNPay (ký theo thứ tự phản hồi) ----

  private static Map<String, String> response(final String code, final String message, final String transactionNo) {
    final Map<String, String> r = new LinkedHashMap<>();
    r.put("vnp_ResponseId", "resp-1");
    r.put("vnp_Command", "refund");
    r.put("vnp_ResponseCode", code);
    r.put("vnp_Message", message);
    r.put("vnp_TmnCode", TMN);
    r.put("vnp_TxnRef", "0f4c1c7e-1111-2222-3333-444455556666");
    r.put("vnp_Amount", "105000000");
    r.put("vnp_BankCode", "NCB");
    r.put("vnp_PayDate", "20261120100500");
    if (transactionNo != null) {
      r.put("vnp_TransactionNo", transactionNo);
    }
    r.put("vnp_TransactionType", "03");
    r.put("vnp_TransactionStatus", "05");
    r.put("vnp_OrderInfo", "Refund booking X");
    return r;
  }

  private static String signedResponse(final String code, final String message, final String transactionNo) {
    final Map<String, String> r = response(code, message, transactionNo);
    r.put("vnp_SecureHash", VNPayService.hmacSHA512(SECRET, VNPayRefundService.responseHashInput(r)));
    return toJson(r);
  }

  private static String toJson(final Map<String, String> map) {
    final StringBuilder sb = new StringBuilder("{");
    map.forEach((k, v) -> sb.append(sb.length() > 1 ? "," : "").append('"').append(k).append("\":\"")
        .append(v.replace("\"", "\\\"")).append('"'));
    return sb.append('}').toString();
  }
}
