package com.nitditto.service.gateway;

import com.nitditto.model.Payment;
import com.nitditto.payload.response.PaymentLinkResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class VNPayService {

    @Value("${vnpay.tmn-code}")
    private String vnpTmnCode;

    @Value("${vnpay.hash-secret}")
    private String vnpHashSecret;

    @Value("${vnpay.pay-url}")
    private String vnpPayUrl;

    @Value("${vnpay.return-url}")
    private String vnpReturnUrl;

    private static final DateTimeFormatter VNP_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public PaymentLinkResponse createPaymentLink(final Payment payment, final String ipAddress) {
        final long amount = BigDecimal.valueOf(payment.getAmount())
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        final ZonedDateTime now = ZonedDateTime.now(VIETNAM_ZONE);
        final String createDate = VNP_DATE_FORMATTER.format(now);
        final String expireDate = VNP_DATE_FORMATTER.format(now.plusMinutes(15));

        final Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", "2.1.0");
        vnpParams.put("vnp_Command", "pay");
        vnpParams.put("vnp_TmnCode", this.vnpTmnCode);
        vnpParams.put("vnp_Amount", String.valueOf(amount));
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", payment.getId().toString());
        vnpParams.put("vnp_OrderInfo", "Payment for booking " + payment.getBookingId());
        vnpParams.put("vnp_OrderType", "other");
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_ReturnUrl", this.vnpReturnUrl);
        vnpParams.put("vnp_IpAddr", (ipAddress != null && !ipAddress.isBlank()) ? ipAddress : "127.0.0.1");
        vnpParams.put("vnp_CreateDate", createDate);
        vnpParams.put("vnp_ExpireDate", expireDate);

        final List<String> fieldNames = new ArrayList<>(vnpParams.keySet());
        Collections.sort(fieldNames);

        final StringBuilder hashData = new StringBuilder();
        final StringBuilder query = new StringBuilder();

        for (final String fieldName : fieldNames) {
            final String fieldValue = vnpParams.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                hashData.append(fieldName).append('=').append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII)).append('&');
                query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII)).append('=')
                        .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII)).append('&');
            }
        }

        if (hashData.length() > 0) {
            hashData.setLength(hashData.length() - 1);
        }
        if (query.length() > 0) {
            query.setLength(query.length() - 1);
        }

        final String secureHash = hmacSHA512(this.vnpHashSecret, hashData.toString());
        final String paymentUrl = this.vnpPayUrl + "?" + query + "&vnp_SecureHash=" + secureHash;

        return PaymentLinkResponse.builder()
                .paymentLinkId(payment.getId().toString())
                .paymentLinkUrl(paymentUrl)
                .build();
    }

    public boolean verifySignature(final Map<String, String> fields, final String secureHash) {
        if (secureHash == null || secureHash.isBlank() || fields == null) {
            return false;
        }

        final List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);

        final StringBuilder hashData = new StringBuilder();
        for (final String fieldName : fieldNames) {
            if ("vnp_SecureHash".equalsIgnoreCase(fieldName) || "vnp_SecureHashType".equalsIgnoreCase(fieldName)) {
                continue;
            }
            final String fieldValue = fields.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                hashData.append(fieldName).append('=').append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII)).append('&');
            }
        }

        if (hashData.length() > 0) {
            hashData.setLength(hashData.length() - 1);
        }

        final String calculatedHash = hmacSHA512(this.vnpHashSecret, hashData.toString());
        return calculatedHash.equalsIgnoreCase(secureHash);
    }

    public static String hmacSHA512(final String key, final String data) {
        try {
            if (key == null || data == null) {
                return "";
            }
            final Mac hmac512 = Mac.getInstance("HmacSHA512");
            final byte[] hmacKeyBytes = key.getBytes(StandardCharsets.UTF_8);
            final SecretKeySpec secretKey = new SecretKeySpec(hmacKeyBytes, "HmacSHA512");
            hmac512.init(secretKey);
            final byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            final byte[] result = hmac512.doFinal(dataBytes);
            final StringBuilder sb = new StringBuilder(2 * result.length);
            for (final byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (final Exception ex) {
            log.error("Failed to calculate HMAC-SHA512", ex);
            return "";
        }
    }
}
