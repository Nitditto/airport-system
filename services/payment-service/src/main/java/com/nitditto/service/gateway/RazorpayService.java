package com.nitditto.service.gateway;

import com.nitditto.model.Payment;
import com.nitditto.payload.dto.UserDTO;
import com.nitditto.payload.response.PaymentLinkResponse;
import com.razorpay.PaymentLink;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


@Slf4j
@Service
public class RazorpayService {

    @Value("${razorpay.api.key}")
    private String razorpayKeyId;

    @Value("${razorpay.api.secret}")
    private String razorpaySecret;

    @Value("${razorpay.callback.base-url}")
    private String callbackBaseUrl;

    private RazorpayClient razorpayClient;

    @PostConstruct
    private void init() throws RazorpayException {
        if (!this.isConfigured()) {
            log.warn("Razorpay is not configured: RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET are empty");
            return;
        }
        this.razorpayClient = new RazorpayClient(this.razorpayKeyId, this.razorpaySecret);
    }

    public boolean isConfigured() {
        return this.razorpayKeyId != null && !this.razorpayKeyId.isBlank()
                && this.razorpaySecret != null && !this.razorpaySecret.isBlank();
    }

    private RazorpayClient client() throws RazorpayException {
        if (this.razorpayClient == null) {
            throw new RazorpayException("Razorpay is not configured: set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET");
        }
        return this.razorpayClient;
    }

    /** Razorpay đòi {@code expire_by} cách lúc tạo ít nhất 15 phút; thêm 1 phút đệm. */
    static final Duration MIN_LINK_LIFETIME = Duration.ofMinutes(16);

    public PaymentLinkResponse createPaymentLink(final UserDTO user, final Payment payment) throws RazorpayException {
        return this.createPaymentLink(user, payment, null);
    }

    /**
     * @param bookingExpiry hạn thanh toán của booking: link hết hạn đúng lúc này nếu Razorpay cho phép; cửa sổ thanh toán
     *                      ngắn hơn mức tối thiểu của Razorpay thì link sống tối thiểu {@link #MIN_LINK_LIFETIME} (lệch tối
     *                      đa ~1 phút). Thanh toán rơi vào khoảng lệch này được booking-service tự hoàn tiền toàn bộ.
     *                      Null = link không có hạn.
     */
    public PaymentLinkResponse createPaymentLink(final UserDTO user, final Payment payment, final Instant bookingExpiry)
            throws RazorpayException {
        return this.createPaymentLink(user, payment, bookingExpiry, Instant.now());
    }

    PaymentLinkResponse createPaymentLink(final UserDTO user, final Payment payment, final Instant bookingExpiry,
            final Instant now) throws RazorpayException {
        // Giá vé là VND nhưng Razorpay thu INR: số tiền ĐÃ được quy đổi (ExchangeRateService) và lưu trên payment. Không có thì
        // từ chối chứ không bao giờ gửi số VND như thể đó là INR (lỗi cũ: 1.150.000 VND thành 1.150.000 INR).
        if (payment.getChargeAmountMinor() == null || payment.getChargeCurrency() == null) {
            throw new RazorpayException("Payment " + payment.getId() + " has no charge amount in the Razorpay currency");
        }

        final JSONObject paymentLinkRequest = new JSONObject();
        paymentLinkRequest.put("amount", payment.getChargeAmountMinor());
        paymentLinkRequest.put("currency", payment.getChargeCurrency());
        paymentLinkRequest.put("description", "Payment for booking " + payment.getBookingId());
        final Instant expireBy = expireByOf(bookingExpiry, now);
        if (expireBy != null) {
            paymentLinkRequest.put("expire_by", expireBy.getEpochSecond());
        }

        final JSONObject customer = new JSONObject();
        customer.put("name", user.getFullName());
        customer.put("email", user.getEmail());
        if (user.getPhone() != null) {
            customer.put("contact", user.getPhone());
        }
        paymentLinkRequest.put("customer", customer);

        final JSONObject notify = new JSONObject();
        notify.put("email", true);
        notify.put("sms", user.getPhone() != null);
        paymentLinkRequest.put("notify", notify);

        paymentLinkRequest.put("reminder_enable", true);

        final String successUrl = this.callbackBaseUrl + "/booking-success/" + payment.getBookingId();
        paymentLinkRequest.put("callback_url", successUrl);
        paymentLinkRequest.put("callback_method", "get");

        // Nhúng metadata nội bộ vào "notes" — đây là cách duy nhất để verify() sau này
        // nhận diện lại đúng Payment record khi Razorpay trả kết quả, KHÔNG tin trực
        // tiếp bất kỳ id nào client tự gửi lên ở bước verify.
        final JSONObject notes = new JSONObject();
        notes.put("payment_id", payment.getId().toString());
        notes.put("booking_id", payment.getBookingId().toString());
        notes.put("user_id", user.getId().toString());
        paymentLinkRequest.put("notes", notes);

        final PaymentLink paymentLink = this.client().paymentLink.create(paymentLinkRequest);
        final String paymentLinkId = paymentLink.get("id");
        final String paymentLinkUrl = paymentLink.get("short_url");

        return PaymentLinkResponse.builder()
                .paymentLinkId(paymentLinkId)
                .paymentLinkUrl(paymentLinkUrl)
                .expiresAt(expireBy)
                .build();
    }

    /** Hạn link gửi cho Razorpay: hạn booking, nhưng không sớm hơn now + {@link #MIN_LINK_LIFETIME}; null nếu không có hạn. */
    public static Instant expireByOf(final Instant bookingExpiry, final Instant now) {
        if (bookingExpiry == null) {
            return null;
        }
        final Instant earliestAllowed = now.plus(MIN_LINK_LIFETIME);
        final Instant expireBy = bookingExpiry.isBefore(earliestAllowed) ? earliestAllowed : bookingExpiry;
        return expireBy.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    }

    /**
     * Hoàn một phần hoặc toàn bộ một payment đã capture. {@code amountMinor} theo đơn vị nhỏ nhất của loại tiền đã thu (paise).
     * {@code receipt} (id yêu cầu của hệ thống) được Razorpay lưu kèm để đối chiếu.
     */
    public JSONObject refundPayment(final String razorpayPaymentId, final long amountMinor, final String receipt)
            throws RazorpayException {
        final JSONObject request = new JSONObject();
        request.put("amount", amountMinor);
        request.put("speed", "normal");
        request.put("receipt", receipt);
        return this.client().payments.refund(razorpayPaymentId, request).toJson();
    }

    public JSONObject fetchPaymentDetails(final String razorpayPaymentId) throws RazorpayException {
        final com.razorpay.Payment payment = this.client().payments.fetch(razorpayPaymentId);
        return payment.toJson();
    }
}
