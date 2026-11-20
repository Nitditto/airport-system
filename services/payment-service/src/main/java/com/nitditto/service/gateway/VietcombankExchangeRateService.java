package com.nitditto.service.gateway;

import com.nitditto.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Tỷ giá theo thị trường Việt Nam: bảng tỷ giá công khai của Vietcombank (XML). Dùng cột "Transfer" (ngân hàng MUA
 * chuyển khoản, đúng chiều bên bán nhận INR rồi đổi về VND) theo mặc định; {@code fx.rate-type=sell} dùng giá bán ra.
 *
 * <p>An toàn cho tiền: số INR cộng biên an toàn {@code fx.margin-percent} (bù trượt giá giữa lúc báo giá và lúc Razorpay
 * quyết toán) rồi làm tròn LÊN theo đơn vị nhỏ nhất. Tỷ giá mới lệch quá {@code fx.max-change-percent} so với tỷ giá đang
 * dùng bị coi là dữ liệu lỗi và bị bỏ qua (tránh một bảng sai làm khách bị thu gấp nhiều lần hay gần như miễn phí). Bảng được cache {@code fx.cache-minutes}; nếu lấy bảng mới
 * lỗi thì dùng tỷ giá đã có chừng nào chưa quá {@code fx.max-stale-hours}; hết hạn thì dùng tỷ giá dự phòng cấu hình tay
 * ({@code fx.fallback-vnd-per-inr}, mặc định không có); không có gì đáng tin thì TỪ CHỐI tạo thanh toán chứ không đoán.
 *
 * <p>Đọc XML an toàn: cấm DOCTYPE/thực thể ngoài (nguồn bên ngoài không được tin).
 */
@Slf4j
@Service
public class VietcombankExchangeRateService implements ExchangeRateService {

  private static final String SUPPORTED = "INR";
  private static final long MIN_MINOR = 100; // Razorpay: tối thiểu 1 INR
  private static final Duration RETRY_BACKOFF = Duration.ofMinutes(1);

  private record Cached(BigDecimal rate, Instant fetchedAt, String source) {
  }

  private final RestClient restClient;
  private final Clock clock;
  private final String url;
  private final boolean useSellRate;
  private final Duration cacheTtl;
  private final Duration maxStale;
  private final BigDecimal fallbackRate;
  private final double marginPercent;
  private final BigDecimal marginFactor;
  private final BigDecimal maxChange;

  private volatile Cached cached;
  private Instant nextRetryAt = Instant.MIN;

  public VietcombankExchangeRateService(@Qualifier("fxRestClient") final RestClient restClient, final Clock clock,
      @Value("${fx.vietcombank-url}") final String url,
      @Value("${fx.rate-type:transfer}") final String rateType,
      @Value("${fx.cache-minutes:60}") final long cacheMinutes,
      @Value("${fx.max-stale-hours:24}") final long maxStaleHours,
      @Value("${fx.fallback-vnd-per-inr:}") final String fallbackVndPerInr,
      @Value("${fx.margin-percent:1.0}") final double marginPercent,
      @Value("${fx.max-change-percent:20}") final double maxChangePercent) {
    this.restClient = restClient;
    this.clock = clock;
    this.url = url;
    this.useSellRate = "sell".equalsIgnoreCase(rateType);
    this.cacheTtl = Duration.ofMinutes(cacheMinutes);
    this.maxStale = Duration.ofHours(maxStaleHours);
    this.fallbackRate = parseRate(fallbackVndPerInr);
    if (marginPercent < 0 || marginPercent > 50) {
      throw new IllegalArgumentException("fx.margin-percent must be between 0 and 50");
    }
    this.marginPercent = marginPercent;
    this.marginFactor = BigDecimal.ONE.add(BigDecimal.valueOf(marginPercent).movePointLeft(2));
    this.maxChange = BigDecimal.valueOf(maxChangePercent).movePointLeft(2);
  }

  @Override
  public Quote quoteFromVnd(final String currency, final double amountVnd) {
    if (!SUPPORTED.equalsIgnoreCase(currency)) {
      throw new BusinessException("Unsupported charge currency: " + currency);
    }
    if (amountVnd <= 0) {
      throw new BusinessException("Amount must be positive");
    }
    final Cached rate = this.currentRate();
    final long minor = BigDecimal.valueOf(amountVnd).multiply(BigDecimal.valueOf(100)).multiply(this.marginFactor)
        .divide(rate.rate(), 0, RoundingMode.UP).longValueExact();
    if (minor < MIN_MINOR) {
      throw new BusinessException("Amount is too small to be charged in " + SUPPORTED);
    }
    return new Quote(SUPPORTED, minor, rate.rate().doubleValue(), this.marginPercent, rate.source());
  }

  private synchronized Cached currentRate() {
    final Instant now = this.clock.instant();
    final Cached existing = this.cached;
    if (existing != null && Duration.between(existing.fetchedAt(), now).compareTo(this.cacheTtl) < 0) {
      return existing;
    }
    // Vừa lỗi thì không gọi lại ngay cho từng thanh toán: dùng tỷ giá đang có (nếu còn hạn) cho tới lần thử kế tiếp.
    final boolean backingOff = now.isBefore(this.nextRetryAt);
    if (!backingOff) {
      try {
        final Cached fresh = this.fetch(now);
        this.rejectImplausibleJump(existing, fresh);
        this.cached = fresh;
        return fresh;
      } catch (final RuntimeException ex) {
        this.nextRetryAt = now.plus(RETRY_BACKOFF);
        log.warn("Could not refresh the VND/INR exchange rate: {}", ex.toString());
      }
    }
    if (existing != null && Duration.between(existing.fetchedAt(), now).compareTo(this.maxStale) <= 0) {
      log.warn("Using the cached VND/INR rate fetched at {}", existing.fetchedAt());
      return existing;
    }
    if (this.fallbackRate != null) {
      log.error("Using the CONFIGURED fallback VND/INR rate {}: the live rate is unavailable", this.fallbackRate);
      return new Cached(this.fallbackRate, now, "configured fallback rate");
    }
    throw new BusinessException("Razorpay payment is temporarily unavailable: no reliable VND/INR exchange rate");
  }

  private void rejectImplausibleJump(final Cached existing, final Cached fresh) {
    if (existing == null) {
      return;
    }
    final BigDecimal change = fresh.rate().subtract(existing.rate()).abs().divide(existing.rate(), 6,
        RoundingMode.HALF_UP);
    if (change.compareTo(this.maxChange) > 0) {
      log.error("The VND/INR rate jumped from {} to {}: ignoring the new value as a likely feed error", existing.rate(),
          fresh.rate());
      throw new IllegalStateException("Implausible exchange rate change");
    }
  }

  private Cached fetch(final Instant now) {
    final String xml = this.restClient.get().uri(this.url).retrieve().body(String.class);
    if (xml == null || xml.isBlank()) {
      throw new IllegalStateException("Empty exchange rate response");
    }
    final Document document = parse(xml);
    final NodeList entries = document.getElementsByTagName("Exrate");
    for (int i = 0; i < entries.getLength(); i++) {
      final Element entry = (Element) entries.item(i);
      if (SUPPORTED.equalsIgnoreCase(entry.getAttribute("CurrencyCode"))) {
        final BigDecimal rate = parseRate(entry.getAttribute(this.useSellRate ? "Sell" : "Transfer"));
        if (rate == null) {
          throw new IllegalStateException("No usable INR rate in the feed");
        }
        final NodeList stamp = document.getElementsByTagName("DateTime");
        final String feedTime = stamp.getLength() > 0 ? stamp.item(0).getTextContent().trim() : "unknown time";
        return new Cached(rate, now, "Vietcombank " + (this.useSellRate ? "sell" : "transfer") + " rate (feed "
            + feedTime + ")");
      }
    }
    throw new IllegalStateException("INR is not in the exchange rate feed");
  }

  private static Document parse(final String xml) {
    try {
      final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    } catch (final Exception ex) {
      throw new IllegalStateException("Unreadable exchange rate feed: " + ex.getClass().getSimpleName(), ex);
    }
  }

  /** "274.43", "25,560.00" -> số; "-", rỗng, không hợp lệ, hoặc <= 0 -> null. */
  static BigDecimal parseRate(final String text) {
    if (text == null || text.isBlank() || "-".equals(text.trim())) {
      return null;
    }
    try {
      final BigDecimal value = new BigDecimal(text.trim().replace(",", ""));
      return value.signum() > 0 ? value : null;
    } catch (final NumberFormatException ex) {
      return null;
    }
  }
}
