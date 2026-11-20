package com.nitditto.service.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nitditto.exception.BusinessException;
import com.nitditto.service.gateway.ExchangeRateService.Quote;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class VietcombankExchangeRateServiceTest {

  private static final String URL = "https://fx.test/pXML.aspx";
  private static final Instant T0 = Instant.parse("2026-11-20T03:00:00Z");

  // Định dạng thật của bảng tỷ giá Vietcombank: dấu phẩy ngàn, "-" khi không niêm yết.
  private static final String FEED = """
      <ExrateList>
        <DateTime>11/20/2026 10:21:28 AM</DateTime>
        <Exrate CurrencyCode="USD" CurrencyName="US DOLLAR" Buy="25,530.00" Transfer="25,560.00" Sell="25,940.00" />
        <Exrate CurrencyCode="INR" CurrencyName="INDIAN RUPEE" Buy="-" Transfer="274.43" Sell="286.25" />
        <Source>Joint Stock Commercial Bank for Foreign Trade of Vietnam - Vietcombank</Source>
      </ExrateList>""";

  private final AtomicReference<Instant> now = new AtomicReference<>(T0);
  private final Clock clock = new Clock() {
    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(final java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now.get();
    }
  };

  private MockRestServiceServer server;
  private RestClient restClient;

  @BeforeEach
  void setUp() {
    final RestClient.Builder builder = RestClient.builder();
    this.server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
    this.restClient = builder.build();
  }

  // Các test tỷ giá thuần dùng biên 0 để số học dễ đối chiếu; biên được kiểm riêng ở dưới.
  private VietcombankExchangeRateService service(final String rateType, final String fallback) {
    return serviceWith(rateType, fallback, 0.0);
  }

  private VietcombankExchangeRateService serviceWith(final String rateType, final String fallback,
      final double margin) {
    return new VietcombankExchangeRateService(this.restClient, this.clock, URL, rateType, 60, 24, fallback, margin, 20);
  }

  private void feed(final String xml) {
    this.server.expect(requestTo(URL)).andRespond(withSuccess(xml, MediaType.APPLICATION_XML));
  }

  @Test
  void usesTheTransferRate_andRoundsTheForeignAmountUp() {
    feed(FEED);

    final Quote quote = service("transfer", "").quoteFromVnd("INR", 1_150_500.0);

    // 1.150.500 / 274,43 = 4192,326... INR = 419.232,6 paise; làm tròn LÊN thành 419.233 để bên bán không thiệt
    assertThat(quote.currency()).isEqualTo("INR");
    assertThat(quote.rate()).isEqualTo(274.43);
    assertThat(quote.amountMinor()).isEqualTo(419_233L);
    assertThat(quote.amountMajor()).isEqualTo(4192.33);
    assertThat(quote.source()).contains("Vietcombank transfer").contains("11/20/2026 10:21:28 AM");
    // đối chiếu độc lập: số tiền INR quy ngược lại về VND không được thấp hơn giá vé
    assertThat(quote.amountMinor() / 100.0 * quote.rate()).isGreaterThanOrEqualTo(1_150_500.0);
  }

  @Test
  void theSellRateCanBeChosen() {
    feed(FEED);

    final Quote quote = service("sell", "").quoteFromVnd("INR", 1_150_500.0);

    assertThat(quote.rate()).isEqualTo(286.25);
    assertThat(quote.amountMinor()).isEqualTo(401_922L);
  }

  @Test
  void theRateIsCached_soTheFeedIsNotCalledForEveryPayment() {
    feed(FEED); // chỉ MỘT lời gọi được phép

    final VietcombankExchangeRateService service = service("transfer", "");
    service.quoteFromVnd("INR", 500_000.0);
    service.quoteFromVnd("INR", 700_000.0);

    this.server.verify();
  }

  @Test
  void afterTheCacheExpires_aFreshRateIsFetched() {
    feed(FEED);
    final VietcombankExchangeRateService service = service("transfer", "");
    service.quoteFromVnd("INR", 500_000.0);

    this.server.reset();
    this.now.set(T0.plusSeconds(61 * 60));
    feed(FEED.replace("274.43", "280.00"));

    assertThat(service.quoteFromVnd("INR", 500_000.0).rate()).isEqualTo(280.0);
  }

  @Test
  void whenTheFeedFails_theLastKnownRateIsUsedWhileItIsNotTooOld() {
    feed(FEED);
    final VietcombankExchangeRateService service = service("transfer", "");
    service.quoteFromVnd("INR", 500_000.0);

    this.server.reset();
    this.now.set(T0.plusSeconds(3 * 3600));
    this.server.expect(requestTo(URL)).andRespond(withServerError());

    assertThat(service.quoteFromVnd("INR", 500_000.0).rate()).isEqualTo(274.43);
  }

  @Test
  void whenTheLastRateIsTooOldAndThereIsNoFallback_paymentIsRefused_neverGuessed() {
    feed(FEED);
    final VietcombankExchangeRateService service = service("transfer", "");
    service.quoteFromVnd("INR", 500_000.0);

    this.server.reset();
    this.now.set(T0.plusSeconds(25 * 3600));
    this.server.expect(requestTo(URL)).andRespond(withServerError());

    assertThatThrownBy(() -> service.quoteFromVnd("INR", 500_000.0))
        .isInstanceOf(BusinessException.class).hasMessageContaining("no reliable VND/INR exchange rate");
  }

  @Test
  void theConfiguredFallbackRateIsUsedOnlyWhenNothingElseIsAvailable() {
    this.server.expect(requestTo(URL)).andRespond(withServerError());

    final Quote quote = service("transfer", "300").quoteFromVnd("INR", 600_000.0);

    assertThat(quote.rate()).isEqualTo(300.0);
    assertThat(quote.amountMinor()).isEqualTo(200_000L);
    assertThat(quote.source()).contains("fallback");
  }

  @Test
  void aFeedWithoutAUsableInrRate_isRejected() {
    feed(FEED.replace("274.43", "-"));
    assertThatThrownBy(() -> service("transfer", "").quoteFromVnd("INR", 500_000.0))
        .isInstanceOf(BusinessException.class);

    this.server.reset();
    feed("<ExrateList><Exrate CurrencyCode=\"USD\" Buy=\"1\" Transfer=\"1\" Sell=\"1\" /></ExrateList>");
    assertThatThrownBy(() -> service("transfer", "").quoteFromVnd("INR", 500_000.0))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void aFeedWithADoctype_isNotParsed_soExternalEntitiesCannotBeUsed() {
    feed("<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>" + FEED);

    assertThatThrownBy(() -> service("transfer", "").quoteFromVnd("INR", 500_000.0))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void theSafetyMargin_isAddedBeforeRoundingUp_andReportedInTheQuote() {
    feed(FEED);

    final Quote quote = serviceWith("transfer", "", 1.0).quoteFromVnd("INR", 1_150_500.0);

    // 1.150.500 x 1,01 / 274,43 = 4234,24... INR -> 423.425 paise (lên); tỷ giá báo cáo vẫn là tỷ giá thị trường
    assertThat(quote.amountMinor()).isEqualTo(423_425L);
    assertThat(quote.marginPercent()).isEqualTo(1.0);
    assertThat(quote.rate()).isEqualTo(274.43);
    assertThat(quote.amountMinor() / 100.0 * quote.rate()).isGreaterThan(1_150_500.0 * 1.0099);
  }

  @Test
  void aMarginOutsideTheSaneRange_isRejectedAtStartup() {
    assertThatThrownBy(() -> serviceWith("transfer", "", -1.0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> serviceWith("transfer", "", 51.0)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aRateThatJumpsImplausibly_isIgnored_andTheKnownRateKeepsBeingUsed() {
    feed(FEED);
    final VietcombankExchangeRateService service = service("transfer", "");
    service.quoteFromVnd("INR", 500_000.0);

    this.server.reset();
    this.now.set(T0.plusSeconds(61 * 60));
    feed(FEED.replace("274.43", "400.00")); // +45%: dữ liệu lỗi

    assertThat(service.quoteFromVnd("INR", 500_000.0).rate()).isEqualTo(274.43);
  }

  @Test
  void aRateWithinTheAllowedChange_isAccepted() {
    feed(FEED);
    final VietcombankExchangeRateService service = service("transfer", "");
    service.quoteFromVnd("INR", 500_000.0);

    this.server.reset();
    this.now.set(T0.plusSeconds(61 * 60));
    feed(FEED.replace("274.43", "280.00")); // +2%

    assertThat(service.quoteFromVnd("INR", 500_000.0).rate()).isEqualTo(280.0);
  }

  @Test
  void afterAFailure_theFeedIsNotHammered_forEveryPayment() {
    feed(FEED);
    final VietcombankExchangeRateService service = service("transfer", "");
    service.quoteFromVnd("INR", 500_000.0);

    this.server.reset();
    this.now.set(T0.plusSeconds(61 * 60));
    this.server.expect(requestTo(URL)).andRespond(withServerError()); // chỉ MỘT lời gọi được phép

    service.quoteFromVnd("INR", 500_000.0);
    this.now.set(T0.plusSeconds(61 * 60 + 30));
    service.quoteFromVnd("INR", 700_000.0); // còn trong thời gian chờ thử lại: không gọi nữa

    this.server.verify();
  }

  @Test
  void theFeedIsRetriedOnceTheBackoffHasPassed() {
    feed(FEED);
    final VietcombankExchangeRateService service = service("transfer", "");
    service.quoteFromVnd("INR", 500_000.0);

    this.server.reset();
    this.now.set(T0.plusSeconds(61 * 60));
    this.server.expect(requestTo(URL)).andRespond(withServerError());
    service.quoteFromVnd("INR", 500_000.0);

    this.server.reset();
    this.now.set(T0.plusSeconds(61 * 60 + 90));
    feed(FEED.replace("274.43", "276.00"));

    assertThat(service.quoteFromVnd("INR", 500_000.0).rate()).isEqualTo(276.0);
  }

  @Test
  void onlyInrIsSupported_andAmountsMustBePositiveAndNotTiny() {
    final VietcombankExchangeRateService service = service("transfer", "");

    assertThatThrownBy(() -> service.quoteFromVnd("USD", 500_000.0)).isInstanceOf(BusinessException.class)
        .hasMessageContaining("Unsupported");
    assertThatThrownBy(() -> service.quoteFromVnd("INR", 0)).isInstanceOf(BusinessException.class);

    feed(FEED);
    assertThatThrownBy(() -> service.quoteFromVnd("INR", 100.0)).isInstanceOf(BusinessException.class)
        .hasMessageContaining("too small");
  }
}
