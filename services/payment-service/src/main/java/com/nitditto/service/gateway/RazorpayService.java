package com.nitditto.service.gateway;

import com.nitditto.model.Payment;
import com.nitditto.payload.dto.UserDTO;
import com.nitditto.payload.response.PaymentLinkResponse;
import com.razorpay.PaymentLink;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;

import jakarta.annotation.PostConstruct;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

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
        this.razorpayClient = new RazorpayClient(this.razorpayKeyId, this.razorpaySecret);
    }

    public PaymentLinkResponse createPaymentLink(final UserDTO user, final Payment payment) throws RazorpayException {
        final long amountInPaisa = BigDecimal.valueOf(payment.getAmount())
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        final JSONObject paymentLinkRequest = new JSONObject();
        paymentLinkRequest.put("amount", amountInPaisa);
        paymentLinkRequest.put("currency", "INR");
        paymentLinkRequest.put("description", "Payment for booking " + payment.getBookingId());

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

        final PaymentLink paymentLink = this.razorpayClient.paymentLink.create(paymentLinkRequest);
        final String paymentLinkId = paymentLink.get("id");
        final String paymentLinkUrl = paymentLink.get("short_url");

        return PaymentLinkResponse.builder()
                .paymentLinkId(paymentLinkId)
                .paymentLinkUrl(paymentLinkUrl)
                .build();
    }

    public JSONObject fetchPaymentDetails(final String razorpayPaymentId) throws RazorpayException {
        final com.razorpay.Payment payment = this.razorpayClient.payments.fetch(razorpayPaymentId);
        return payment.toJson();
    }
}
