package com.nitditto.service.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nitditto.service.gateway.VNPayQueryService.Command;
import com.nitditto.service.gateway.VNPayQueryService.Outcome;
import com.nitditto.service.gateway.VNPayQueryService.Result;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Hợp đồng với API tra cứu giao dịch (querydr) của VNPay ở mức HTTP. Thứ tự trường lấy từ tài liệu công khai và CHƯA được
 * kiểm chứng với VNPay thật: test này khoá hành vi của code, không chứng minh VNPay chấp nhận.
 */
class VNPayQueryServiceTest {

  private static final String URL = "https://vnpay.test/merchant_webapi/api/transaction";
  private static final String TMN = "TESTTMN";
  private static final String SECRET = "query-hash-secret";
  private static final String TXN_REF = "0f4c1c7e-1111-2222-3333-444455556666";
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-20T03:00:00Z"), ZoneOffset.UTC);

  private MockRestServiceServer server;
  private RestClient restClient;

  @BeforeEach
  void setUp() {
    final RestClient.Builder builder = RestClient.builder();
    this.server = MockRestServiceServer.bindTo(builder).build();
    this.restClient = builder.build();
  }

  private VNPayQueryService service(final boolean verifyResponse) {
    return new VNPayQueryService(this.restClient, CLOCK, TMN, SECRET, URL, "10.0.0.5", verifyResponse);
  }

  private static Command command() {
    return new Command(TXN_REF, "20261120095000", "Query payment", "req1234567890abcdef1234567890abcd");
  }

  @Test
  void requestIsSignedOverTheDocumentedFieldOrder() {
    // Chuỗi ký dựng tay, độc lập với code: RequestId|Version|Command|TmnCode|TxnRef|TransactionDate|CreateDate|Ip|OrderInfo
    final String expectedInput = String.join("|", "req1234567890abcdef1234567890abcd", "2.1.0", "querydr", TMN, TXN_REF,
        "20261120095000", "20261120100000", "10.0.0.5", "Query payment");
    this.server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
        .andExpect(header(HttpHeaders.CONTENT_LENGTH, org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyOrNullString())))
        .andExpect(jsonPath("$.vnp_Command").value("querydr"))
        .andExpect(jsonPath("$.vnp_TxnRef").value(TXN_REF))
        .andExpect(jsonPath("$.vnp_TransactionDate").value("20261120095000"))
        // CLOCK = 03:00Z = 10:00 giờ Việt Nam
        .andExpect(jsonPath("$.vnp_CreateDate").value("20261120100000"))
        .andExpect(jsonPath("$.vnp_SecureHash").value(VNPayService.hmacSHA512(SECRET, expectedInput)))
        .andRespond(withSuccess(signed("00", "00", "14123456"), MediaType.APPLICATION_JSON));

    final Result result = this.service(true).query(command());

    this.server.verify();
    assertThat(result.outcome()).isEqualTo(Outcome.PAID);
    assertThat(result.transactionNo()).isEqualTo("14123456");
    assertThat(result.amount()).isEqualTo("105000000");
  }

  @Test
  void transactionStatus_isMappedToTheOutcome() {
    assertThat(outcomeFor("00", "00")).isEqualTo(Outcome.PAID);
    assertThat(outcomeFor("00", "01")).isEqualTo(Outcome.IN_PROGRESS);
    assertThat(outcomeFor("00", "02")).isEqualTo(Outcome.FAILED);
  }

  @Test
  void statusesThatNeedAHuman_areUnknown_neverPaidOrFailed() {
    for (final String status : new String[] {"04", "05", "06", "07", "09", "99"}) {
      assertThat(outcomeFor("00", status)).as("status " + status).isEqualTo(Outcome.UNKNOWN);
    }
  }

  @Test
  void responseCode91_meansVnpayHasNoSuchTransaction() {
    assertThat(outcomeFor("91", "")).isEqualTo(Outcome.NOT_FOUND);
  }

  @Test
  void otherResponseCodes_areUnknown() {
    for (final String code : new String[] {"02", "03", "94", "97", "99"}) {
      assertThat(outcomeFor(code, "")).as("code " + code).isEqualTo(Outcome.UNKNOWN);
    }
  }

  private Outcome outcomeFor(final String responseCode, final String transactionStatus) {
    this.server.reset();
    this.server.expect(requestTo(URL))
        .andRespond(withSuccess(signed(responseCode, transactionStatus, "1"), MediaType.APPLICATION_JSON));
    return this.service(true).query(command()).outcome();
  }

  @Test
  void aBadSignature_isUnknown_whenVerificationIsOn_soAForgedPaidAnswerIsNeverTrusted() {
    final Map<String, String> tampered = response("00", "00", "1");
    tampered.put("vnp_SecureHash", "deadbeef");
    this.server.expect(requestTo(URL)).andRespond(withSuccess(toJson(tampered), MediaType.APPLICATION_JSON));

    final Result result = this.service(true).query(command());

    assertThat(result.outcome()).isEqualTo(Outcome.UNKNOWN);
    assertThat(result.message()).contains("signature");
  }

  @Test
  void verificationCanBeSwitchedOff_whenTheRealFormatDiffers() {
    this.server.expect(requestTo(URL))
        .andRespond(withSuccess(toJson(response("00", "00", "1")), MediaType.APPLICATION_JSON));

    assertThat(this.service(false).query(command()).outcome()).isEqualTo(Outcome.PAID);
  }

  @Test
  void serverError_emptyBody_andMissingConfig_areUnknown() {
    this.server.expect(requestTo(URL)).andRespond(withServerError());
    assertThat(this.service(true).query(command()).outcome()).isEqualTo(Outcome.UNKNOWN);

    this.server.reset();
    this.server.expect(requestTo(URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
    assertThat(this.service(true).query(command()).outcome()).isEqualTo(Outcome.UNKNOWN);

    this.server.reset();
    final VNPayQueryService unconfigured = new VNPayQueryService(this.restClient, CLOCK, "", "", URL, "127.0.0.1", true);
    assertThat(unconfigured.query(command()).outcome()).isEqualTo(Outcome.UNKNOWN);
    this.server.verify(); // không có lời gọi HTTP nào
  }

  // ---- phản hồi giả của VNPay (ký theo thứ tự phản hồi) ----

  private static Map<String, String> response(final String code, final String status, final String transactionNo) {
    final Map<String, String> r = new LinkedHashMap<>();
    r.put("vnp_ResponseId", "resp-1");
    r.put("vnp_Command", "querydr");
    r.put("vnp_ResponseCode", code);
    r.put("vnp_Message", "QueryDR Success");
    r.put("vnp_TmnCode", TMN);
    r.put("vnp_TxnRef", TXN_REF);
    r.put("vnp_Amount", "105000000");
    r.put("vnp_BankCode", "NCB");
    r.put("vnp_PayDate", "20261120095500");
    r.put("vnp_TransactionNo", transactionNo);
    r.put("vnp_TransactionType", "01");
    r.put("vnp_TransactionStatus", status);
    r.put("vnp_OrderInfo", "Query payment");
    return r;
  }

  private static String signed(final String code, final String status, final String transactionNo) {
    final Map<String, String> r = response(code, status, transactionNo);
    r.put("vnp_SecureHash", VNPayService.hmacSHA512(SECRET, VNPayQueryService.responseHashInput(r)));
    return toJson(r);
  }

  private static String toJson(final Map<String, String> map) {
    final StringBuilder sb = new StringBuilder("{");
    map.forEach((k, v) -> sb.append(sb.length() > 1 ? "," : "").append('"').append(k).append("\":\"")
        .append(v.replace("\"", "\\\"")).append('"'));
    return sb.append('}').toString();
  }
}
